package io.github.oliviercailloux.git;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand.ListMode;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.MergeResult.MergeStatus;
import org.eclipse.jgit.api.errors.CheckoutConflictException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.errors.AmbiguousObjectException;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Table;
import com.google.common.collect.TreeBasedTable;

import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinates;
import io.github.oliviercailloux.utils.Utils;

/**
 * This could be simplified by splitting in a row client with basic operations
 * and a higher level one with caching and predicate search
 * (getContents<Predicate>). Note that predicate search must use caching.
 *
 * Does not depend on the content on the work dir. Only uses the content of the
 * .git directory on the disk.
 *
 * @author Olivier Cailloux
 *
 */
public class ComplexClient {
	public static ComplexClient aboutAndUsing(RepositoryCoordinates coordinates, Path path) {
		return new ComplexClient(coordinates, path);
	}

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(ComplexClient.class);

	private final Path outputBaseDir;

	private final RepositoryCoordinates coordinates;

	private GitLocalHistory allHistory;

	/**
	 * The repository can be cloned or fetched.
	 */
	private Boolean exists;
	/**
	 * The repository database is not empty and the repository contains at least one
	 * branch. exists == null ⇒ hasContent == null. exists == false ⇒ hasContent ==
	 * false.
	 */
	private Boolean hasContent;

	private final Table<ObjectId, Path, String> blobCache;

	ComplexClient(RepositoryCoordinates coordinates, Path outputBaseDir) {
		this.coordinates = requireNonNull(coordinates);
		this.outputBaseDir = requireNonNull(outputBaseDir);
		allHistory = null;
		exists = null;
		hasContent = null;
		blobCache = TreeBasedTable.create();
	}

	private Repository openRepository() throws IOException {
		return new FileRepository(getProjectDirectory().resolve(".git").toFile());
	}

	public boolean tryClone() throws GitAPIException {
		try {
			cloneRepository();
			assert exists == true;
		} catch (InvalidRemoteException e) {
			LOGGER.info("Cloning.", e);
			exists = false;
			hasContent = false;
			allHistory = null;
		}
		return exists;
	}

	public boolean hasContent() throws IOException, GitAPIException {
		checkState(exists != null);
		assert Utils.implies(exists == null, hasContent == null);
		assert Utils.implies(Boolean.FALSE.equals(exists), Boolean.FALSE.equals(hasContent));
		if (hasContent != null) {
			return hasContent;
		}

		try (Git git = open()) {
			hasContent = git.getRepository().getObjectDatabase().exists() && getAllBranches().size() >= 1;
			return hasContent;
		}
	}

	public boolean hasContentCached() {
		checkState(hasContent != null);
		return hasContent;
	}

	public boolean tryRetrieve() throws GitAPIException, IOException, IllegalStateException, CheckoutConflictException {
		final String name = coordinates.getRepositoryName();
		final Path outputProjectDir = outputBaseDir.resolve(name);
		LOGGER.debug("Using output dir: {}.", outputProjectDir);
		if (Files.exists(outputProjectDir)) {
			update();
		} else {
			tryClone();
		}
		assert exists != null;
		return exists;
	}

	public void update() throws GitAPIException, IOException, IllegalStateException, CheckoutConflictException {
		try (Git git = open()) {
			LOGGER.info("Updating {}.", coordinates);
			git.fetch().call();
			exists = true;
			allHistory = null;
			if (hasContent != null && !hasContent) {
				/** Previously cached information about no content may now be invalid. */
				hasContent = null;
			}
//			final Ref masterRef = repo.getRepository().exactRef("refs/heads/master");
//			assert masterRef != null : repo.branchList().call();
//			final RevCommit masterCommit = repo.getRepository().parseCommit(masterRef.getObjectId());
//			assert masterCommit != null : masterRef.getObjectId();
//			final CheckoutCommand checkoutCmd = repo.checkout();
//			checkoutCmd.setStartPoint(masterCommit).call();
			if (getAllBranches().isEmpty()) {
				return;
			}
			git.checkout().setName("refs/remotes/origin/master").call();

			final Ref originMasterRef = git.getRepository().exactRef("refs/remotes/origin/master");
			assert originMasterRef != null : git.branchList().setListMode(ListMode.REMOTE).call();
			final MergeResult res = git.merge().include(originMasterRef).call();
			final boolean rightState = res.getMergeStatus() == MergeStatus.ALREADY_UP_TO_DATE
					|| res.getMergeStatus() == MergeStatus.MERGED || res.getMergeStatus() == MergeStatus.FAST_FORWARD;
			if (!rightState) {
				throw new IllegalStateException("Illegal merge result: " + res.getMergeStatus());
			}
		}
	}

