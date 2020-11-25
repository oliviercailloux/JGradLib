package io.github.oliviercailloux.git.fs;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Verify.verify;
import static com.google.common.base.Verify.verifyNotNull;
import static io.github.oliviercailloux.jaris.exceptions.Unchecker.IO_UNCHECKER;

import java.io.IOException;
import java.net.URI;
import java.nio.file.ClosedFileSystemException;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.WatchService;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.PeekingIterator;
import com.google.common.graph.ImmutableGraph;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;

import io.github.oliviercailloux.git.GitLocalHistory;
import io.github.oliviercailloux.git.GitUtils;
import io.github.oliviercailloux.git.fs.GitPath.GitObject;

/**
 * <p>
 * A git file system. Associated to a git repository. Can be used to obtain
 * {@link GitPath} instances.
 * </p>
 * <p>
 * The <em>default</em> directory of a git file system refers to the root
 * directory of its <tt>main</tt> branch.
 * </p>
 * <p>
 * Must be {@link #close()}d to release resources associated with readers.
 * </p>
 *
 * @see #getAbsolutePath(String, String...)
 * @see #getAbsolutePath(GitRev, String...)
 * @see #getRelativePath(String...)
 * @see #getGitRootDirectories()
 */
public abstract class GitFileSystem extends FileSystem {
	static class PathNotFoundException extends Exception {
	}

	static class TreeWalkIterator implements PeekingIterator<String> {
		private TreeWalk walk;
		private DirectoryIteratorException exception;

		private Boolean hasNext;
		private boolean closed;

		public TreeWalkIterator(TreeWalk walk) {
			this.walk = checkNotNull(walk);
			exception = null;
			hasNext = null;
			closed = false;
		}

		@Override
		public boolean hasNext() throws DirectoryIteratorException {
			if (exception != null) {
				throw exception;
			}

			if (closed) {
				return false;
			}

			if (hasNext == null) {
				try {
					hasNext = walk.next();
				} catch (IOException e) {
					exception = new DirectoryIteratorException(e);
					throw exception;
				}
			}

			return hasNext;
		}

		@Override
		public String peek() throws DirectoryIteratorException {
			if (exception != null) {
				throw exception;
			}
			if (hasNext == null) {
				hasNext();
			}
			if (!hasNext) {
				throw new NoSuchElementException();
			}
			return walk.getNameString();
		}

		@Override
		public String next() throws DirectoryIteratorException {
			final String current = peek();
			hasNext = null;
			return current;
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException();
		}

		public void close() {
			closed = true;
			walk.close();
		}

	}

	static class TreeWalkDirectoryStream implements DirectoryStream<String> {
		private final TreeWalkIterator iterator;
		private boolean returned;
		private boolean closed;

		public TreeWalkDirectoryStream(TreeWalk walk) {
			iterator = new TreeWalkIterator(walk);
			returned = false;
			closed = false;
		}

		@Override
		public void close() {
			closed = true;
			iterator.close();
		}

