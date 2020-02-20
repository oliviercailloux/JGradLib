package io.github.oliviercailloux.git.fs;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Verify.verify;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.AccessMode;
import java.nio.file.ClosedFileSystemException;
import java.nio.file.DirectoryStream;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.WatchService;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
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
import org.eclipse.jgit.treewalk.filter.TreeFilter;
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
import io.github.oliviercailloux.utils.Utils;

public class GitRepoFileSystem extends FileSystem {
	private static class NoSubtreeFilter extends TreeFilter {
		@Override
		public boolean shouldBeRecursive() {
			return false;
		}

		@Override
		public boolean include(TreeWalk walker)
				throws MissingObjectException, IncorrectObjectTypeException, IOException {
			return !walker.isSubtree();
		}

		@Override
		public TreeFilter clone() {
			return this;
		}
	}

	private static class DirectoryChannel implements SeekableByteChannel {
		private boolean open;

		public DirectoryChannel() {
			open = true;
		}

		@Override
		public boolean isOpen() {
			return open;
		}

		@Override
		public void close() throws IOException {
			open = false;
		}

		@Override
		public int read(ByteBuffer dst) throws IOException {
			throw new IOException("is a folder");
		}

		@Override
		public int write(ByteBuffer src) throws IOException {
			throw new IOException("is a folder");
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
		public long size() throws IOException {
			throw new IOException("is a folder");
		}

		@Override
		public SeekableByteChannel truncate(long size) throws IOException {
			throw new IOException("is a folder");
		}

	}

	private static final TreeFilter NO_SUBTREE_FILTER = new NoSubtreeFilter();

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GitRepoFileSystem.class);

	public static GitRepoFileSystem given(GitFileSystemProvider provider, Repository repository) {
		return new GitRepoFileSystem(provider, repository);
	}

	/**
	 * It is crucial to always use the same instance of Jimfs, because Jimfs refuses
	 * to resolve paths coming from different instances.
	 */
	static final FileSystem JIM_FS = Jimfs.newFileSystem(Configuration.unix());
	static final Path JIM_FS_SLASH = JIM_FS.getPath("/");
	static final Path JIM_FS_EMPTY = JIM_FS.getPath("");

	final GitPath masterRoot = new GitPath(this, "master", GitRepoFileSystem.JIM_FS_SLASH);
	final GitPath root = new GitPath(this, "", GitRepoFileSystem.JIM_FS_EMPTY);

	private final GitFileSystemProvider gitProvider;
	private boolean isOpen;
	private Repository repository;
	private ObjectReader reader;

	private GitLocalHistory history;

	protected GitRepoFileSystem(GitFileSystemProvider gitProvider, Repository repository) {
		this.gitProvider = checkNotNull(gitProvider);
		this.repository = checkNotNull(repository);
		reader = repository.newObjectReader();
		reader.setAvoidUnreachableObjects(true);
		isOpen = true;
		history = null;
	}

	@Override
	public GitFileSystemProvider provider() {
		return gitProvider;
	}

	@Override
	public void close() throws IOException {
		isOpen = false;
		reader.close();
		repository.close();
	}

	@Override
	public boolean isOpen() {
		return isOpen;
	}

	@Override
	public boolean isReadOnly() {
		return true;
	}

	@Override
	public String getSeparator() {
		assert JIM_FS.getSeparator().equals("/");
		return "/";
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

		Utils.getOrThrow(() -> getHistory());

		final Comparator<Path> compareWithoutTies = Comparator.comparing((p) -> ((GitPath) p),
				getPathLexicographicTemporalComparator());
		return ImmutableSortedSet.copyOf(compareWithoutTies,
				getCachedHistory().getGraph().nodes().stream().map((c) -> getPath(c.getName() + "/")).iterator());
	}

	/**
	 * @return absolute paths whose rev strings are sha1 object ids, ordered with
	 *         earlier commits coming first.
	 */
	public ImmutableSortedSet<GitPath> getGitRootDirectories() {
		if (!isOpen) {
			throw new ClosedFileSystemException();
		}

		Utils.getOrThrow(() -> getHistory());

		final Comparator<GitPath> compareWithoutTies = getPathLexicographicTemporalComparator();

		/** Important to compare without ties, otherwise some commits get collapsed. */
		return ImmutableSortedSet.copyOf(compareWithoutTies,
				getCachedHistory().getGraph().nodes().stream().map((c) -> getPath(c.getName() + "/")).iterator());
	}

