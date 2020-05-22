package io.github.oliviercailloux.git.fs;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Verify.verify;
import static io.github.oliviercailloux.exceptions.Unchecker.IO_UNCHECKER;

import java.io.IOException;
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
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.eclipse.jgit.errors.CorruptObjectException;
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

import com.google.common.base.Verify;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Sets;
import com.google.common.graph.ImmutableGraph;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;

import io.github.oliviercailloux.git.GitLocalHistory;
import io.github.oliviercailloux.git.GitUtils;
import io.github.oliviercailloux.utils.SeekableInMemoryByteChannel;

public class GitRepoFileSystem extends FileSystem {
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
	private static class GitObject {
		/**
		 * <code>null</code> when this is a commit.
		 */
		private FileMode fileMode;
		private ObjectId objectId;

		public GitObject(ObjectId objectId, FileMode fileMode) {
			this.objectId = checkNotNull(objectId);
			this.fileMode = fileMode;
		}
	}

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GitRepoFileSystem.class);

	/**
	 * It is crucial to always use the same instance of Jimfs, because Jimfs refuses
	 * to resolve paths coming from different instances.
	 */
	static final FileSystem JIM_FS = Jimfs.newFileSystem(Configuration.unix());

	static final Path JIM_FS_EMPTY = JIM_FS.getPath("");
	static final Path JIM_FS_SLASH = JIM_FS.getPath("/");

	public static GitRepoFileSystem given(GitFileSystemProvider provider, Repository repository) {
		return new GitRepoFileSystem(provider, repository, false);
	}

	private final GitFileSystemProvider gitProvider;
	private GitLocalHistory history;

	private boolean isOpen;
	private ObjectReader reader;
	private Repository repository;
	private boolean shouldCloseRepository;

	final GitPath masterRoot = new GitPath(this, "master", GitRepoFileSystem.JIM_FS_SLASH);
	final GitPath root = new GitPath(this, "", GitRepoFileSystem.JIM_FS_EMPTY);

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
	}

	public GitPath getAbsolutePath(String revStr) {
		return getAbsolutePath(revStr, "/");
	}

	public GitPath getAbsolutePath(String revStr, String dirAndFile, String... more) {
		checkArgument(!revStr.isEmpty());
		final Path part = JIM_FS_SLASH.resolve(JIM_FS.getPath(dirAndFile, more));
		checkArgument(part.isAbsolute());
		return new GitPath(this, revStr, part);
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

		final String revStr = gitPath.toAbsolutePath().getRevStr();
		return getCommitId(revStr);
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
	public GitPath getPath(String first, String... more) {
		/** Ugly (sob)… */
		if (!first.isEmpty() && !first.contains("//") && !first.endsWith("/")) {
			return getRelativePath(first, more);
		}
		return getPath(first, Arrays.asList(more));
	}

	@Override
	public PathMatcher getPathMatcher(String syntaxAndPattern) {
		throw new UnsupportedOperationException();
	}

	public GitPath getRelativePath(String first, String... more) {
		return getPath("", ImmutableList.<String>builder().add(first).addAll(Arrays.asList(more)).build());
	}

	/**
	 * @return a relative path designating the root of the default (master) commit.
	 */
	public GitPath getRoot() {
		return root;
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
				getCachedHistory().getGraph().nodes().stream().map((c) -> getPath(c.getName() + "/")).iterator());
	}

	@Override
	public String getSeparator() {
		assert JIM_FS.getSeparator().equals("/");
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

		final RevTree tree = getTree(dir).orElseThrow(() -> new NoSuchFileException(dir.toString()));

		final ImmutableSet.Builder<GitPath> builder = ImmutableSet.builder();
		try (TreeWalk treeWalk = new TreeWalk(reader)) {
			treeWalk.addTree(tree);
			treeWalk.setRecursive(false);
			while (treeWalk.next()) {
				final GitPath path = dir.resolve(new GitPath(this, "", JIM_FS.getPath(treeWalk.getNameString())));
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

	public BasicFileAttributes readAttributes(GitPath gitPath, ImmutableSet<LinkOption> optionsSet) throws IOException {
		final GitObject gitObject;
		if (gitPath.toRelativePath().equals(root)) {
			final String revStr = gitPath.toAbsolutePath().getRevStr();
			final Optional<RevTree> commitTree = getCommitTree(revStr);
			if (commitTree.isEmpty()) {
				throw new NoSuchFileException(gitPath.toString());
			}
			gitObject = new GitObject(commitTree.get(), FileMode.TREE);
		} else {
			gitObject = getGitObjectNotRoot(gitPath).orElseThrow(() -> new NoSuchFileException(gitPath.toString()));
		}

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

	private GitObject getAndCheckCommit(GitPath gitPath)
			throws IOException, NoSuchFileException, MissingObjectException, IncorrectObjectTypeException {
		final String revStr = gitPath.toAbsolutePath().getRevStr();
		final Optional<ObjectId> commitIdOpt = getCommitId(revStr);
		if (commitIdOpt.isEmpty()) {
			throw new NoSuchFileException(gitPath.toString());
		}
		if (isObjectId(revStr)) {
			if (getCommitTree(revStr).isEmpty()) {
				throw new NoSuchFileException(gitPath.toString());
			}
		}
		final ObjectId commitId = commitIdOpt.get();
		return new GitObject(commitId, null);
	}

	private GitObject getAndCheckGitObject(GitPath gitPath) throws IOException, NoSuchFileException,
			MissingObjectException, IncorrectObjectTypeException, CorruptObjectException {
		final GitObject gitObject;
		if (gitPath.toRelativePath().equals(root)) {
			gitObject = getAndCheckCommit(gitPath);
		} else {
			gitObject = getGitObjectNotRoot(gitPath).orElseThrow(() -> new NoSuchFileException(gitPath.toString()));
		}
		return gitObject;
	}

	private Optional<ObjectId> getCommitId(String revStr) throws IOException {
		if (isObjectId(revStr)) {
			return Optional.of(ObjectId.fromString(revStr));
		}
		return getCommitIdFromRef(revStr);
	}

	/**
	 * @param refStr must be a ref, such as “master”, and not a SHA-1 object id
	 */
	private Optional<ObjectId> getCommitIdFromRef(String refStr) throws IOException {
		final Optional<ObjectId> commitId;

		final Ref ref = repository.findRef(refStr);
		if (ref == null) {
			LOGGER.debug("Ref str " + refStr + " not found.");
			commitId = Optional.empty();
		} else {
			commitId = Optional.ofNullable(ref.getLeaf().getObjectId());
			if (commitId.isEmpty()) {
				/** TODO enquire how this can happen. */
				LOGGER.debug("Ref " + ref.getName() + " does not exist yet.");
			} else {
				LOGGER.debug("Ref {} found: {}.", ref.getName(), commitId);
			}
		}
		return commitId;
	}

	private Optional<RevTree> getCommitTree(String revStr)
			throws IOException, MissingObjectException, IncorrectObjectTypeException {
		final Optional<ObjectId> commitId = getCommitId(revStr);

		final Optional<RevCommit> commit;
		if (commitId.isEmpty()) {
			commit = Optional.empty();
		} else {
			try (RevWalk walk = new RevWalk(reader)) {
				commit = Optional.of(walk.parseCommit(commitId.get()));
			}
		}

		return commit.map(RevCommit::getTree);
	}

	/**
	 * @param gitPath dirAndFile part must be non-empty and not "/".
	 */
	private Optional<GitObject> getGitObjectNotRoot(GitPath gitPath)
			throws IOException, MissingObjectException, IncorrectObjectTypeException, CorruptObjectException {
		checkArgument(!gitPath.toRelativePath().equals(root));
		final GitPath withoutRoot = gitPath.toRelativePath();
		/**
		 * TreeWalk.forPath says “Empty path not permitted” if using "" or "/".
		 */
		checkArgument(!withoutRoot.equals(root));

		final Optional<RevTree> treeOpt = getCommitTree(gitPath.toAbsolutePath().getRevStr());

		final Optional<GitObject> objectId;
		if (treeOpt.isEmpty()) {
			objectId = Optional.empty();
		} else {
			final RevTree tree = treeOpt.get();
			try (TreeWalk treeWalk = TreeWalk.forPath(repository, withoutRoot.toString(), tree)) {
				if (treeWalk == null) {
					objectId = Optional.empty();
					LOGGER.debug("Path " + withoutRoot + " not found.");
				} else {
					objectId = Optional.of(new GitObject(treeWalk.getObjectId(0), treeWalk.getFileMode()));
					verify(!treeWalk.next());
				}
			}
		}
		verify(objectId.isEmpty() || objectId.get().fileMode != null);
		return objectId;
	}

	private GitPath getPath(String first, List<String> more) {
		/**
		 * Get first arg until // or end of string. First arg may not start with /.
		 *
		 * Build a list of dirAndFile arguments, constituted of the rest of the first
		 * arg if it contains "//" (taking everything after the first / in "//", thus
		 * including a slash), and all others, concatenated then split at "/". If
		 * resulting revStr is not empty, and the list of dirAndFile is not empty, then
		 * dirAndFile must start with "/". If revStr is not empty and dirAndFile is
		 * empty, dirAndFile is considered as slash. If revStr is empty, and the list of
		 * dirAndFile is not empty, then dirAndFile must not start with /. If revStr is
		 * empty, and dirAndFile is empty, dirAndFile is considered as "".
		 *
		 * TODO change this, this is complicated and unclear: intuition and endsWith
		 * suggest that the path corresponding to the simple string "ploum.txt" should
		 * transform to a relative path. We should simply decide that this is an
		 * absolute path, thus containing a revStr, iff the first component starts with
		 * slash.
		 */
		checkArgument(!first.startsWith("/"));
		checkArgument(first.isEmpty() || first.contains("//") || first.endsWith("/"), first);
		final int startDoubleSlash = first.indexOf("//");
		verify(startDoubleSlash != 0);
		final String revStr;
		if (startDoubleSlash == -1) {
			revStr = first.isEmpty() ? "" : first.substring(0, first.length() - 1);
		} else {
			revStr = first.substring(0, startDoubleSlash);
		}
		verify(!revStr.startsWith("/"));
		verify(!revStr.endsWith("/"));
		final String restFirst;
		if (startDoubleSlash == -1) {
			restFirst = "";
		} else {
			restFirst = first.substring(startDoubleSlash + 1, first.length());
			verify(restFirst.startsWith("/"));
		}

		final ImmutableList<String> dirAndFile = ImmutableList.<String>builder().add(restFirst).addAll(more).build()
				.stream().filter((s) -> !s.isEmpty()).collect(ImmutableList.toImmutableList());

		if (!dirAndFile.isEmpty()) {
			checkArgument(revStr.isEmpty() == !dirAndFile.get(0).startsWith("/"));
		}

		final Path effectiveDirAndFile;
		if (dirAndFile.isEmpty()) {
			if (revStr.isEmpty()) {
				effectiveDirAndFile = JIM_FS_EMPTY;
			} else {
				effectiveDirAndFile = JIM_FS_SLASH;
			}
		} else {
			effectiveDirAndFile = JIM_FS.getPath(dirAndFile.get(0),
					dirAndFile.subList(1, dirAndFile.size()).toArray(new String[] {}));
		}

		Verify.verify(!revStr.isEmpty() == effectiveDirAndFile.isAbsolute());

		return new GitPath(this, revStr, effectiveDirAndFile);
	}

	/**
	 * Paths must have a revStr in the form of a sha1, not a reference such as
	 * "master".
	 */
	private Comparator<GitPath> getPathLexicographicTemporalComparator() {
		final Comparator<ObjectId> compareWithoutTies = getLexicographicTemporalComparator();
		return Comparator.comparing(p -> ObjectId.fromString(p.getRevStr()), compareWithoutTies);
	}

	private Optional<RevTree> getTree(GitPath dir)
			throws IOException, MissingObjectException, IncorrectObjectTypeException, CorruptObjectException {
		if (dir.toRelativePath().equals(root)) {
			return getCommitTree(dir.toAbsolutePath().getRevStr());
		}

		final Optional<GitObject> gitObjectOpt = getGitObjectNotRoot(dir);
		if (gitObjectOpt.isEmpty()) {
			return Optional.empty();
		}
		final GitObject gitObject = gitObjectOpt.get();
		assert gitObject.fileMode != null;
		if (!gitObject.fileMode.equals(FileMode.TYPE_TREE)) {
			throw new NotDirectoryException(dir.toString());
		}

		final RevTree tree;
		try (RevWalk walk = new RevWalk(reader)) {
			tree = walk.parseTree(gitObject.objectId);
		}
		return Optional.of(tree);
	}

	private boolean isObjectId(String revStr) {
		/**
		 * This is incorrect (I suppose): a ref could have the same length as an object
		 * id.
		 */
		return revStr.length() == 40;
	}

	Repository getRepository() {
		return repository;
	}

}