		@Override
		public Iterator<String> iterator() {
			if (returned || closed) {
				throw new IllegalStateException();
			}
			returned = true;
			return iterator;
		}

	}

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GitFileSystem.class);

	/**
	 * It is crucial to always use the same instance of Jimfs, because Jimfs refuses
	 * to resolve paths coming from different instances. And the references to JimFs
	 * might be better here rather than in {@link GitFileSystemProvider} because
	 * when {@link GitFileSystemProvider} is initialized, we do not want to refer to
	 * JimFs, which might not be initialized yet (perhaps this should not create
	 * problems, but as it seems logically better to have these references here
	 * anyway, I did not investigate).
	 */
	static final FileSystem JIM_FS = Jimfs.newFileSystem(Configuration.unix());

	static final Path JIM_FS_EMPTY = JIM_FS.getPath("");

	static final Path JIM_FS_SLASH = JIM_FS.getPath("/");

	private final GitFileSystemProvider gitProvider;
	private GitLocalHistory history;

	private boolean isOpen;
	private ObjectReader reader;
	private Repository repository;
	private boolean shouldCloseRepository;

	private Set<TreeWalkDirectoryStream> toClose;

	final GitPathRoot mainSlash = new GitPathRoot(this, GitRev.DEFAULT);
	final GitEmptyPath defaultPath = new GitEmptyPath(mainSlash);

	/**
	 * Git file system provides low-level access to read operations on a repository
	 * (such as retrieving a RevCommit given an id; including with specific
	 * exceptions raised by JGit). The higher level access such as reading a Commit
	 * and throwing nice user-level exceptions such as {@link NoSuchFileException}
	 * is left to elsewhere where possible, e.g. GitPathRoot.
	 */
	protected GitFileSystem(GitFileSystemProvider gitProvider, Repository repository, boolean shouldCloseRepository) {
		this.gitProvider = checkNotNull(gitProvider);
		this.repository = checkNotNull(repository);
		this.shouldCloseRepository = shouldCloseRepository;
		reader = repository.newObjectReader();
		reader.setAvoidUnreachableObjects(true);
		isOpen = true;
		history = null;
		this.toClose = new LinkedHashSet<>();
	}

	@Override
	public void close() {
		isOpen = false;

		List<RuntimeException> closeExceptions = new ArrayList<>();
		try {
			reader.close();
		} catch (RuntimeException e) {
			closeExceptions.add(e);
		}
		if (shouldCloseRepository) {
			try {
				repository.close();
			} catch (RuntimeException e) {
				closeExceptions.add(e);
			}
		}
		for (TreeWalkDirectoryStream closeable : toClose) {
			try {
				closeable.close();
			} catch (RuntimeException e) {
				closeExceptions.add(e);
			}
		}
		try {
			provider().getGitFileSystems().hasBeenClosedEvent(this);
		} catch (RuntimeException e) {
			closeExceptions.add(e);
		}
		if (!closeExceptions.isEmpty()) {
			final RuntimeException first = closeExceptions.remove(0);
			if (!closeExceptions.isEmpty()) {
				LOGGER.error("Further problems while closing: {}.", closeExceptions);
			}
			throw first;
		}
	}

	/**
	 * Converts a path string, or a sequence of strings that when joined form a path
	 * string, to a {@code GitPath}. If {@code first} starts with <code>/</code> (or
	 * if {@code first} is empty and the first non-empty string in {@code more}
	 * starts with <code>/</code>), this method behaves as if
	 * {@link #getAbsolutePath(String, String...)} had been called. Otherwise, it
	 * behaves as if {@link #getRelativePath(String...)} had been called.
	 *
	 * @param first the path string or initial part of the path string
	 * @param more  additional strings to be joined to form the path string
	 *
	 * @return the resulting {@code Path}
	 *
	 * @throws InvalidPathException If the path string cannot be converted
	 */
	@Override
	public GitPath getPath(String first, String... more) {
		final ImmutableList<String> allNames = Stream.concat(Stream.of(first), Stream.of(more))
				.collect(ImmutableList.toImmutableList());
		final boolean startsWithSlash = allNames.stream().filter(n -> !n.isEmpty()).findFirst()
				.map(s -> s.startsWith("/")).orElse(false);
		if (startsWithSlash) {
			return getAbsolutePath(first, more);
		}
		return getRelativePath(allNames);
	}

	/**
	 * <p>
	 * Converts an absolute git path string, or a sequence of strings that when
	 * joined form an absolute git path string, to an absolute {@code GitPath}.
	 * </p>
	 * <p>
	 * If {@code more} does not specify any elements then the value of the
	 * {@code first} parameter is the path string to convert.
	 * </p>
	 * <p>
	 * If {@code more} specifies one or more elements then each non-empty string,
	 * including {@code first}, is considered to be a sequence of name elements (see
	 * {@link Path}) and is joined to form a path string using <code>/</code> as
	 * separator. If {@code first} does not end with <code>//</code> (but ends with
	 * <code>/</code>, as required), and if {@code more} does not start with
	 * <code>/</code>, then a <code>/</code> is added so that there will be exactly
	 * two slashes joining {@code first} to {@code more}.
	 * </p>
	 * <p>
	 * For example, if {@code getAbsolutePath("/refs/heads/main/","foo","bar")} is
	 * invoked, then the path string {@code "/refs/heads/main//foo/bar"} is
	 * converted to a {@code Path}.
	 * </p>
	 * <p>
	 * A {@code Path} representing a default (or empty) path is returned iff names
	 * contain only empty strings.
	 * </p>
	 *
	 * @param first the string form of the root component, possibly followed by
	 *              other path segments. Must start with <code>/</code>; must
	 *              contain at most once <code>//</code>; if does not contain
	 *              <code>//</code>, must end with <code>/</code>.
	 * @param more  may start with <code>/</code>.
	 * @return an absolute git path.
	 * @throws InvalidPathException if {@code first} does not contain a valid root
	 *                              component
	 * @see GitPath
	 */
	public GitPath getAbsolutePath(String first, String... more) throws InvalidPathException {
		final String rootStringForm;
		final ImmutableList<String> internalPath;
		if (first.contains("//")) {
			final int startDoubleSlash = first.indexOf("//");
			final String beforeDoubleSlash = first.substring(0, startDoubleSlash);
			final String afterDoubleSlash = first.substring(startDoubleSlash + 2);
			rootStringForm = beforeDoubleSlash + "/";
			internalPath = Stream.concat(Stream.of(afterDoubleSlash), Stream.of(more))
					.collect(ImmutableList.toImmutableList());
		} else {
			rootStringForm = first;
			internalPath = ImmutableList.copyOf(more);
		}

		return getAbsolutePath(GitRev.stringForm(rootStringForm), internalPath);
	}

	/**
	 * @param root
	 * @param internalPath may start with <code>/</code>
	 * @return
	 */
	public GitPath getAbsolutePath(GitRev root, String... internalPath) {
		return getAbsolutePath(root, ImmutableList.copyOf(internalPath));
	}

	private GitPath getAbsolutePath(ObjectId objectId, String... internalPath) {
		return getAbsolutePath(GitRev.commitId(objectId), ImmutableList.copyOf(internalPath));
	}

	private GitPath getAbsolutePath(GitRev root, List<String> names) {
		checkNotNull(root);
		final String first = names.isEmpty() ? "" : names.get(0);
		final String[] more = names.isEmpty() ? new String[] {}
				: names.subList(1, names.size()).toArray(new String[] {});
		final Path internalPath = JIM_FS.getPath(first, more);
		return GitAbsolutePath.givenRev(this, root, JIM_FS_SLASH.resolve(internalPath));
	}

	/**
	 * <p>
	 * Converts a relative git path string, or a sequence of strings that when
	 * joined form a relative git path string, to a relative {@code GitPath}.
	 * </p>
	 * <p>
	 * Each non-empty string in {@code names} is considered to be a sequence of name
	 * elements (see {@link Path}) and is joined to form a path string using
	 * <code>/</code> as separator.
	 * </p>
	 * <p>
	 * For example, if {@code getRelativePath("foo","bar")} is invoked, then the
	 * path string {@code "foo/bar"} is converted to a {@code Path}.
	 * </p>
	 * <p>
	 * The <em>empty</em> path is returned iff names contain only empty strings. It
	 * refers to the {@link GitFileSystem default directory} of this file system.
	 * </p>
	 *
	 * @param names the internal path; its first element (if any) may not start with
	 *              <code>/</code>.
	 * @return a relative git path.
	 * @throws InvalidPathException if the first non-empty string in {@code names}
	 *                              start with <code>/</code>.
	 * @see GitPath
	 */
	public GitPath getRelativePath(String... names) throws InvalidPathException {
		return getRelativePath(ImmutableList.copyOf(names));
	}

	private GitPath getRelativePath(ImmutableList<String> names) throws InvalidPathException {
		/**
		 * NOT equivalent to resolve each component to the previous part, starting with
		 * JIM_FS_EMPTY: if one starts with a slash, this makes the resulting path
		 * absolute.
		 */
		final String first = names.isEmpty() ? "" : names.get(0);
		final String[] more = names.isEmpty() ? new String[] {}
				: names.subList(1, names.size()).toArray(new String[] {});
		final Path internalPath = JIM_FS.getPath(first, more);
		if (internalPath.isAbsolute()) {
			throw new InvalidPathException(first, first + " makes this internal path absolute.");
		}
		return GitRelativePath.relative(this, internalPath);
	}

	public GitLocalHistory getCachedHistory() {
		checkState(history != null);
		return history;
	}

	@Override
	public Iterable<FileStore> getFileStores() {
		throw new UnsupportedOperationException();
	}

	/**
	 * Retrieve the set of all commits of this repository. This is equivalent to
	 * calling {@link #getRootDirectories()}, but with a more precise type. Each git
	 * path in the returned set consists in only a root component, and this root
	 * component is a commit SHA-1.
	 *
	 * @return absolute paths whose root components are commit SHA-1s, ordered with
	 *         earlier commits coming first.
	 */
	public ImmutableSortedSet<GitPath> getGitRootDirectories() {
		if (!isOpen) {
			throw new ClosedFileSystemException();
		}

		IO_UNCHECKER.call(() -> getHistory());

		final Comparator<GitPath> compareWithoutTies = getPathLexicographicTemporalComparator();

		/** Important to compare without ties, otherwise some commits get collapsed. */
		return ImmutableSortedSet.copyOf(compareWithoutTies, getCachedHistory().getGraph().nodes().stream()
				.map((c) -> getAbsolutePath(GitRev.commitId(c))).iterator());
	}

	public GitLocalHistory getHistory() throws IOException {
		if (history == null) {
			history = GitUtils.getHistory(repository);
		}
		return history;
	}

	Comparator<ObjectId> getLexicographicTemporalComparator() {
		getCachedHistory();
		final Comparator<ObjectId> comparingDates = Comparator.comparing(o -> history.getCommitDateById(o));
		final Comparator<ObjectId> compareWithoutTies = comparingDates.thenComparing((c1, c2) -> {
			final ImmutableGraph<RevCommit> graph = history.getTransitivelyClosedGraph();
			if (graph.hasEdgeConnecting(history.getCommit(c2), history.getCommit(c1))) {
				/** c1 < c2 */
				return -1;
			}
			if (graph.hasEdgeConnecting(history.getCommit(c1), history.getCommit(c2))) {
				/** c1 child-of c2 thus c1 comes after (is greater than) c2 temporally. */
				return 1;
			}
			return 0;
		}).thenComparing(Comparator.comparing(ObjectId::getName));
		return compareWithoutTies;
	}

	/**
	 * Paths must have a root in the form of a sha1.
	 */
	private Comparator<GitPath> getPathLexicographicTemporalComparator() {
		final Comparator<ObjectId> compareWithoutTies = getLexicographicTemporalComparator();
		return Comparator.comparing(
				(GitPath p) -> IO_UNCHECKER.getUsing(() -> p.toAbsolutePath().getRoot().getCommit()),
				compareWithoutTies);
	}

	@Override
	public PathMatcher getPathMatcher(String syntaxAndPattern) {
		throw new UnsupportedOperationException();
	}

	@Override
	public ImmutableSortedSet<Path> getRootDirectories() {
		if (!isOpen) {
			throw new ClosedFileSystemException();
		}

		IO_UNCHECKER.call(() -> getHistory());

		final Comparator<Path> compareWithoutTies = Comparator.comparing((p) -> ((GitPath) p),
				getPathLexicographicTemporalComparator());
		return ImmutableSortedSet.copyOf(compareWithoutTies,
				getCachedHistory().getGraph().nodes().stream().map(this::getAbsolutePath).iterator());
	}

	@Override
	public String getSeparator() {
		verify(JIM_FS.getSeparator().equals("/"));
		return "/";
	}

	@Override
	public UserPrincipalLookupService getUserPrincipalLookupService() {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean isOpen() {
		return isOpen;
	}

	@Override
	public boolean isReadOnly() {
		return true;
	}

	byte[] getBytes(ObjectId objectId) throws IOException {
		if (!isOpen) {
			throw new ClosedFileSystemException();
		}

		final ObjectLoader fileLoader = reader.open(objectId, Constants.OBJ_BLOB);
		verify(fileLoader.getType() == Constants.OBJ_BLOB);
		final byte[] bytes = fileLoader.getBytes();
		return bytes;
	}

	@SuppressWarnings("resource")
	DirectoryStream<String> iterate(RevTree tree) throws IOException {
		if (!isOpen) {
			throw new ClosedFileSystemException();
		}

		TreeWalk treeWalk = new TreeWalk(reader);
		treeWalk.addTree(tree);
		treeWalk.setRecursive(false);
		return new TreeWalkDirectoryStream(treeWalk);
	}

	@Override
	public WatchService newWatchService() throws IOException {
		throw new UnsupportedOperationException();
	}

	@Override
	public GitFileSystemProvider provider() {
		return gitProvider;
	}

	long getSize(GitObject gitObject) throws IOException {
		final ObjectLoader fileLoader = reader.open(gitObject.getObjectId());
		verify(fileLoader.getType() == gitObject.getFileMode().getObjectType(),
				"Expected " + gitObject.getFileMode() + " but loaded " + fileLoader.getType());
		return fileLoader.getSize();
	}

	@Override
	public Set<String> supportedFileAttributeViews() {
		throw new UnsupportedOperationException();
	}

	RevTree getRevTree(ObjectId treeId) throws IOException {
		if (!isOpen) {
			throw new ClosedFileSystemException();
		}

		final RevTree revTree;
		try (RevWalk walk = new RevWalk(reader)) {
			revTree = walk.parseTree(treeId);
		}
		return revTree;
	}

	RevCommit getRevCommit(ObjectId possibleCommitId)
			throws MissingObjectException, IncorrectObjectTypeException, IOException {
		if (!isOpen) {
			throw new ClosedFileSystemException();
		}

		final RevCommit revCommit;
		try (RevWalk walk = new RevWalk(reader)) {
			revCommit = walk.parseCommit(possibleCommitId);
		}
		return revCommit;
	}

	GitObject getGitObject(RevTree rootTree, String relativePath) throws IOException, PathNotFoundException {
		if (!isOpen) {
			throw new ClosedFileSystemException();
		}

		final GitObject gitObject;
		try (TreeWalk treeWalk = TreeWalk.forPath(repository, reader, relativePath, rootTree)) {
			if (treeWalk == null) {
				throw new PathNotFoundException();
			}

			final FileMode fileMode = treeWalk.getFileMode();
			verify(fileMode != null);
			final ObjectId objectId = treeWalk.getObjectId(0);
			verify(!objectId.equals(ObjectId.zeroId()), relativePath);
			gitObject = new GitObject(objectId, fileMode);
			verify(!treeWalk.next(), relativePath);
		}
		return gitObject;
	}

	/**
	 * <p>
	 * Returns a gitjfs URI that identifies this git file system, and this specific
	 * git file system instance while it is open.
	 * </p>
	 * <p>
	 * While this instance is open, giving the returned URI to
	 * {@link GitFileSystemProvider#getFileSystem(URI)} will return this file system
	 * instance; giving it to {@link GitFileSystemProvider#getPath(URI)} will return
	 * the default path associated to this file system.
	 * </p>
	 *
	 * @return the URI that identifies this file system.
	 */
	public URI toUri() {
		return gitProvider.getGitFileSystems().toUri(this);
	}

	/**
	 * Note that if this method returns an object id, it means that this object id
	 * exists in the database. But it may be a blob, a tree, … (at least if the
	 * given git ref is a tag, not sure otherwise), see
	 * https://git-scm.com/book/en/v2/Git-Internals-Git-References.
	 */
	Optional<ObjectId> getObjectId(String gitRef) throws IOException {
		if (!isOpen) {
			throw new ClosedFileSystemException();
		}

		final Ref ref = repository.exactRef(gitRef);
		if (ref == null) {
			return Optional.empty();
		}

		verify(ref.getName().equals(gitRef));
		verify(!ref.isSymbolic());
		final ObjectId possibleCommitId = ref.getObjectId();
		verifyNotNull(possibleCommitId);
		return Optional.of(possibleCommitId);
	}

}