	/**
	 * Paths must have a revStr in the form of a sha1, not a reference such as
	 * "master".
	 */
	private Comparator<GitPath> getPathLexicographicTemporalComparator() {
		final Comparator<ObjectId> compareWithoutTies = getLexicographicTemporalComparator();
		return Comparator.comparing(p -> ObjectId.fromString(p.getRevStr()), compareWithoutTies);
	}

	public GitLocalHistory getHistory() throws IOException {
		if (history == null) {
			history = GitUtils.getHistory(repository);
		}
		return history;
	}

	public GitLocalHistory getCachedHistory() {
		checkState(history != null);
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
	public Iterable<FileStore> getFileStores() {
		throw new UnsupportedOperationException();
	}

	@Override
	public Set<String> supportedFileAttributeViews() {
		throw new UnsupportedOperationException();
	}

	@Override
	public GitPath getPath(String first, String... more) {
		return getPath(first, Arrays.asList(more));
	}

	public GitPath getAbsolutePath(String revStr) {
		return getAbsolutePath(revStr, "/");
	}

	public GitPath getAbsolutePath(String revStr, String dirAndFile) {
		checkArgument(!revStr.isEmpty());
		final Path part = JIM_FS_SLASH.resolve(dirAndFile);
		checkArgument(part.isAbsolute());
		return new GitPath(this, revStr, part);
	}

	public GitPath getRelativePath(String first, String... more) {
		return getPath("", ImmutableList.<String>builder().add(first).addAll(Arrays.asList(more)).build());
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
		 */
		checkArgument(!first.startsWith("/"));
		checkArgument(first.isEmpty() || first.contains("//") || first.endsWith("/"));
		final int startDoubleSlash = first.indexOf("//");
		Verify.verify(startDoubleSlash != 0);
		final String revStr;
		if (startDoubleSlash == -1) {
			revStr = first.isEmpty() ? "" : first.substring(0, first.length() - 1);
		} else {
			revStr = first.substring(0, startDoubleSlash);
		}
		Verify.verify(!revStr.startsWith("/"));
		Verify.verify(!revStr.endsWith("/"));
		final String restFirst;
		if (startDoubleSlash == -1) {
			restFirst = "";
		} else {
			restFirst = first.substring(startDoubleSlash + 1, first.length());
			Verify.verify(restFirst.startsWith("/"));
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

	@Override
	public PathMatcher getPathMatcher(String syntaxAndPattern) {
		throw new UnsupportedOperationException();
	}

	@Override
	public UserPrincipalLookupService getUserPrincipalLookupService() {
		throw new UnsupportedOperationException();
	}

	@Override
	public WatchService newWatchService() throws IOException {
		throw new UnsupportedOperationException();
	}

	Repository getRepository() {
		return repository;
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

	private Optional<ObjectId> getCommitId(String revStr) throws IOException {
		if (isObjectId(revStr)) {
			return Optional.of(ObjectId.fromString(revStr));
		}
		return getCommitIdFromRef(revStr);
	}

	private boolean isObjectId(String revStr) {
		/**
		 * This is incorrect (I suppose): a ref could have the same length as an object
		 * id.
		 */
		return revStr.length() == 40;
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

	private Optional<TreeWalk> getTreeWalk(GitPath gitPath)
			throws IOException, MissingObjectException, IncorrectObjectTypeException, CorruptObjectException {
		final GitPath withoutRoot = gitPath.toRelativePath();
		/**
		 * TreeWalk.forPath says “Empty path not permitted” if using "" or "/".
		 */
		checkArgument(!withoutRoot.equals(root));

		final Optional<RevTree> tree = getCommitTree(gitPath.toAbsolutePath().getRevStr());
		/**
		 * Dangerous zone: treeWalk could end up not closed if an exception is thrown
		 * after creation and before return (as it is not in a try block). Seems
		 * unlikely, though.
		 */
		final Optional<TreeWalk> treeWalk = tree.flatMap(
				Utils.uncheck(t -> Optional.ofNullable(TreeWalk.forPath(repository, withoutRoot.toString(), t))));
		if (treeWalk.isEmpty()) {
			LOGGER.debug("Path " + withoutRoot + " not found.");
		}
		return treeWalk;
	}

	/**
	 * @param gitPath dirAndFile part must be non-empty.
	 */
	private Optional<ObjectId> getObjectId(GitPath gitPath)
			throws IOException, MissingObjectException, IncorrectObjectTypeException, CorruptObjectException {
		checkArgument(!gitPath.toRelativePath().equals(root));

		final Optional<TreeWalk> treeWalkOpt = getTreeWalk(gitPath);
		if (treeWalkOpt.isEmpty()) {
			return Optional.empty();
		}

		final Optional<ObjectId> objectId;
		try (TreeWalk treeWalk = treeWalkOpt.get()) {
			objectId = Optional.of(treeWalk.getObjectId(0));
			verify(!treeWalk.next());
		}
		return objectId;
	}

	private Optional<RevTree> getTree(GitPath dir)
			throws IOException, MissingObjectException, IncorrectObjectTypeException, CorruptObjectException {
		if (dir.toRelativePath().equals(root)) {
			return getCommitTree(dir.toAbsolutePath().getRevStr());
		}

		final Optional<ObjectId> dirId = getObjectId(dir);
		if (dirId.isEmpty()) {
			return Optional.empty();
		}

		final RevTree tree;
		try (RevWalk walk = new RevWalk(reader)) {
			tree = walk.parseTree(dirId.get());
		}
		return Optional.of(tree);
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

	public void checkAccess(GitPath gitPath, AccessMode... modes)
			throws MissingObjectException, IncorrectObjectTypeException, CorruptObjectException, IOException {
		final ImmutableSet<AccessMode> modesList = ImmutableSet.copyOf(modes);
		if (modesList.contains(AccessMode.WRITE)) {
			throw new AccessDeniedException(gitPath.toString());
		}
		if (!Sets.difference(modesList, ImmutableSet.of(AccessMode.READ, AccessMode.EXECUTE)).isEmpty()) {
			throw new UnsupportedOperationException();
		}
		if (gitPath.toRelativePath().equals(root)) {
			final String revStr = gitPath.toAbsolutePath().getRevStr();
			if (isObjectId(revStr)) {
				if (getCommitTree(revStr).isEmpty()) {
					throw new NoSuchFileException(gitPath.toString());
				}
			} else {
				final Optional<ObjectId> commitId = getCommitId(revStr);
				if (commitId.isEmpty()) {
					throw new NoSuchFileException(gitPath.toString());
				}
			}
			return;
		}

		final Optional<FileMode> fileMode;
		final Optional<TreeWalk> treeWalkOpt = getTreeWalk(gitPath);
		if (treeWalkOpt.isEmpty()) {
			fileMode = Optional.empty();
		} else {
			try (TreeWalk treeWalk = treeWalkOpt.get()) {
				fileMode = Optional.of(treeWalk.getFileMode());
				verify(!treeWalk.next());
			}
		}

		if (fileMode.isEmpty()) {
			throw new NoSuchFileException(gitPath.toString());
		}

		if (modesList.contains(AccessMode.EXECUTE)) {
			if (!fileMode.get().equals(FileMode.EXECUTABLE_FILE)) {
				throw new AccessDeniedException(gitPath.toString());
			}
		}
	}

	public SeekableByteChannel newByteChannel(GitPath gitPath) throws IOException {
		if (!isOpen) {
			throw new ClosedFileSystemException();
		}
		if (gitPath.toRelativePath().equals(root)) {
			return new DirectoryChannel();
		}

		final ObjectId fileId = getObjectId(gitPath).orElseThrow(() -> new NoSuchFileException(gitPath.toString()));

		final ObjectLoader fileLoader = reader.open(fileId, Constants.OBJ_BLOB);

		if (fileLoader.getType() == Constants.OBJ_TREE) {
			return new DirectoryChannel();
		}
		if (fileLoader.getType() != Constants.OBJ_BLOB) {
			throw new IOException("Unexpected file type: " + fileLoader.getType());
		}
		final byte[] bytes = fileLoader.getBytes();
		LOGGER.debug("Read: {}.", new String(bytes, StandardCharsets.UTF_8));
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
			treeWalk.setFilter(NO_SUBTREE_FILTER);
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

}
