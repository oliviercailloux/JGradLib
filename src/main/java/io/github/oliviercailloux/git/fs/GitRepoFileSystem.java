package io.github.oliviercailloux.git.fs;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Verify.verify;
import static io.github.oliviercailloux.jaris.exceptions.Unchecker.IO_UNCHECKER;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.AccessMode;
import java.nio.file.ClosedFileSystemException;
import java.nio.file.DirectoryStream;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
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
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Sets;
import com.google.common.graph.ImmutableGraph;

import io.github.oliviercailloux.git.GitLocalHistory;
import io.github.oliviercailloux.git.GitUtils;
import io.github.oliviercailloux.utils.SeekableInMemoryByteChannel;

public abstract class GitRepoFileSystem extends FileSystem {
	private static class DirectoryChannel implements SeekableByteChannel {
		private boolean open;

		public DirectoryChannel() {
			open = true;
		}

		@Override
		public void close() throws IOException {
			open = false;
		}

		@Override
		public boolean isOpen() {
			return open;
		}

		@Override
		public long position() throws IOException {
			return 0;
		}

		@Override
		public SeekableByteChannel position(long newPosition) throws IOException {
			throw new IOException("is a folder");
		}

		@Override
		public int read(ByteBuffer dst) throws IOException {
			throw new IOException("is a folder");
		}

		@Override
		public long size() throws IOException {
			throw new IOException("is a folder");
		}

		@Override
		public SeekableByteChannel truncate(long size) throws IOException {
			throw new IOException("is a folder");
		}

		@Override
		public int write(ByteBuffer src) throws IOException {
			throw new IOException("is a folder");
		}

	}

	private static class GitBasicFileAttributes implements BasicFileAttributes {
		private GitObject gitObject;
		private long size;

		public GitBasicFileAttributes(GitObject gitObject, long size) {
			this.gitObject = checkNotNull(gitObject);
			final FileMode fileMode = gitObject.fileMode;
			checkArgument(fileMode == null || !fileMode.equals(FileMode.TYPE_GITLINK));
			checkArgument(fileMode == null || !fileMode.equals(FileMode.TYPE_MASK));
			checkArgument(fileMode == null || !fileMode.equals(FileMode.TYPE_MISSING));
			this.size = size;
		}

		@Override
		public FileTime creationTime() {
			return FileTime.fromMillis(0);
		}

		@Override
		public ObjectId fileKey() {
			return gitObject.objectId;
		}

		@Override
		public boolean isDirectory() {
			return gitObject.fileMode == null || Objects.equals(gitObject.fileMode, FileMode.TREE);
		}

		@Override
		public boolean isOther() {
			return false;
		}

		@Override
		public boolean isRegularFile() {
			return Objects.equals(gitObject.fileMode, FileMode.REGULAR_FILE)
					|| Objects.equals(gitObject.fileMode, FileMode.EXECUTABLE_FILE);
		}

		@Override
		public boolean isSymbolicLink() {
			return Objects.equals(gitObject.fileMode, FileMode.SYMLINK);
		}

		@Override
		public FileTime lastAccessTime() {
			return FileTime.fromMillis(0);
		}

		@Override
		public FileTime lastModifiedTime() {
			return FileTime.fromMillis(0);
		}

		@Override
		public long size() {
			return size;
		}

	}

	/**
	 * It would be clearer to have three kinds of git objects. One, with a file mode
	 * (an objectid that has been found with a tree walker and corresponds to a file
	 * or a sub-directory). Second, a commit that has been checked to exist,
	 * together with its tree! Third, a commit that has not been checked to exist
	 * (the object id was given right from the start and we never had to walk
	 * anything).
	 */
	static class GitObject {
		private final RevCommit commit;
		private final ObjectId objectId;
		private final FileMode fileMode;

		public GitObject(RevCommit commit, ObjectId objectId, FileMode fileMode) {
			this.commit = checkNotNull(commit);
			this.objectId = checkNotNull(objectId);
			this.fileMode = checkNotNull(fileMode);
		}

		RevCommit getCommit() {
			return commit;
		}

		ObjectId getObjectId() {
			return objectId;
		}

