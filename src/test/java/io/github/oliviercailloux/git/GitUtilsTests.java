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

import com.google.common.base.Verify;
import com.google.common.collect.ImmutableSet;
import com.google.common.graph.GraphBuilder;

import io.github.oliviercailloux.utils.Utils;

class GitUtilsTests {

	@Test
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
	void testUsingBareCreated() throws Exception {
		final Path gitDirPath = Utils.getTempDirectory().resolve("Just created " + Instant.now()).resolve(".git");
		Git.init().setGitDir(gitDirPath.toFile()).call().close();

		final GitLocalHistory historyEmpty = GitUtils.getHistory(gitDirPath.toFile());
		assertEquals(GraphBuilder.directed().build(), historyEmpty.getGraph());

	}

	@Test
	void testUsingBareClone() throws Exception {
		final Path gitDirPath = Utils.getTempDirectory().resolve("testrel cloned " + Instant.now()).resolve(".git");
		new GitCloner().download(GitUri.fromGitUri(URI.create("ssh:git@github.com:oliviercailloux/testrel.git")),
				gitDirPath, true);
		final GitLocalHistory history = GitUtils.getHistory(gitDirPath.toFile());
		assertTrue(history.getGraph().nodes().size() >= 2);
	}

	@Test
	void testUsingWrongDir() throws Exception {
		assertThrows(RepositoryNotFoundException.class,
				() -> GitUtils.getHistory(Utils.getTempDirectory().resolve("not existing " + Instant.now()).toFile()));
	}
}
