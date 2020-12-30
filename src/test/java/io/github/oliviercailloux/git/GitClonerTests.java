package io.github.oliviercailloux.git;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.internal.storage.dfs.DfsRepository;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Verify;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.MoreCollectors;
import com.google.common.graph.Traverser;

import io.github.oliviercailloux.git.fs.GitFileSystem;
import io.github.oliviercailloux.git.fs.GitFileSystemProvider;
import io.github.oliviercailloux.git.git_hub.model.GitHubToken;
import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinates;
import io.github.oliviercailloux.git.git_hub.model.v3.CommitGitHubDescription;
import io.github.oliviercailloux.git.git_hub.services.GitHubFetcherV3;
import io.github.oliviercailloux.utils.Utils;

class GitClonerTests {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GitClonerTests.class);

	@Test
	void testUpdate() throws Exception {
		final ImmutableList<ObjectId> shas;
		try (GitHubFetcherV3 fetcher = GitHubFetcherV3.using(GitHubToken.getRealInstance())) {
			final List<CommitGitHubDescription> commits = fetcher
					.getCommitsGitHubDescriptions(RepositoryCoordinates.from("oliviercailloux", "testrel"), false);
			shas = commits.stream().map(CommitGitHubDescription::getSha).collect(ImmutableList.toImmutableList());
		}

		final GitCloner cloner = new GitCloner();

		final Path httpsPath = Utils.getTempDirectory()
				.resolve("testrel cloned using https " + Utils.ISO_BASIC_UTC_FORMATTER.format(Instant.now()));
		cloner.download(GitUri.fromUri(URI.create("https://github.com/oliviercailloux/testrel/")), httpsPath);

		final Path sshPath = Utils.getTempDirectory()
				.resolve("testrel cloned using ssh " + Utils.ISO_BASIC_UTC_FORMATTER.format(Instant.now()));

		final GitHistory historyFromHttpsClone;
		final ObjectId masterId;
		try (Repository repo = new FileRepository(httpsPath.resolve(".git").toFile())) {
			historyFromHttpsClone = GitUtils.getHistory(repo);
			final Ref master = repo.exactRef("refs/heads/master");
			masterId = master.getObjectId();
		}

		/**
		 * Compare only the root rather than everything because the list obtained from
		 * GitHub contains only the master branch, thus, might be less than the full
		 * list.
		 */
		assertEquals(shas.reverse().get(0),
				historyFromHttpsClone.getRoots().stream().collect(MoreCollectors.onlyElement()));
		final List<ObjectId> commitsToMaster = ImmutableList.copyOf(
				Traverser.forGraph(historyFromHttpsClone.getGraph()::predecessors).depthFirstPostOrder(masterId));
		assertEquals(shas.reverse(), commitsToMaster);

		cloner.download(GitUri.fromUri(URI.create("ssh://git@github.com/oliviercailloux/testrel.git")), sshPath);
		try (Repository repo2 = new FileRepository(sshPath.resolve(".git").toFile())) {
			assertEquals(historyFromHttpsClone, GitUtils.getHistory(repo2));
		}

		final Path filePath = Utils.getTempDirectory().resolve("testrel cloned using file transport to ssh clone "
				+ Utils.ISO_BASIC_UTC_FORMATTER.format(Instant.now()));
		cloner.download(GitUri.fromUri(sshPath.toUri()), filePath);

		/**
		 * This clone does not clone the clone’s origin branches that are not local to
		 * the clone. Thus, their histories might differ.
		 */
		try (Repository repo3 = new FileRepository(filePath.resolve(".git").toFile())) {
			final GitHistory historyFromFileClone = GitUtils.getHistory(repo3);
			final ImmutableList<ObjectId> commitsToMasterInFileClone = ImmutableList.copyOf(
					Traverser.forGraph(historyFromFileClone.getGraph()::predecessors).depthFirstPostOrder(masterId));
			assertEquals(commitsToMaster, commitsToMasterInFileClone);
		}

		Files.writeString(sshPath.resolve("newfile.txt"), "newcontent");
		final RevCommit newCommit;
		try (Repository repo = new FileRepository(sshPath.resolve(".git").toFile())) {
			try (Git git = new Git(repo)) {
				git.add().addFilepattern("newfile.txt").call();
				final CommitCommand commit = git.commit();
				commit.setCommitter(new PersonIdent("Me", "email"));
				commit.setMessage("New commit");
				newCommit = commit.call();
				final Ref master = repo.exactRef("refs/heads/master");
				Verify.verify(master.getObjectId().equals(newCommit));
			}
		}

		/** Should update and fetch the new commit. */
		cloner.download(GitUri.fromUri(sshPath.toUri()), filePath);
		final GitHistory enlargedHistory;
		try (Repository repo = new FileRepository(filePath.resolve(".git").toFile())) {
			enlargedHistory = GitUtils.getHistory(repo);
		}
		assertNotEquals(historyFromHttpsClone, enlargedHistory);
		final ImmutableSet<ObjectId> expectedEnlargedCommits = ImmutableSet.<ObjectId>builder().addAll(commitsToMaster)
				.add(newCommit).build();
		assertEquals(expectedEnlargedCommits, enlargedHistory.getGraph().nodes());
	}

	@Test
	void testCloneBare() throws Exception {
		final Path gitDirPath = Utils.getTempDirectory()
				.resolve("testrel cloned " + Utils.ISO_BASIC_UTC_FORMATTER.format(Instant.now()));
		new GitCloner().downloadBare(GitUri.fromGitUrl("git@github.com:oliviercailloux/testrel.git"), gitDirPath);
		assertTrue(Files.exists(gitDirPath.resolve("refs")));
		assertFalse(Files.exists(gitDirPath.resolve(".git")));
	}

	@Test
	void testCloneFileProtocol() throws Exception {
		final Path workTreePath = Utils.getTempDirectory()
				.resolve("Just created " + Utils.ISO_BASIC_UTC_FORMATTER.format(Instant.now()));
		final Path gitDirPath = workTreePath.resolve(".git");
		Git.init().setDirectory(workTreePath.toFile()).call().close();

		{
			final URI uri = gitDirPath.toUri();
			LOGGER.info("Cloning from {}.", uri);
			final Path clonedTo = Utils.getTempDirectory()
					.resolve("Just cloned using .git " + Utils.ISO_BASIC_UTC_FORMATTER.format(Instant.now()));
			new GitCloner().download(GitUri.fromUri(uri), clonedTo);
			assertTrue(Files.exists(clonedTo.resolve(".git")));
			assertTrue(Files.exists(clonedTo.resolve(".git").resolve("refs")));
		}

		final URI uri = workTreePath.toUri();
		LOGGER.info("Cloning from {}.", uri);
		final Path clonedTo = Utils.getTempDirectory()
				.resolve("Just cloned " + Utils.ISO_BASIC_UTC_FORMATTER.format(Instant.now()));
		new GitCloner().download(GitUri.fromUri(uri), clonedTo);
		assertTrue(Files.exists(clonedTo.resolve(".git")));
		assertTrue(Files.exists(clonedTo.resolve(".git").resolve("refs")));
	}

	@Test
	void testCloneFileProtocolToSamePlace() throws Exception {
		final Path workTreePath = Utils.getTempDirectory()
				.resolve("Just created " + Utils.ISO_BASIC_UTC_FORMATTER.format(Instant.now()));
		final Path gitDirPath = workTreePath.resolve(".git");
		Git.init().setDirectory(workTreePath.toFile()).call().close();

		final URI uri = gitDirPath.toUri();
		assertThrows(IllegalStateException.class, () -> new GitCloner().download(GitUri.fromUri(uri), workTreePath));
	}

	@Test
	void testCloneInMemory() throws Exception {
		try (DfsRepository repo = new InMemoryRepository(new DfsRepositoryDescription("myrepo"))) {
			repo.create(true);
			/**
			 * TODO why do these fail?
			 */
			// new
			// GitCloner().clone(GitUri.fromGitUri(URI.create("ssh:git@github.com:oliviercailloux/testrel.git")),
			// repo);
			// new
			// GitCloner().clone(GitUri.fromGitUri(URI.create("https://github.com/github/testrepo.git")),
			// repo);
			new GitCloner().clone(GitUri.fromUri(URI.create("https://github.com/oliviercailloux/testrel.git")), repo);
			final Ref head = repo.findRef(Constants.HEAD);
			assertNotNull(head);
			assertEquals("e26c142665bb9f560d59b18fd80763ef45e29324", head.getLeaf().getObjectId().getName());
			try (GitFileSystem gitFs = GitFileSystemProvider.getInstance().newFileSystemFromDfsRepository(repo)) {
				assertTrue(Files.exists(gitFs.getAbsolutePath("/refs/heads/master/")));
				assertTrue(Files.exists(gitFs.getAbsolutePath("/refs/heads/master/", "Test.html")));
				assertFalse(Files.exists(gitFs.getAbsolutePath("/refs/heads/master/", "test.html")));
				assertTrue(Files.exists(gitFs.getAbsolutePath("/refs/heads/dev/", "Test.html")));
				assertFalse(Files.exists(gitFs.getAbsolutePath("/refs/heads/master/", "does not exist.txt")));
				assertFalse(
						Files.exists(gitFs.getAbsolutePath("/FFFFFFFFb0e12c98d1e424a767a91c8d9d2f3f34/", "Test.html")));
				assertFalse(
						Files.exists(gitFs.getAbsolutePath("/c0170a38b0e12c98d1e424a767a91c8d9d2f3f34/", "Test.html")));
				assertTrue(
						Files.exists(gitFs.getAbsolutePath("/c0170a38b0e12c98d1e424a767a91c8d9d2f3f34/", "ploum.txt")));
			}
		}
	}

	@Test
	void testCloneToFileUsingRepo() throws Exception {
		final Path gitDir = Utils.getTempUniqueDirectory("git-test");
		Git.init().setBare(true).setDirectory(gitDir.toFile()).call();
		try (Repository repo = new FileRepository(gitDir.toString())) {
			new GitCloner().clone(GitUri.fromUri(URI.create("https://github.com/oliviercailloux/testrel.git")), repo);
			final Ref head = repo.findRef("HEAD");
			assertEquals("e26c142665bb9f560d59b18fd80763ef45e29324", head.getLeaf().getObjectId().getName());
		}
		try (GitFileSystem gitFs = GitFileSystemProvider.getInstance().newFileSystemFromGitDir(gitDir)) {
			assertTrue(Files.exists(gitFs.getAbsolutePath("/refs/heads/master/")));
			assertTrue(Files.exists(gitFs.getAbsolutePath("/refs/heads/master/", "Test.html")));
			assertFalse(Files.exists(gitFs.getAbsolutePath("/refs/heads/master/", "test.html")));
			assertTrue(Files.exists(gitFs.getAbsolutePath("/refs/heads/dev/", "Test.html")));
			assertFalse(Files.exists(gitFs.getAbsolutePath("/refs/heads/master/", "does not exist.txt")));
			assertFalse(Files.exists(gitFs.getAbsolutePath("/FFFFFFFFb0e12c98d1e424a767a91c8d9d2f3f34/", "Test.html")));
			assertFalse(Files.exists(gitFs.getAbsolutePath("/c0170a38b0e12c98d1e424a767a91c8d9d2f3f34/", "Test.html")));
			assertTrue(Files.exists(gitFs.getAbsolutePath("/c0170a38b0e12c98d1e424a767a91c8d9d2f3f34/", "ploum.txt")));
		}
	}

}
