package io.github.oliviercailloux.git.fs;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.common.collect.ImmutableList;
import io.github.oliviercailloux.git.JGit;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.Test;

public class JGitTests {
	@Test
	void testCreateRepo() throws Exception {
		try (Repository repo = new InMemoryRepository(new DfsRepositoryDescription("myrepo"))) {
//		try (Repository repo = new FileRepository(Utils.getTempUniqueDirectory("created").toFile())) {
			JGit.createRepoWithSubDir(repo);

			try (Git git = Git.wrap(repo)) {
				final ImmutableList<RevCommit> read = ImmutableList.copyOf(git.log().call());
				assertEquals(3, read.size());
			}
		}
	}
}
