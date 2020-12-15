package io.github.oliviercailloux.git.fs;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Verify.verify;
import static com.google.common.base.Verify.verifyNotNull;

import java.io.IOException;
import java.io.UncheckedIOException;
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

import com.google.common.base.VerifyException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.PeekingIterator;
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
 * Must be {@link #close() closed} to release resources associated with readers.
 * </p>
 *
 * @see #getAbsolutePath(String, String...)
 * @see #getRelativePath(String...)
 * @see #getGitRootDirectories()
 */
public abstract class GitFileSystem extends FileSystem {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GitFileSystem.class);

	/**
	 * Used instead of {@link NoSuchFileException} at places where we can’t get the
	 * string form of the path that is not found (because it is only known to the
	 * caller).
	 */
	@SuppressWarnings("serial")
	static class PathNotFoundException extends Exception {
	}

	static class TreeWalkIterator implements PeekingIterator<String> {
		private final TreeWalk walk;
		private Boolean hasNext = null;

		private String next;

		public TreeWalkIterator(TreeWalk walk) {
			this.walk = checkNotNull(walk);
			hasNext = null;
			next = null;
		}

		@Override
		public boolean hasNext() throws DirectoryIteratorException {
			if (hasNext != null) {
				return hasNext;
			}

			try {
				hasNext = walk.next();
			} catch (IOException e) {
				verify(hasNext == null);
				throw new DirectoryIteratorException(e);
			}

			next = hasNext ? walk.getNameString() : null;

			return hasNext;
		}

		@Override
		public String peek() throws DirectoryIteratorException {
			if (!hasNext()) {
				throw new NoSuchElementException();
			}
			return next;
		}

		@Override
		public String next() throws DirectoryIteratorException {
			final String current = peek();
			hasNext = null;
			next = null;
			return current;
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException();
		}

		public void close() {
			hasNext = false;
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

		/**
		 * As requested per the contract of {@link DirectoryStream}, invoking the
		 * iterator method to obtain a second or subsequent iterator throws
		 * IllegalStateException.
		 * <p>
		 * An important property of the directory stream's Iterator is that its hasNext
		 * method is guaranteed to read-ahead by at least one element. If hasNext method
		 * returns true, and is followed by a call to the next method, it is guaranteed
		 * that the next method will not throw an exception due to an I/O error, or
		 * because the stream has been closed. The Iterator does not support the remove
		 * operation.
		 * <p>
		 * Once a directory stream is closed, then further access to the directory,
		 * using the Iterator, behaves as if the end of stream has been reached. Due to
		 * read-ahead, the Iterator may return one or more elements after the directory
		 * stream has been closed.
		 * <p>
		 * If an I/O error is encountered when accessing the directory then it causes
		 * the Iterator's hasNext or next methods to throw DirectoryIteratorException
		 * with the IOException as the cause. As stated above, the hasNext method is
		 * guaranteed to read-ahead by at least one element. This means that if hasNext
		 * method returns true, and is followed by a call to the next method, then it is
		 * guaranteed that the next method will not fail with a
		 * DirectoryIteratorException.
		 */
		@Override
		public PeekingIterator<String> iterator() {
			if (returned || closed) {
				throw new IllegalStateException();
			}
			returned = true;
			return iterator;
		}

	}

	/**
	 * It is crucial to always use the same instance of Jimfs, because Jimfs refuses
	 * to resolve paths coming from different instances. And the references to JimFs
	 * might be better here rather than in {@link GitFileSystemProvider} because
	 * when {@link GitFileSystemProvider} is initialized, we do not want to refer to
	 * JimFs, which might not be initialized yet (perhaps this should not create
	 * problems, but as it seems logically better to have these references here
	 * anyway, I did not investigate).
	 */
	static final FileSystem JIM_FS = Jimfs
			.newFileSystem(Configuration.unix().toBuilder().setWorkingDirectory("/").build());

	static final Path JIM_FS_EMPTY = JIM_FS.getPath("");

	static final Path JIM_FS_SLASH = JIM_FS.getPath("/");

	private final GitFileSystemProvider gitProvider;
	private GitLocalHistory history;

	private boolean isOpen;
	private final ObjectReader reader;
	private final Repository repository;
	private final boolean shouldCloseRepository;

	private final Set<DirectoryStream<GitPath>> toClose;

	final GitPathRoot mainSlash = new GitPathRoot(this, GitPathRoot.DEFAULT_GIT_REF);
	final GitEmptyPath emptyPath = new GitEmptyPath(mainSlash);

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
		for (@SuppressWarnings("resource")
		DirectoryStream<GitPath> closeable : toClose) {
			try {
				closeable.close();
			} catch (IOException e) {
				throw new VerifyException("Close should not throw exceptions.", e);
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
	 * <p>
	 * No check is performed to ensure that the path refers to an existing git
	 * object in this git file system.
	 * </p>
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

		return GitRelativePath.relative(this, allNames);
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
	 * <code>/</code>, then a <code>/</code> is added so that there will be two
	 * slashes joining {@code first} to {@code more}.
	 * </p>
	 * <p>
	 * For example, if {@code getAbsolutePath("/refs/heads/main/","foo","bar")} is
	 * invoked, then the path string {@code "/refs/heads/main//foo/bar"} is
	 * converted to a {@code Path}.
	 * </p>
	 * <p>
	 * No check is performed to ensure that the path refers to an existing git
	 * object in this git file system.
	 * </p>
	 *
	 * @param first the string form of the root component, possibly followed by
	 *              other path segments. Must start with <tt>/refs/</tt> or
	 *              <tt>/heads/</tt> or <tt>/tags/</tt> or be a slash followed by a
	 *              40-characters long sha-1; must contain at most once
	 *              <code>//</code>; if does not contain <code>//</code>, must end
	 *              with <code>/</code>.
	 * @param more  may start with <code>/</code>.
	 * @return an absolute git path.
	 * @throws InvalidPathException if {@code first} does not contain a syntaxically
	 *                              valid root component
	 * @see GitPath
	 */
	public GitPath getAbsolutePath(String first, String... more) throws InvalidPathException {
		final String rootStringForm;
		final ImmutableList<String> internalPath;
		if (first.contains("//")) {
			final int startDoubleSlash = first.indexOf("//");
			final String beforeMiddleOfDoubleSlash = first.substring(0, startDoubleSlash + 1);
			final String afterMiddleOfDoubleSlash = first.substring(startDoubleSlash + 1);
			rootStringForm = beforeMiddleOfDoubleSlash;
			internalPath = Stream.concat(Stream.of(afterMiddleOfDoubleSlash), Stream.of(more))
					.collect(ImmutableList.toImmutableList());
		} else {
			rootStringForm = first;
			final List<String> givenMore = new ArrayList<>(ImmutableList.copyOf(more));
			if (givenMore.isEmpty()) {
				givenMore.add("/");
			} else if (!givenMore.get(0).startsWith("/")) {
				givenMore.set(0, "/" + givenMore.get(0));
			}
			internalPath = ImmutableList.copyOf(givenMore);
		}
		verify(internalPath.isEmpty() || internalPath.get(0).startsWith("/"));

		return GitAbsolutePath.givenRoot(new GitPathRoot(this, GitRev.stringForm(rootStringForm)), internalPath);
	}

	/**
	 * Returns a git path referring to a commit designated by its id. No check is
	 * performed to ensure that the commit exists.
	 *
	 * @param commitId     the commit to refer to
	 * @param internalPath may start with a slash.
	 * @return an absolute path
	 * @see GitPath
	 */
	public GitPath getAbsolutePath(ObjectId commitId, String... internalPath) {
		final List<String> givenMore = new ArrayList<>(ImmutableList.copyOf(internalPath));
		if (givenMore.isEmpty()) {
			givenMore.add("/");
		} else if (!givenMore.get(0).startsWith("/")) {
			givenMore.set(0, "/" + givenMore.get(0));
		}
		return GitAbsolutePath.givenRoot(new GitPathRoot(this, GitRev.commitId(commitId)), givenMore);
	}

	/**
	 * Returns a git path referring to a commit designated by its id. No check is
	 * performed to ensure that the commit exists.
	 *
	 * @param rootStringForm the string form of the root component. Must start with
	 *                       <tt>/refs/</tt> or <tt>/heads/</tt> or <tt>/tags/</tt>
	 *                       or be a 40-characters long sha-1 surrounded by slash
	 *                       characters; must end with <tt>/</tt>; may not contain
	 *                       <tt>//</tt> nor <tt>\</tt>.
	 * @return a git path root
	 * @throws InvalidPathException if {@code rootStringForm} does not contain a
	 *                              syntaxically valid root component
	 * @see GitPathRoot
	 */
	public GitPathRoot getPathRoot(String rootStringForm) throws InvalidPathException {
		return new GitPathRoot(this, GitRev.stringForm(rootStringForm));
	}

	/**
	 * Returns a git path referring to a commit designated by its id. No check is
	 * performed to ensure that the commit exists.
	 *
	 * @param commitId the commit to refer to
	 * @return an git path root
	 * @see GitPathRoot
	 */
	public GitPathRoot getPathRoot(ObjectId commitId) {
		return new GitPathRoot(this, GitRev.commitId(commitId));
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
	 * An <em>empty</em> path is returned iff names contain only empty strings. It
	 * then implicitly refers to the main branch of this git file system.
	 * </p>
	 * <p>
	 * No check is performed to ensure that the path refers to an existing git
	 * object in this git file system.
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
		return GitRelativePath.relative(this, ImmutableList.copyOf(names));
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
	 * Retrieve the set of all commits of this repository. Consider calling rather
	 * {@link #getGitRootDirectories()}, whose type is more precise.
	 *
	 * @return absolute path roots referring to commit ids.
	 * @throws UncheckedIOException if an I/O error occurs (I have no idea why the
	 *                              Java Files API does not want an IOException
	 *                              here)
	 */
	@Override
	public ImmutableSet<Path> getRootDirectories() throws UncheckedIOException {
		final ImmutableSet<ObjectId> allCommits = getAllCommits();
		final ImmutableSet<Path> paths = allCommits.stream().map(this::getPathRoot)
				.collect(ImmutableSet.toImmutableSet());
		return paths;
	}

	/**
	 * Retrieve the set of all commits of this repository reachable from some ref.
	 * This is equivalent to calling {@link #getRootDirectories()}, but with a more
	 * precise type.
	 *
	 * @return absolute path roots, all referring to commit ids (no ref).
	 * @throws UncheckedIOException if an I/O error occurs (using an Unchecked
	 *                              variant to mimic the behavior of
	 *                              {@link #getRootDirectories()})
	 */
	public ImmutableSet<GitPathRoot> getCommits() throws UncheckedIOException {
		final ImmutableSet<ObjectId> allCommits = getAllCommits();
		final ImmutableSet<GitPathRoot> paths = allCommits.stream().map(this::getPathRoot)
				.collect(ImmutableSet.toImmutableSet());
		return paths;
	}

	/**
	 * Returns a set containing one git path root for each git ref (of the form
	 * <tt>/refs/…</tt>) contained in this git file system. This does not consider
	 * HEAD or other special references, but considers both branches and tags.
	 *
	 * @return git path roots referencing git refs (not commit ids).
	 *
	 * @throws IOException if an I/O error occurs
	 */
	public ImmutableSet<GitPathRoot> getRefs() throws IOException {
		if (!isOpen) {
			throw new ClosedFileSystemException();
		}

		final List<Ref> refs = repository.getRefDatabase().getRefsByPrefix(Constants.R_REFS);
		return refs.stream().map(r -> getPathRoot("/" + r.getName() + "/")).collect(ImmutableSet.toImmutableSet());
	}

	private ImmutableSet<ObjectId> getAllCommits() throws UncheckedIOException {
		if (!isOpen) {
			throw new ClosedFileSystemException();
		}

		final ImmutableSet<ObjectId> allCommits;
		try (RevWalk walk = new RevWalk(reader)) {
			/**
			 * Not easy to get really all commits, so we are content with returning only the
			 * ones reachable from some ref: this is the normal behavior of git, it seems
			 * (https://stackoverflow.com/questions/4786972).
			 */
			final List<Ref> refs = repository.getRefDatabase().getRefsByPrefix(Constants.R_REFS);
			walk.setRetainBody(false);
			for (Ref ref : refs) {
				walk.markStart(walk.parseCommit(ref.getLeaf().getObjectId()));
			}
			allCommits = ImmutableSet.copyOf(walk);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return allCommits;
	}

	public GitLocalHistory getHistory() throws IOException {
		if (history == null) {
			history = GitUtils.getHistory(repository);
		}
		return history;
	}

	@Override
	public PathMatcher getPathMatcher(String syntaxAndPattern) {
		throw new UnsupportedOperationException();
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
	TreeWalkDirectoryStream iterate(RevTree tree) throws IOException {
		if (!isOpen) {
			throw new ClosedFileSystemException();
		}

		final TreeWalk treeWalk = new TreeWalk(reader);
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
		if (!isOpen) {
			throw new ClosedFileSystemException();
		}

		final ObjectLoader fileLoader = reader.open(gitObject.getObjectId());
		verify(fileLoader.getType() == gitObject.getFileMode().getObjectType(),
				String.format("Expected file mode %s and object type %s but loaded object type %s",
						gitObject.getFileMode(), gitObject.getFileMode().getObjectType(), fileLoader.getType()));
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

	void toClose(DirectoryStream<GitPath> stream) {
		toClose.add(stream);
	}

}