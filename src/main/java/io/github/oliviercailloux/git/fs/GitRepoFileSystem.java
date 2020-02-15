package io.github.oliviercailloux.git.fs;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Verify.verify;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.ClosedFileSystemException;
import java.nio.file.DirectoryStream;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.WatchService;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Set;

import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.Constants;
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
import com.google.common.graph.ImmutableGraph;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;

import io.github.oliviercailloux.git.GitLocalHistory;
import io.github.oliviercailloux.git.GitUtils;
import io.github.oliviercailloux.utils.SeekableInMemoryByteChannel;

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

	private static final TreeFilter NO_SUBTREE_FILTER = new NoSubtreeFilter();

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GitFileSystem.class);

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

	@Override
	public ImmutableSortedSet<Path> getRootDirectories() {
		if (!isOpen) {
			throw new ClosedFileSystemException();
		}

		try {
			getHistory();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}

		final Comparator<Path> compareWithoutTies = Comparator.comparing((p) -> ((GitPath) p),
				getLecixographicTemporalComparator());
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

		try {
			getHistory();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}

		final Comparator<GitPath> compareWithoutTies = getLecixographicTemporalComparator();

		/** Important to compare without ties, otherwise some commits get collapsed. */
		return ImmutableSortedSet.copyOf(compareWithoutTies,
				getCachedHistory().getGraph().nodes().stream().map((c) -> getPath(c.getName() + "/")).iterator());
	}

	private Comparator<GitPath> getLecixographicTemporalComparator() {
		final Comparator<GitPath> comparingDates = Comparator
				.comparing((p) -> getCachedHistory().getCommitDate(getCommit(p)));
		final Comparator<GitPath> compareWithoutTies = comparingDates.thenComparing((p1, p2) -> {
			final RevCommit c1 = getCommit(p1);
			final RevCommit c2 = getCommit(p2);
			final ImmutableGraph<RevCommit> graph = getCachedHistory().getTransitivelyClosedGraph();
			if (graph.hasEdgeConnecting(c2, c1)) {
				/** c1 < c2 */
				return -1;
			}
			if (graph.hasEdgeConnecting(c1, c2)) {
				/** c1 child-of c2 thus c1 comes after (is greater than) c2 temporally. */
				return 1;
			}
			return 0;
		}).thenComparing(Comparator.comparing(GitPath::toString));
		return compareWithoutTies;
	}

	/**
	 * history must have been loaded (might be more efficient to use
	 * {@link #getObjectId(GitPath)} if history is not loaded).
	 *
	 * @param path must have a revStr in the form of a sha1, not a reference such as
	 *             "master".
	 */
	private RevCommit getCommit(GitPath path) {
		final ObjectId objectId = ObjectId.fromString(path.getRevStr());
		return getCachedHistory().getCommit(objectId);
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

		final ImmutableList<String> dirAndFile = ImmutableList.<String>builder().add(restFirst)
				.addAll(Arrays.asList(more)).build().stream().filter((s) -> !s.isEmpty())
				.collect(ImmutableList.toImmutableList());

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

	public GitPath getRelativePath(String first, String... more) {
		return getPath("", ImmutableList.<String>builder().add(first).addAll(Arrays.asList(more)).build()
				.toArray(new String[] {}));
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

	private RevTree getTree(String revStr)
			throws IOException, FileNotFoundException, MissingObjectException, IncorrectObjectTypeException {
		final Ref ref = repository.findRef(revStr);
		if (ref == null) {
			throw new FileNotFoundException("Rev str " + revStr + " not found.");
		}

		final ObjectId commitId = ref.getLeaf().getObjectId();
		if (commitId == null) {
			throw new FileNotFoundException("Ref " + ref.getName() + " does not exist.");
		}

		final RevTree tree;
		try (RevWalk walk = new RevWalk(reader)) {
			tree = walk.parseCommit(commitId).getTree();
		}
		return tree;
	}

	private RevTree getTree(GitPath dir) throws IOException, FileNotFoundException, MissingObjectException,
			IncorrectObjectTypeException, CorruptObjectException {
		if (dir.getWithoutRoot().toString().isEmpty()) {
			return getTree(dir.toAbsolutePath().getRevStr());
		}

		final ObjectId dirId = getObjectId(dir);
		final RevTree tree;
		try (RevWalk walk = new RevWalk(reader)) {
			tree = walk.parseTree(dirId);
		}
		return tree;
	}

	private ObjectId getObjectId(GitPath gitPath) throws IOException, FileNotFoundException, MissingObjectException,
			IncorrectObjectTypeException, CorruptObjectException {
		checkArgument(!gitPath.getWithoutRoot().toString().isEmpty());

		final String revStr = gitPath.toAbsolutePath().getRevStr();
		final RevTree tree = getTree(revStr);

		final ObjectId objectId;
		final GitPath withoutRoot = gitPath.getWithoutRoot();
		try (TreeWalk treeWalk = TreeWalk.forPath(repository, withoutRoot.toString(), tree)) {
			if (treeWalk == null) {
				throw new FileNotFoundException("Path " + withoutRoot + " not found.");
			}
			objectId = treeWalk.getObjectId(0);
			verify(!treeWalk.next());
		}
		return objectId;
	}

	public SeekableByteChannel newByteChannel(GitPath gitPath) throws IOException {
		if (!isOpen) {
			throw new ClosedFileSystemException();
		}

		final ObjectId fileId = getObjectId(gitPath);

		final ObjectLoader fileLoader = reader.open(fileId, Constants.OBJ_BLOB);
		final byte[] bytes = fileLoader.getBytes();
		LOGGER.info("Read: {}.", new String(bytes, StandardCharsets.UTF_8));
		return new SeekableInMemoryByteChannel(bytes);
	}

	public DirectoryStream<GitPath> newDirectoryStream(GitPath dir, Filter<? super Path> filter) throws IOException {
		if (!isOpen) {
			throw new ClosedFileSystemException();
		}

		final RevTree tree = getTree(dir);

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