		FileMode getFileMode() {
			return fileMode;
		}
	}

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GitRepoFileSystem.class);

	private final GitFileSystemProvider gitProvider;
	private GitLocalHistory history;

	private boolean isOpen;
	private ObjectReader reader;
	private Repository repository;
	private boolean shouldCloseRepository;

	final GitPath mainSlash = GitPath.absolute(this, RootComponent.DEFAULT, GitFileSystemProvider.JIM_FS_SLASH);
	final GitPath defaultPath = GitPath.relative(this, GitFileSystemProvider.JIM_FS_EMPTY);

	protected GitRepoFileSystem(GitFileSystemProvider gitProvider, Repository repository,
			boolean shouldCloseRepository) {
		this.gitProvider = checkNotNull(gitProvider);
		this.repository = checkNotNull(repository);
		this.shouldCloseRepository = shouldCloseRepository;
		reader = repository.newObjectReader();
		reader.setAvoidUnreachableObjects(true);
		isOpen = true;
		history = null;
	}

	public void checkAccess(GitPath gitPath, AccessMode... modes)
			throws MissingObjectException, IncorrectObjectTypeException, CorruptObjectException, IOException {
		final ImmutableSet<AccessMode> modesList = ImmutableSet.copyOf(modes);
		if (modesList.contains(AccessMode.WRITE)) {
			throw new AccessDeniedException(gitPath.toString());
		}
		if (!Sets.difference(modesList, ImmutableSet.of(AccessMode.READ, AccessMode.EXECUTE)).isEmpty()) {
			throw new UnsupportedOperationException();
		}

		final GitObject gitObject = getAndCheckGitObject(gitPath);

		if (modesList.contains(AccessMode.EXECUTE)) {
			if (!Objects.equals(gitObject.fileMode, FileMode.EXECUTABLE_FILE)) {
				throw new AccessDeniedException(gitPath.toString());
			}
		}
	}

	@Override
	public void close() {
		isOpen = false;
		reader.close();
		if (shouldCloseRepository) {
			repository.close();
		}
		provider().getGitFileSystems().hasBeenClosedEvent(this);
	}

	@Override
	public GitPath getPath(String first, String... more) {
		if (first.startsWith("/")) {
			return getAbsolutePath(first, more);
		}
		return getRelativePath(
				Stream.concat(Stream.of(first), Stream.of(more)).collect(ImmutableList.toImmutableList()));
	}

	/**
	 * @param rootStringForm with prefix and trailing slash.
	 * @param internalPath   may contain slashes.
	 * @return
	 */
	public GitPath getAbsolutePath(String rootStringForm, String... internalPath) {
		return getAbsolutePath(RootComponent.givenStringForm(rootStringForm), ImmutableList.copyOf(internalPath));
	}

	public GitPath getAbsolutePath(ObjectId objectId, String... internalPath) {
		return getAbsolutePath(RootComponent.givenObjectId(objectId), ImmutableList.copyOf(internalPath));
	}

	private GitPath getAbsolutePath(RootComponent root, List<String> internalPath) {
		checkNotNull(root);
		Path internalJimPath = GitFileSystemProvider.JIM_FS_SLASH;
		for (String name : internalPath) {
			internalJimPath = internalJimPath.resolve(name);
		}
		return GitPath.absolute(this, root, internalJimPath);
	}

	public GitPath getRelativePath(String... names) {
		return getRelativePath(ImmutableList.copyOf(names));
	}

	private GitPath getRelativePath(List<String> internalPath) {
		Path internalJimPath = GitFileSystemProvider.JIM_FS_EMPTY;
		for (String name : internalPath) {
			internalJimPath = internalJimPath.resolve(name);
			checkArgument(!internalJimPath.isAbsolute(), name + " makes this internal path absolute.");
		}
		return GitPath.relative(this, internalJimPath);
	}

	public GitLocalHistory getCachedHistory() {
		checkState(history != null);
		return history;
	}

	/**
	 * if the given path is a reference, and this method returns an objectId, then
	 * it implies existence of that commit root; otherwise (thus if the given path
	 * contains an object id and not a reference), this method simply returns the
	 * object id without checking for its validity.
	 */
	public Optional<ObjectId> getCommitId(GitPath gitPath) throws IOException {
		if (!isOpen) {
			throw new ClosedFileSystemException();
		}

		return gitPath.getCommitId();
	}

	@Override
	public Iterable<FileStore> getFileStores() {
		throw new UnsupportedOperationException();
	}

	/**
	 * @return absolute paths whose rev strings are sha1 object ids, ordered with
	 *         earlier commits coming first.
	 */
	public ImmutableSortedSet<GitPath> getGitRootDirectories() {
		if (!isOpen) {
			throw new ClosedFileSystemException();
		}

		IO_UNCHECKER.call(() -> getHistory());

		final Comparator<GitPath> compareWithoutTies = getPathLexicographicTemporalComparator();

		/** Important to compare without ties, otherwise some commits get collapsed. */
		return ImmutableSortedSet.copyOf(compareWithoutTies,
				getCachedHistory().getGraph().nodes().stream().map((c) -> getPath(c.getName() + "/")).iterator());
	}

	public GitLocalHistory getHistory() throws IOException {
		if (history == null) {
			history = GitUtils.getHistory(repository);
		}
		return history;
	}

	public Comparator<ObjectId> getLexicographicTemporalComparator() {
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

	@Override
	public PathMatcher getPathMatcher(String syntaxAndPattern) {
		throw new UnsupportedOperationException();
	}

	/**
	 * @return a relative path designating the root of the default (main) commit.
	 */
	public GitPath getRoot() {
		return defaultPath;
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
		assert GitFileSystemProvider.JIM_FS.getSeparator().equals("/");
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

	public SeekableByteChannel newByteChannel(GitPath gitPath) throws IOException {
		if (!isOpen) {
			throw new ClosedFileSystemException();
		}

		final GitObject gitObject = getAndCheckGitObject(gitPath);
		if (gitObject.fileMode == null || gitObject.fileMode.equals(FileMode.TYPE_TREE)) {
			return new DirectoryChannel();
		}
		if (!gitObject.fileMode.equals(FileMode.TYPE_FILE)) {
			throw new IOException("Unexpected file type: " + gitObject.fileMode);
		}

		final ObjectLoader fileLoader = reader.open(gitObject.objectId, Constants.OBJ_BLOB);
		verify(fileLoader.getType() == Constants.OBJ_BLOB);
		final byte[] bytes = fileLoader.getBytes();
		/**
		 * Should not log here: if the charset is not UTF-8, this messes up the output.
		 */
//		LOGGER.debug("Read: {}.", new String(bytes, StandardCharsets.UTF_8));
		return new SeekableInMemoryByteChannel(bytes);
	}

	public DirectoryStream<GitPath> newDirectoryStream(GitPath dir, Filter<? super Path> filter) throws IOException {
		if (!isOpen) {
			throw new ClosedFileSystemException();
		}

		final GitObject gitObject = getAndCheckGitObject(dir);
		if (!gitObject.fileMode.equals(FileMode.TYPE_TREE)) {
			throw new NotDirectoryException(dir.toString());
		}

		final RevTree tree;
		if (dir.toRelativePath().equals(defaultPath)) {
			tree = gitObject.commit.getTree();
		} else {
			try (RevWalk walk = new RevWalk(reader)) {
				tree = walk.parseTree(gitObject.objectId);
			}
		}

		final ImmutableSet.Builder<GitPath> builder = ImmutableSet.builder();
		try (TreeWalk treeWalk = new TreeWalk(reader)) {
			treeWalk.addTree(tree);
			treeWalk.setRecursive(false);
			while (treeWalk.next()) {
				final GitPath path = dir.resolve(
						GitPath.relative(this, GitFileSystemProvider.JIM_FS.getPath(treeWalk.getNameString())));
				if (!filter.accept(path)) {
					continue;
				}
				builder.add(path);
			}
		}
		final ImmutableSet<GitPath> built = builder.build();

		return new DirectoryStream<>() {
			@Override
			public void close() throws IOException {
				// nothing to do.
			}

			@Override
			public Iterator<GitPath> iterator() {
				return built.iterator();
			}
		};
	}

	@Override
	public WatchService newWatchService() throws IOException {
		throw new UnsupportedOperationException();
	}

	@Override
	public GitFileSystemProvider provider() {
		return gitProvider;
	}

	URI toUri() {
		return gitProvider.getGitFileSystems().toUri(this);
	}

	public BasicFileAttributes readAttributes(GitPath gitPath, ImmutableSet<LinkOption> optionsSet) throws IOException {
		final GitObject gitObject = getAndCheckGitObject(gitPath);

		final ObjectLoader fileLoader = reader.open(gitObject.objectId);
		verify(fileLoader.getType() == gitObject.fileMode.getObjectType(),
				"Expected " + gitObject.fileMode + " but loaded " + fileLoader.getType());

		final GitBasicFileAttributes gitBasicFileAttributes = new GitBasicFileAttributes(gitObject,
				fileLoader.getSize());
		if (!optionsSet.contains(LinkOption.NOFOLLOW_LINKS) && gitBasicFileAttributes.isSymbolicLink()) {
			throw new UnsupportedOperationException(
					"Path " + gitPath.toString() + "is a sym link; I do not follow symlinks yet.");
		}
		return gitBasicFileAttributes;
	}

	@Override
	public Set<String> supportedFileAttributeViews() {
		throw new UnsupportedOperationException();
	}

	private RevCommit getRevCommit(GitPath gitPath) throws IOException, NoSuchFileException {
		final ObjectId commitId = gitPath.getCommitIdOrThrow();

		final RevCommit revCommit;
		try (RevWalk walk = new RevWalk(reader)) {
			revCommit = walk.parseCommit(commitId);
		} catch (@SuppressWarnings("unused") MissingObjectException e) {
			throw new NoSuchFileException(gitPath.toString());
		} catch (IncorrectObjectTypeException e) {
			LOGGER.info("Tried to access a non-commit as a commit: " + e + ".");
			throw new NoSuchFileException(gitPath.toString());
		}
		return revCommit;
	}

	GitObject getAndCheckGitObject(GitPath gitPath) throws IOException, NoSuchFileException {
		final RevCommit commit = getRevCommit(gitPath);
		final RevTree tree = commit.getTree();
		final GitPath relativePath = gitPath.toRelativePath();

		final GitObject gitObject;
		if (relativePath.equals(defaultPath)) {
			gitObject = new GitObject(commit, tree, FileMode.TREE);
		} else {
			/**
			 * TreeWalk.forPath says “Empty path not permitted” if using "" or "/".
			 */
			LOGGER.debug("Searching for {} in {}.", relativePath, tree);
			try (TreeWalk treeWalk = TreeWalk.forPath(repository, reader, relativePath.toString(), tree)) {
				if (treeWalk == null) {
					LOGGER.debug("Path " + relativePath + " not found.");
					throw new NoSuchFileException(gitPath.toString());
				}

				final FileMode fileMode = treeWalk.getFileMode();
				verify(fileMode != null);
				final ObjectId objectId = treeWalk.getObjectId(0);
				verify(!objectId.equals(ObjectId.zeroId()), gitPath.toString());
				gitObject = new GitObject(commit, objectId, fileMode);
				verify(!treeWalk.next());
			} catch (MissingObjectException | IncorrectObjectTypeException e) {
				throw new VerifyException(e);
			} catch (CorruptObjectException e) {
				throw new IOException(e);
			}
		}
		return gitObject;
	}

	/**
	 * Paths must have a root in the form of a sha1.
	 */
	private Comparator<GitPath> getPathLexicographicTemporalComparator() {
		final Comparator<ObjectId> compareWithoutTies = getLexicographicTemporalComparator();
		return Comparator.comparing((GitPath p) -> p.getRootComponent().getObjectId(), compareWithoutTies);
	}

	protected Repository getRepository() {
		return repository;
	}

	@Override
	public boolean equals(Object o2) {
		if (!(o2 instanceof GitRepoFileSystem)) {
			return false;
		}
		final GitRepoFileSystem g2 = (GitRepoFileSystem) o2;
		return repository.equals(g2.repository);
	}

	@Override
	public int hashCode() {
		return Objects.hash(repository);
	}

}
