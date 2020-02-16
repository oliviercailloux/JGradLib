package io.github.oliviercailloux.git;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;

import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import com.google.common.base.Verify;
import com.google.common.collect.ImmutableSet;
import com.google.common.graph.GraphBuilder;

import io.github.oliviercailloux.utils.Utils;

class GitUtilsTests {

	@Test
	@EnabledIfEnvironmentVariable(named = "CONTINUOUS_INTEGRATION", matches = "true")
	void testLogFromClone() throws Exception {
		final Path workTreePath = Utils.getTempDirectory().resolve("testrel cloned " + Instant.now());
		new GitCloner().download(GitUri.fromGitUri(URI.create("ssh:git@github.com:oliviercailloux/testrel.git")),
				workTreePath);
		final GitLocalHistory history = GitUtils.getHistory(workTreePath.toFile());
		assertTrue(history.getGraph().nodes().size() >= 2);
	}

	@Test
	void testLogFromCreated() throws Exception {
		final Path workTreePath = Utils.getTempDirectory().resolve("Just created " + Instant.now());
		final Path gitDirPath = workTreePath.resolve(".git");
		Git.init().setDirectory(workTreePath.toFile()).call().close();

		final GitLocalHistory historyEmpty = GitUtils.getHistory(gitDirPath.toFile());
		assertEquals(GraphBuilder.directed().build(), historyEmpty.getGraph());

		final RevCommit newCommit;
		try (Git git = Git.open(workTreePath.toFile())) {
			git.add().addFilepattern("newfile.txt").call();
			final CommitCommand commit = git.commit();
			commit.setCommitter(new PersonIdent("Me", "email"));
			commit.setMessage("New commit");
			newCommit = commit.call();
			final Ref master = git.getRepository().exactRef("refs/heads/master");
			final ObjectId objectId = master.getObjectId();
			Verify.verify(objectId.equals(newCommit));
		}

		final GitLocalHistory historyOne = GitUtils.getHistory(gitDirPath.toFile());
		assertEquals(ImmutableSet.of(newCommit), historyOne.getGraph().nodes());
		assertEquals(ImmutableSet.of(newCommit), historyOne.getRoots());
	}

	@Test
	@EnabledIfEnvironmentVariable(named = "CONTINUOUS_INTEGRATION", matches = "true")
	void testUsingJustCreated() throws Exception {
		final Path gitDirPath = Utils.getTempDirectory().resolve("Just created " + Instant.now()).resolve(".git");
		Git.init().setGitDir(gitDirPath.toFile()).call().close();

		final GitLocalHistory historyEmpty = GitUtils.getHistory(gitDirPath.toFile());
		assertEquals(GraphBuilder.directed().build(), historyEmpty.getGraph());

	}

	@Test
	@EnabledIfEnvironmentVariable(named = "CONTINUOUS_INTEGRATION", matches = "true")
	void testUsingBareClone() throws Exception {
		final GitUri testRel = GitUri.fromGitUri(URI.create("ssh:git@github.com:oliviercailloux/testrel.git"));
		final Path repoBarePath = Utils.getTempDirectory().resolve("testrel cloned bare " + Instant.now());
		new GitCloner().downloadBare(testRel, repoBarePath);
		final GitLocalHistory history = GitUtils.getHistory(repoBarePath.toFile());
		assertTrue(history.getGraph().nodes().size() >= 2);
		new GitCloner().downloadBare(testRel, repoBarePath);
		assertEquals(history, GitUtils.getHistory(repoBarePath.toFile()));
		new GitCloner().download(testRel, repoBarePath);
		assertEquals(history, GitUtils.getHistory(repoBarePath.toFile()));
	}

	@Test
	@EnabledIfEnvironmentVariable(named = "CONTINUOUS_INTEGRATION", matches = "true")
	void testUsingClone() throws Exception {
		final GitUri testRel = GitUri.fromGitUri(URI.create("ssh:git@github.com:oliviercailloux/testrel.git"));
		final Path workTreePath = Utils.getTempDirectory().resolve("testrel cloned " + Instant.now());
		final Path gitDir = workTreePath.resolve(".git");
		new GitCloner().download(testRel, workTreePath);
		final GitLocalHistory history = GitUtils.getHistory(gitDir.toFile());
		assertTrue(history.getGraph().nodes().size() >= 2);
		new GitCloner().download(testRel, workTreePath);
		assertEquals(history, GitUtils.getHistory(gitDir.toFile()));
		new GitCloner().downloadBare(testRel, gitDir);
		assertEquals(history, GitUtils.getHistory(gitDir.toFile()));
	}

	@Test
	void testUsingWrongDir() throws Exception {
		assertThrows(RepositoryNotFoundException.class,
				() -> GitUtils.getHistory(Utils.getTempDirectory().resolve("not existing " + Instant.now()).toFile()));
	}
}