	public Path getProjectDirectory() {
		final String name = coordinates.getRepositoryName();
		return outputBaseDir.resolve(name);
	}

	public ImmutableList<Ref> getAllBranches() throws IOException, GitAPIException {
		try (Git git = open()) {
			final List<Ref> br = git.branchList().setListMode(ListMode.ALL).call();
			return ImmutableList.copyOf(br);
		}
	}

	public String fetchBlob(AnyObjectId revSpec, Path path)
			throws MissingObjectException, IncorrectObjectTypeException, IOException {
		checkArgument(!path.toString().equals(""));
		if (blobCache.contains(revSpec, path)) {
			return blobCache.get(revSpec, path);
		}

		LOGGER.debug("Blob cache miss, fetching {}.", path);
		try (Repository repository = openRepository()) {
			final Optional<AnyObjectId> foundId = getBlobId(repository, revSpec, path);

			final String read;
			if (foundId.isPresent()) {
				final AnyObjectId id = foundId.get();
				read = read(repository, id);
			} else {
				read = "";
			}
//			LOGGER.info("Found id: {}, read: {}.", foundId, read);
			blobCache.put(revSpec.copy(), path, read);
			return read;
		}
	}

	private Optional<AnyObjectId> getBlobId(Repository repository, AnyObjectId revSpec, Path path)
			throws AmbiguousObjectException, IncorrectObjectTypeException, IOException, MissingObjectException,
			CorruptObjectException {
		checkArgument(!path.toString().equals(""));
		final Optional<AnyObjectId> foundId;
		final RevTree tree;
		{
			final RevCommit commit = getCommit(repository, revSpec);
			tree = commit.getTree();
		}

		final Path rel = path.isAbsolute() ? getProjectDirectory().relativize(path) : path;
		LOGGER.debug("Trying to relativize {} against {}: {}.", getProjectDirectory(), path, rel);
		try (TreeWalk treewalk = TreeWalk.forPath(repository, rel.toString(), tree)) {
			if (treewalk != null) {
				foundId = Optional.of(treewalk.getObjectId(0));
			} else {
				foundId = Optional.empty();
			}
		}
		return foundId;
	}

	private RevCommit getCommit(Repository repository, AnyObjectId revSpec)
			throws MissingObjectException, IncorrectObjectTypeException, IOException {
		final RevCommit commit;
		try (RevWalk walk = new RevWalk(repository)) {
			commit = walk.parseCommit(revSpec);
		}
		return commit;
	}

	public Optional<ObjectId> tryResolve(String revSpec) throws IOException {
		try (Repository repository = openRepository()) {
			final ObjectId resolved = repository.resolve(revSpec);
			return Optional.ofNullable(resolved);
		}
	}

	public Optional<AnyObjectId> getBlobId(AnyObjectId revSpec, Path path) throws AmbiguousObjectException,
			IncorrectObjectTypeException, IOException, MissingObjectException, CorruptObjectException {
		try (Repository repository = openRepository()) {
			return getBlobId(repository, revSpec, path);
		}
	}

	String cachedOrRead(Repository repository, AnyObjectId revSpec, Path path, AnyObjectId blobId)
			throws MissingObjectException, IOException {
		if (blobCache.contains(revSpec, path)) {
			return blobCache.get(revSpec, path);
		}
		final String read = read(repository, blobId);
		blobCache.put(revSpec.copy(), path, read);
		return read;
	}

