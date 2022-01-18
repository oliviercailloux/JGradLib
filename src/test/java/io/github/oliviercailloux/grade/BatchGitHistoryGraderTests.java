package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.github.oliviercailloux.git.GitUtils;
import io.github.oliviercailloux.git.fs.GitFileSystem;
import io.github.oliviercailloux.git.fs.GitFileSystemProvider;
import io.github.oliviercailloux.git.git_hub.model.GitHubUsername;
import java.io.IOException;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.lib.Repository;
import org.junit.jupiter.api.Test;

public class BatchGitHistoryGraderTests {
	@Test
	void testBatch() throws Exception {
		try (Repository repository = new InMemoryRepository(new DfsRepositoryDescription("myrepo"));
				GitFileSystem gitFs = GitFileSystemProvider.getInstance().newFileSystemFromRepository(repository)) {
			final ImmutableSet<GitHubUsername> authors = ImmutableSet.of(GitHubUsername.given("user1"),
					GitHubUsername.given("user2"));
			final BatchGitHistoryGrader<RuntimeException> batchGrader = new BatchGitHistoryGrader<>(
					() -> new StaticFetcher(authors, gitFs));
			final ImmutableMap<GitHubUsername, IGrade> grades = batchGrader.getAndWriteGrades("testprefix",
					ZonedDateTime.now(), this::grader, Path.of("test grades.json"));
			assertEquals(authors, grades.keySet());
			assertEquals(ImmutableSet.of(Mark.zero("No commit found.")), ImmutableSet.copyOf(grades.values()));
		}
	}

	public IGrade grader(GitFileSystemHistory history) throws Exception {
		return Mark.one();
	}

	static class StaticFetcher implements GitFileSystemWithHistoryFetcher {
		private final ImmutableSet<GitHubUsername> authors;

		private final GitFileSystem gitFs;

		public StaticFetcher(ImmutableSet<GitHubUsername> authors, GitFileSystem gitFs) {
			this.authors = checkNotNull(authors);
			this.gitFs = checkNotNull(gitFs);
		}

		@Override
		public ImmutableSet<GitHubUsername> getAuthors() {
			return authors;
		}

		@Override
		public GitFileSystemHistory goTo(GitHubUsername author) throws IOException {
			return GitFileSystemHistory.create(gitFs, GitUtils.getHistory(gitFs));
		}

		@Override
		public void close() {
			/* Nothing to close. */
		}
	}
}
