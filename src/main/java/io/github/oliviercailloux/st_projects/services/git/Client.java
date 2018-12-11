package io.github.oliviercailloux.st_projects.services.git;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;

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
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinates;

public class Client {
	public static Client aboutAndUsing(RepositoryCoordinates coordinates, Path path) {
		return new Client(coordinates, path);
	}

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(Client.class);

	private Path outputBaseDir;

	private RepositoryCoordinates coordinates;

	private GitHistory history;

	private Boolean exists, hasContent;

	private Client(RepositoryCoordinates coordinates, Path outputBaseDir) {
		this.coordinates = requireNonNull(coordinates);
		this.outputBaseDir = requireNonNull(outputBaseDir);
		history = null;
		exists = null;
		hasContent = null;
	}

	public void checkout(String name) throws IOException, GitAPIException {
		try (Git git = open()) {
			git.checkout().setName(name).call();
		}
	}

	private Repository openRepository() throws IOException {
		return new FileRepository(getProjectDirectory().resolve(".git").toFile());
	}

	public boolean cloneRepositoryIfExists() throws GitAPIException {
		try {
			cloneRepository();
		} catch (InvalidRemoteException e) {
			LOGGER.info("Cloning.", e);
			return false;
		}
		return true;
	}

	public boolean hasContent() throws IOException, GitAPIException {
		checkState(exists != null);
		if (!exists) {
			return false;
		}
		try (Git git = open()) {
			hasContent = git.getRepository().getObjectDatabase().exists() && listBranches().size() >= 1;
			return hasContent;
		}
	}

	public boolean hasCachedContent() {
		return hasContent;
	}

	public boolean retrieve() throws GitAPIException, IOException, IllegalStateException, CheckoutConflictException {
		final String name = coordinates.getRepositoryName();
		final Path outputProjectDir = outputBaseDir.resolve(name);
		if (Files.exists(outputProjectDir)) {
			update();
			exists = true;
		} else {
			exists = cloneRepositoryIfExists();
		}
		return exists;
	}

	public void update() throws GitAPIException, IOException, IllegalStateException, CheckoutConflictException {
		try (Git git = open()) {
			LOGGER.info("Updating {}.", coordinates);
			git.fetch().call();

//			final Ref masterRef = repo.getRepository().exactRef("refs/heads/master");
//			assert masterRef != null : repo.branchList().call();
//			final RevCommit masterCommit = repo.getRepository().parseCommit(masterRef.getObjectId());
//			assert masterCommit != null : masterRef.getObjectId();
//			final CheckoutCommand checkoutCmd = repo.checkout();
//			checkoutCmd.setStartPoint(masterCommit).call();
			if (listBranches().isEmpty()) {
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

	public ImmutableList<Ref> listBranches() throws IOException, GitAPIException {
		try (Git git = open()) {
			final List<Ref> br = git.branchList().setListMode(ListMode.ALL).call();
			return ImmutableList.copyOf(br);
		}
	}

	public void checkout(RevCommit commit) throws IOException, GitAPIException {
		try (Git git = open()) {
			git.checkout().setName(commit.name()).call();
		}
	}

	private void read() throws IOException {
//		try (new FileRepository repository = CookbookHelper.openJGitCookbookRepository()) {
		try (Git git = open()) {
			final Repository repository = git.getRepository();
			// the Ref holds an ObjectId for any type of object (tree, commit, blob, tree)
			Ref head = repository.exactRef("refs/heads/master");
			System.out.println("Ref of refs/heads/master: " + head);

			System.out.println("\nPrint contents of head of master branch, i.e. the latest commit information");
			ObjectLoader loader = repository.open(head.getObjectId());
			loader.copyTo(System.out);

			System.out.println(
					"\nPrint contents of tree of head of master branch, i.e. the latest binary tree information");

			// a commit points to a tree
			try (RevWalk walk = new RevWalk(repository)) {
				RevCommit commit = walk.parseCommit(head.getObjectId());
				RevTree tree = walk.parseTree(commit.getTree().getId());
				System.out.println("Found Tree: " + tree);
				loader = repository.open(tree.getId());
				loader.copyTo(System.out);

				walk.dispose();
				CanonicalTreeParser treeParser = new CanonicalTreeParser();
				try (ObjectReader reader = repository.newObjectReader()) {
					treeParser.reset(reader, tree.getId());
				}

			}
		}
	}

	public String fetchBlob(String revSpec, String path)
			throws MissingObjectException, IncorrectObjectTypeException, IOException {
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
			return read;
		}
	}

	private Optional<AnyObjectId> getBlobId(Repository repository, String revSpec, String path)
			throws AmbiguousObjectException, IncorrectObjectTypeException, IOException, MissingObjectException,
			CorruptObjectException {
		final Optional<AnyObjectId> foundId;
		final RevTree tree;
		{
			final RevCommit commit;
			{
				final ObjectId id1 = repository.resolve(revSpec);
				try (RevWalk walk = new RevWalk(repository)) {
					commit = walk.parseCommit(id1);
				}
			}
			tree = commit.getTree();
		}

		try (TreeWalk treewalk = TreeWalk.forPath(repository, path, tree)) {
			if (treewalk != null) {
				foundId = Optional.of(treewalk.getObjectId(0));
			} else {
				foundId = Optional.empty();
			}
		}
		return foundId;
	}

	public Optional<AnyObjectId> getBlobId(String revSpec, String path) throws AmbiguousObjectException,
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
	}

	public GitHistory listCommits(boolean all) throws IOException, GitAPIException {
		try (Git git = open()) {
			LOGGER.info("Work dir: {}.", git.getRepository().getWorkTree());
			final LogCommand log = git.log();
			if (all) {
				log.all();
			}
			final Iterable<RevCommit> commits = log.call();
			history = GitHistory.from(commits);
			return history;
		}
	}

	public GitHistory getHistory() {
		checkState(history != null);
		return history;
	}

	public static Client about(RepositoryCoordinates coordinates) {
		final String tmpDir = System.getProperty("java.io.tmpdir");
		return new Client(coordinates, Paths.get(tmpDir));
	}

	public boolean exists() {
		checkState(exists != null);
		return exists;
	}

	public RepositoryCoordinates getCoordinates() {
		return coordinates;
	}

	private Git open() throws IOException {
		return Git.open(getProjectDirectory().toFile());
	}

	public Instant getCreationTime(RevCommit commit) {
		PersonIdent authorIdent = commit.getAuthorIdent();
		Date authorDate = authorIdent.getWhen();
//		TimeZone authorTimeZone = authorIdent.getTimeZone();
		return authorDate.toInstant();
	}
}