	public ImmutableList<Path> getContents(AnyObjectId revSpec, Path path)
			throws MissingObjectException, IncorrectObjectTypeException, CorruptObjectException, IOException {
		try (Repository repository = openRepository()) {
			final RevTree tree;
			{
				final RevCommit commit = getCommit(repository, revSpec);
				tree = commit.getTree();
			}

			final Path rel = path.isAbsolute() ? getProjectDirectory().relativize(path) : path;
			LOGGER.debug("Trying to relativize {} against {}: {}.", getProjectDirectory(), path, rel);
//			final Optional<AnyObjectId> foundId;
//			try (TreeWalk treewalk = TreeWalk.forPath(repository, rel.toString(), tree)) {
//				if (treewalk != null) {
//					foundId = Optional.of(treewalk.getObjectId(0));
//				} else {
//					foundId = Optional.empty();
//				}
//			}

//			final String read;
//			if (foundId.isPresent()) {
//				final AnyObjectId id = foundId.get();
//				read = read(repository, id);
//			} else {
//				read = "";
//			}
//			blobCache.put(revSpec.copy(), path, read);
//			return read;

			final ImmutableList.Builder<Path> builder = ImmutableList.builder();
			try (TreeWalk treeWalk = new TreeWalk(repository)) {
//			try (TreeWalk treeWalk = TreeWalk.forPath(repository, rel.toString(), tree)) {
				treeWalk.addTree(tree);
				LOGGER.info("Filtering for {}.", path);
				/** Doesn’t work: accepts parents and children of the given path. */
				treeWalk.setFilter(PathFilter.create(path.toString()));
				treeWalk.setRecursive(false);
				do {
					final boolean foundPath = treeWalk.next();
					checkArgument(foundPath);
					LOGGER.info("Now in {}.", treeWalk.getPathString());
					treeWalk.enterSubtree();
				} while (!treeWalk.getPathString().equals(path.toString()));
				while (treeWalk.next()) {
					if (treeWalk.isSubtree()) {
						builder.add(Paths.get(treeWalk.getPathString()));
//						treeWalk.enterSubtree();
//						System.out.println("dir: " + treeWalk.getPathString());
//					} else {
//						System.out.println("file: " + treeWalk.getPathString());
					}
				}
			}
			return builder.build();
		}
	}

	public ImmutableSet<Path> getPaths(AnyObjectId revSpec, Predicate<FileContent> predicate)
			throws MissingObjectException, IncorrectObjectTypeException, CorruptObjectException, IOException {
		return getFileContents(revSpec, predicate).stream().map(FileContent::getPath)
				.collect(ImmutableSet.toImmutableSet());
	}

	public ImmutableSet<FileContent> getFileContents(AnyObjectId revSpec, Predicate<FileContent> predicate)
			throws MissingObjectException, IncorrectObjectTypeException, IOException, CorruptObjectException {
		final ImmutableSet<FileContent> built;
		try (Repository repository = openRepository()) {
			final RevTree tree;
			{
				final RevCommit commit = getCommit(repository, revSpec);
				tree = commit.getTree();
			}

			final ImmutableSet.Builder<FileContent> builder = ImmutableSet.builder();
			try (TreeWalk treeWalk = new TreeWalk(repository)) {
				treeWalk.addTree(tree);
				treeWalk.setRecursive(true);
				while (treeWalk.next()) {
					final Path path = Paths.get(treeWalk.getPathString());
					final Supplier<String> contentSupplier = () -> {
						try {
							/**
							 * To get better perf, we might use this access, but it works only before
							 * closing the repository thus can’t be returned to the caller.
							 */
//							final ObjectId objectId = treeWalk.getObjectId(0);
//							return cachedOrRead(repository, revSpec, path, objectId);
							return fetchBlob(revSpec, path);
						} catch (IOException e) {
							throw new IllegalStateException(e);
						}
					};
					final FileContent fc = new FileContentImpl(path, contentSupplier);
					final boolean test = predicate.test(fc);
					if (test) {
						builder.add(fc);
					}
					LOGGER.debug("Now in {}.", path);
				}
			}
			built = builder.build();
		}
		return built;
	}

