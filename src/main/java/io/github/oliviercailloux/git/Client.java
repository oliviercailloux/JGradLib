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
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TimeZone;

import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand.ListMode;
import org.eclipse.jgit.api.LogCommand;
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
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Table;
import com.google.common.collect.TreeBasedTable;

import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinates;
import io.github.oliviercailloux.git.utils.Utils;

public class Client {
	public static Client aboutAndUsing(RepositoryCoordinates coordinates, Path path) {
		return new Client(coordinates, path);
	}

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(Client.class);

	private final Path outputBaseDir;

	private final RepositoryCoordinates coordinates;

	private GitHistory allHistory;

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

	private ObjectId defaultRevSpec;

	private Client(RepositoryCoordinates coordinates, Path outputBaseDir) {
		this.coordinates = requireNonNull(coordinates);
		this.outputBaseDir = requireNonNull(outputBaseDir);
		allHistory = null;
		exists = null;
		hasContent = null;
		blobCache = TreeBasedTable.create();
		defaultRevSpec = null;
	}

	@Deprecated
	public void checkout(String name) throws IOException, GitAPIException {
		try (Git git = open()) {
			git.checkout().setName(name).call();
		}
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

	public void checkout(AnyObjectId commitId) throws IOException, GitAPIException {
		try (Git git = open()) {
			git.checkout().setName(commitId.getName()).call();
		}
	}

	public String fetchBlob(AnyObjectId revSpec, Path path)
			throws MissingObjectException, IncorrectObjectTypeException, IOException {
		if (blobCache.contains(revSpec, path)) {
			return blobCache.get(revSpec, path);
		}

		final Optional<AnyObjectId> foundId;
		try (Repository repository = openRepository()) {
			foundId = getBlobId(repository, revSpec, path);

			final String read;
			if (foundId.isPresent()) {
				final AnyObjectId id = foundId.get();
				read = read(repository, id);
			} else {
				read = "";
			}
			blobCache.put(revSpec.copy(), path, read);
			return read;
		}
	}

	public String fetchBlobOrEmpty(Path path) throws MissingObjectException, IncorrectObjectTypeException, IOException {
		if (defaultRevSpec == null) {
			return "";
		}
		return fetchBlob(defaultRevSpec, path);
	}

	public void setDefaultRevSpec(AnyObjectId revSpec) {
		defaultRevSpec = revSpec.copy();
	}

	private Optional<AnyObjectId> getBlobId(Repository repository, AnyObjectId revSpec, Path path)
			throws AmbiguousObjectException, IncorrectObjectTypeException, IOException, MissingObjectException,
			CorruptObjectException {
		final Optional<AnyObjectId> foundId;
		final RevTree tree;
		{
			final RevCommit commit = getCommit(repository, revSpec);
			tree = commit.getTree();
		}

		try (TreeWalk treewalk = TreeWalk.forPath(repository, path.toString(), tree)) {
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

	private String read(Repository repository, AnyObjectId id) throws MissingObjectException, IOException {
		final String read;
		try (ObjectReader reader = repository.newObjectReader()) {
			byte[] data = reader.open(id).getBytes();
			read = new String(data, StandardCharsets.UTF_8);
		}
		return read;
	}

	public void cloneRepository() throws GitAPIException {
		final CloneCommand cloneCmd = Git.cloneRepository();
		final String uri = coordinates.getSshURLString();
		cloneCmd.setURI(uri);
		final File dest = getProjectDirectory().toFile();
		cloneCmd.setDirectory(dest);
		LOGGER.info("Cloning from {} to {}.", uri, dest);
		cloneCmd.call().close();
		exists = true;
	}

	@Deprecated
	public GitHistory getHistory(boolean all) throws IOException, GitAPIException {
		/** Should become private. */
		if (all && allHistory != null) {
			return allHistory;
		}

		final GitHistory history;
		try (Git git = open()) {
			LOGGER.info("Work dir: {}.", git.getRepository().getWorkTree());
			final LogCommand log = git.log();
			if (all) {
				log.all();
			}
			final Iterable<RevCommit> commits = log.call();
			history = GitHistory.from(commits);
		}
		if (all) {
			allHistory = history;
		}
		return history;
	}

	public GitHistory getWholeHistory() throws IOException, GitAPIException {
		return getHistory(true);
	}

	public GitHistory getAllHistoryCached() {
		checkState(allHistory != null);
		return allHistory;
	}

	public static Client about(RepositoryCoordinates coordinates) {
		final String tmpDir = System.getProperty("java.io.tmpdir");
		return new Client(coordinates, Paths.get(tmpDir));
	}

	public boolean existsCached() {
		checkState(exists != null);
		return exists;
	}

	public RepositoryCoordinates getCoordinates() {
		return coordinates;
	}

	private Git open() throws IOException {
		return Git.open(getProjectDirectory().toFile());
	}

	public ZonedDateTime getCreationTime(RevCommit commit) {
		final ZonedDateTime authorCreationTime = getCreationTime(commit.getAuthorIdent());
		final ZonedDateTime committerCreationTime = getCreationTime(commit.getCommitterIdent());
		checkArgument(authorCreationTime.equals(committerCreationTime));
		return authorCreationTime;
	}

	public ZonedDateTime getCreationTime(PersonIdent ident) {
		Date creationInstant = ident.getWhen();
		TimeZone creationZone = ident.getTimeZone();
		final ZonedDateTime creationTime = ZonedDateTime.ofInstant(creationInstant.toInstant(),
				creationZone.toZoneId());
		return creationTime;
	}

	public ObjectId resolve(String revSpec) throws IOException {
		try (Repository repository = openRepository()) {
			final ObjectId resolved = repository.resolve(revSpec);
			checkArgument(resolved != null);
			return resolved;
		}
	}

	public Instant getCreationTimeSimple(RevCommit commit) {
		PersonIdent ident = commit.getAuthorIdent();
		final ZonedDateTime creationTime = getCreationTime(ident);
		return creationTime.toInstant();
	}

	public Set<RevCommit> getAllCommits() throws IOException, GitAPIException {
		return getWholeHistory().getGraph().nodes();
	}

	public RevCommit getCommit(AnyObjectId revSpec) throws IOException {
		try (Repository repository = openRepository()) {
			return getCommit(repository, revSpec);
		}
	}

	public Optional<ObjectId> getDefaultRevSpec() {
		return Optional.ofNullable(defaultRevSpec);
	}
}
