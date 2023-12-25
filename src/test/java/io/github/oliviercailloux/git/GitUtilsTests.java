package io.github.oliviercailloux.git;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;

import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.Test;

import com.google.common.base.Verify;
import com.google.common.collect.ImmutableSet;
import com.google.common.graph.GraphBuilder;

import io.github.oliviercailloux.git.common.GitUri;
import io.github.oliviercailloux.git.factory.GitCloner;
import io.github.oliviercailloux.git.filter.GitHistory;
import io.github.oliviercailloux.git.filter.GitHistoryUtils;
import io.github.oliviercailloux.utils.Utils;

class GitUtilsTests {

	@Test
	void testLogFromCreated() throws Exception {
		final Path workTreePath = Utils.getTempUniqueDirectory("Just created");
		final Path gitDirPath = workTreePath.resolve(".git");
		Git.init().setDirectory(workTreePath.toFile()).call().close();

		try (Repository repository = new FileRepository(gitDirPath.toFile())) {
			final GitHistory historyEmpty = GitHistoryUtils.getHistory(repository);
			assertEquals(GraphBuilder.directed().build(), historyEmpty.getGraph());
		}

		final RevCommit newCommit;
		try (Git git = Git.open(workTreePath.toFile())) {
			git.add().addFilepattern("newfile.txt").call();
			final CommitCommand commit = git.commit();
			commit.setCommitter(new PersonIdent("Me", "email"));
			commit.setMessage("New commit");
			newCommit = commit.call();
			final Ref main = git.getRepository().exactRef("refs/heads/main");
			final ObjectId objectId = main.getObjectId();
			Verify.verify(objectId.equals(newCommit));
		}

		try (Repository repository = new FileRepository(gitDirPath.toFile())) {
			final GitHistory historyOne = GitHistoryUtils.getHistory(repository);
			assertEquals(ImmutableSet.of(newCommit), historyOne.getGraph().nodes());
			assertEquals(ImmutableSet.of(newCommit), historyOne.getRoots());
		}
	}

	@Test
	void testUsingJustCreated() throws Exception {
		final Path gitDirPath = Utils.getTempDirectory().resolve("Just created " + Instant.now()).resolve(".git");
		Git.init().setGitDir(gitDirPath.toFile()).call().close();

		try (Repository repository = new FileRepository(gitDirPath.toFile())) {
			final GitHistory historyEmpty = GitHistoryUtils.getHistory(repository);
			assertEquals(GraphBuilder.directed().build(), historyEmpty.getGraph());
		}
	}

	@Test
	void testUsingBareClone() throws Exception {
		final GitUri testRel = GitUri.fromUri(URI.create("ssh://git@github.com/oliviercailloux/testrel.git"));
		final Path repoBarePath = Utils.getTempUniqueDirectory("testrel cloned bare");
		GitCloner.create().downloadBare(testRel, repoBarePath).close();
		final GitHistory history = getHistory(repoBarePath.toFile());
		assertTrue(history.getGraph().nodes().size() >= 2);
//		new GitCloner().downloadBare(testRel, repoBarePath).close();
//		assertEquals(history, getHistory(repoBarePath.toFile()));
//		new GitCloner().download(testRel, repoBarePath).close();
//		assertEquals(history, getHistory(repoBarePath.toFile()));
	}

	@Test
	void testUsingClone() throws Exception {
		final GitUri testRel = GitUri.fromUri(URI.create("ssh://git@github.com/oliviercailloux/testrel.git"));
		final Path workTreePath = Utils.getTempUniqueDirectory("testrel cloned");
		final Path gitDir = workTreePath.resolve(".git");
		GitCloner.create().download(testRel, workTreePath).close();
		final GitHistory history = getHistory(gitDir.toFile());
		assertTrue(history.getGraph().nodes().size() >= 2);
		GitCloner.create().download(testRel, workTreePath).close();
		assertEquals(history, getHistory(gitDir.toFile()));
//		new GitCloner().downloadBare(testRel, gitDir).close();
//		assertEquals(history, getHistory(gitDir.toFile()));
	}

	@Test
	void testUsingWrongDir() throws Exception {
		assertThrows(IllegalArgumentException.class,
				() -> getHistory(Utils.getTempUniqueDirectory("not existing").toFile()));
	}

	private static GitHistory getHistory(File repoFile) throws IOException {
		final GitHistory history;
		try (Repository repository = new FileRepository(repoFile)) {
			history = GitHistoryUtils.getHistory(repository);
		}
		return history;
	}
}