	public void cloneRepository() throws GitAPIException {
		final CloneCommand cloneCmd = Git.cloneRepository();
		final String uri = coordinates.getSshURLString();
		cloneCmd.setURI(uri);
		final File dest = getProjectDirectory().toFile();
		cloneCmd.setDirectory(dest);
		LOGGER.info("Cloning {} to {}.", uri, dest);
		cloneCmd.call().close();
		exists = true;
		allHistory = null;
		if (hasContent != null && !hasContent) {
			/** Previously cached information about no content may now be invalid. */
			hasContent = null;
		}
	}

	private GitLocalHistory getHistory() throws IOException, GitAPIException {
		final File gitDir = getProjectDirectory().toFile();
		checkState(exists != null);
		if (allHistory != null) {
			return allHistory;
		}
		if (!exists) {
			return GitLocalHistory.from(ImmutableSet.of());
		}

		final GitLocalHistory history = GitUtils.getHistory(gitDir);
		allHistory = history;
		return history;
	}

	/**
	 * The existence of the repository must have been determined already.
	 *
	 * @return an empty history if the repository does not exist.
	 * @throws IOException
	 * @throws GitAPIException
	 */
	public GitLocalHistory getWholeHistory() throws IOException, GitAPIException {
		return getHistory();
	}

	public GitLocalHistory getAllHistoryCached() {
		checkState(allHistory != null);
		return allHistory;
	}

	public static ComplexClient aboutAndUsingTmp(RepositoryCoordinates coordinates) {
		final String tmpDir = System.getProperty("java.io.tmpdir");
		return new ComplexClient(coordinates, Paths.get(tmpDir));
	}

	public boolean existsCached() {
		checkState(exists != null);
		return exists;
	}

	public boolean testedExistence() {
		return exists != null;
	}

	public RepositoryCoordinates getCoordinates() {
		return coordinates;
	}

	Git open() throws IOException {
		return Git.open(getProjectDirectory().toFile());
	}

	public ObjectId resolve(String revSpec) throws IOException {
		try (Repository repository = openRepository()) {
			final ObjectId resolved = repository.resolve(revSpec);
			checkArgument(resolved != null);
			return resolved;
		}
	}

	public Set<RevCommit> getAllCommits() throws IOException, GitAPIException {
		return getWholeHistory().getGraph().nodes();
	}

	public RevCommit getCommit(AnyObjectId revSpec) throws IOException {
		try (Repository repository = openRepository()) {
			return getCommit(repository, revSpec);
		}
	}

	private String read(Repository repository, AnyObjectId id) throws MissingObjectException, IOException {
		final String read;
		try (ObjectReader reader = repository.newObjectReader()) {
			byte[] data = reader.open(id).getBytes();
			read = new String(data, StandardCharsets.UTF_8);
		}
		return read;
	}

	public ImmutableMap<Path, String> getContents(AnyObjectId revSpec, Predicate<FileContent> predicate)
			throws MissingObjectException, IncorrectObjectTypeException, CorruptObjectException, IOException {
		/**
		 * Code is largely redundant with other filtering behavior of this class, but
		 * not factored yet.
		 */
		try (Repository repository = openRepository()) {
			final RevTree tree;
			{
				final RevCommit commit = getCommit(repository, revSpec);
				tree = commit.getTree();
			}

			final ImmutableMap.Builder<Path, String> builder = ImmutableMap.builder();
			try (TreeWalk treeWalk = new TreeWalk(repository)) {
				treeWalk.addTree(tree);
				treeWalk.setRecursive(true);
				while (treeWalk.next()) {
					final Path path = Paths.get(treeWalk.getPathString());
					final ObjectId objectId = treeWalk.getObjectId(0);
					final Supplier<String> contentSupplier = () -> {
						try {
							return cachedOrRead(repository, revSpec, path, objectId);
						} catch (IOException e) {
							throw new IllegalStateException(e);
						}
					};
					final FileContent fc = new FileContentImpl(path, contentSupplier);
					final boolean test = predicate.test(fc);
					if (test) {
						builder.put(path, fc.getContent());
					}
					LOGGER.debug("Now in {}.", path);
				}
			}
			return builder.build();
		}
	}

	public static ComplexClient about(RepositoryCoordinates coordinates) {
		final String tmpDir = System.getProperty("java.io.tmpdir");
		return new ComplexClient(coordinates, Paths.get(tmpDir).resolve(Instant.now().toString()));
	}
}
