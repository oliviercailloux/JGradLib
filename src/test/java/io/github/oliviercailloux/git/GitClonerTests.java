package io.github.oliviercailloux.git;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.internal.storage.file.FileRepository;
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
			shas = commits.stream().map((c) -> c.getSha()).collect(ImmutableList.toImmutableList());
		}

		final GitCloner cloner = new GitCloner();

		final Path httpsPath = Utils.getTempDirectory()
				.resolve("testrel cloned using https " + Utils.ISO_BASIC_UTC_FORMATTER.format(Instant.now()));
		cloner.download(GitUri.fromGitUri(URI.create("https://github.com/oliviercailloux/testrel/")), httpsPath);
		final File httpsGitDirAsFile = httpsPath.resolve(".git").toFile();
		final GitLocalHistory historyFromHttpsClone = GitUtils.getHistory(httpsGitDirAsFile);
		/**
		 * Compare only the root rather than everything because the list obtained from
		 * GitHub contains only the master branch, thus, might be less than the full
		 * list.
		 */
		assertEquals(shas.reverse().get(0),
				historyFromHttpsClone.getRoots().stream().collect(MoreCollectors.onlyElement()));
		final ObjectId masterId;
		try (Repository repo = new FileRepository(httpsGitDirAsFile)) {
			final Ref master = repo.exactRef("refs/heads/master");
			masterId = master.getObjectId();
		}
		final List<ObjectId> commitsToMaster = ImmutableList
				.copyOf(Traverser.forGraph(historyFromHttpsClone.getRawGraph()).depthFirstPostOrder(masterId));
		assertEquals(shas.reverse(), commitsToMaster);

		final Path sshPath = Utils.getTempDirectory()
				.resolve("testrel cloned using ssh " + Utils.ISO_BASIC_UTC_FORMATTER.format(Instant.now()));
		cloner.download(GitUri.fromGitUri(URI.create("ssh:git@github.com:oliviercailloux/testrel.git")), sshPath);
		assertEquals(historyFromHttpsClone, GitUtils.getHistory(sshPath.resolve(".git").toFile()));

		final Path filePath = Utils.getTempDirectory().resolve("testrel cloned using file transport to ssh clone "
				+ Utils.ISO_BASIC_UTC_FORMATTER.format(Instant.now()));
		cloner.download(GitUri.fromGitUri(sshPath.toUri()), filePath);
		/**
		 * This clone does not clone the clone’s origin branches that are not local to
		 * the clone. Thus, their histories might differ.
		 */
		final GitLocalHistory historyFromFileClone = GitUtils.getHistory(filePath.resolve(".git").toFile());
		final ImmutableList<ObjectId> commitsToMasterInFileClone = ImmutableList
				.copyOf(Traverser.forGraph(historyFromFileClone.getRawGraph()).depthFirstPostOrder(masterId));
		assertEquals(commitsToMaster, commitsToMasterInFileClone);

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
		cloner.download(GitUri.fromGitUri(sshPath.toUri()), filePath);
		final GitLocalHistory enlargedHistory = GitUtils.getHistory(filePath.resolve(".git").toFile());
		assertNotEquals(historyFromHttpsClone, enlargedHistory);
		final ImmutableSet<ObjectId> expectedEnlargedCommits = ImmutableSet.<ObjectId>builder().addAll(commitsToMaster)
				.add(newCommit).build();
		assertEquals(expectedEnlargedCommits, enlargedHistory.getGraph().nodes());
	}

	@Test
	void testCloneBare() throws Exception {
		final Path gitDirPath = Utils.getTempDirectory()
				.resolve("testrel cloned " + Utils.ISO_BASIC_UTC_FORMATTER.format(Instant.now())).resolve(".git");
		new GitCloner().download(GitUri.fromGitUri(URI.create("ssh:git@github.com:oliviercailloux/testrel.git")),
				gitDirPath, true);
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
			new GitCloner().download(GitUri.fromGitUri(uri), clonedTo, false);
			assertTrue(Files.exists(clonedTo.resolve(".git")));
			assertTrue(Files.exists(clonedTo.resolve(".git").resolve("refs")));
		}

		final URI uri = workTreePath.toUri();
		LOGGER.info("Cloning from {}.", uri);
		final Path clonedTo = Utils.getTempDirectory()
				.resolve("Just cloned " + Utils.ISO_BASIC_UTC_FORMATTER.format(Instant.now()));
		new GitCloner().download(GitUri.fromGitUri(uri), clonedTo, false);
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
		assertThrows(IllegalStateException.class,
				() -> new GitCloner().download(GitUri.fromGitUri(uri), workTreePath, false));
	}

}
