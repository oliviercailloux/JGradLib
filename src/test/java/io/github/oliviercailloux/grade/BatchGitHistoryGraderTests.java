package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkArgument;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.github.oliviercailloux.git.GitUtils;
import io.github.oliviercailloux.git.JGit;
import io.github.oliviercailloux.git.fs.GitFileSystem;
import io.github.oliviercailloux.git.fs.GitFileSystemProvider;
import io.github.oliviercailloux.git.git_hub.model.GitHubUsername;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.lib.Repository;
import org.junit.jupiter.api.Test;

public class BatchGitHistoryGraderTests {
	private static final double W_USER = 1d;
	private static final double W1 = 12d;
	private static final double W2 = 4d;
	private static final double W3 = 3d;
	private static final double W_TOT = W_USER + W1 + W2 + W3;

	private static final double USER_GRADE_WEIGHT = W_USER / W_TOT;

	private static class MyGrader implements Grader<RuntimeException> {
		private static final Criterion C1 = Criterion.given("c1");
		private static final Criterion C2 = Criterion.given("c2");
		private static final Criterion C3 = Criterion.given("c3");

		@Override
		public MarksTree grade(GitFileSystemHistory history) {
			final int nbCommits = history.getGraph().nodes().size();
			final ImmutableMap<Criterion, Mark> subGrades = ImmutableMap.of(C1, Mark.binary(nbCommits >= 1), C2,
					Mark.binary(nbCommits >= 2), C3, Mark.binary(nbCommits >= 3));
			return MarksTree.composite(subGrades);
		}

		@Override
		public GradeAggregator getAggregator() {
			return GradeAggregator.staticAggregator(ImmutableMap.of(C1, W1, C2, W2, C3, W3), ImmutableMap.of());
		}
	}

	@Test
	void testBatch() throws Exception {
		try (Repository empty = new InMemoryRepository(new DfsRepositoryDescription("empty"));
				GitFileSystem emptyGitFs = GitFileSystemProvider.getInstance().newFileSystemFromRepository(empty);
				Repository early = new InMemoryRepository(new DfsRepositoryDescription("early"));
				GitFileSystem earlyFs = GitFileSystemProvider.getInstance().newFileSystemFromRepository(early);
				Repository now = new InMemoryRepository(new DfsRepositoryDescription("now"));
				GitFileSystem nowFs = GitFileSystemProvider.getInstance().newFileSystemFromRepository(now);
				Repository late = new InMemoryRepository(new DfsRepositoryDescription("late"));
				GitFileSystem lateFs = GitFileSystemProvider.getInstance().newFileSystemFromRepository(late)) {
			final ZonedDateTime nowTime = ZonedDateTime.parse("2022-01-01T10:00:00+01:00[Europe/Paris]");

			JGit.createRepoWithSubDir(early, nowTime.minus(10, ChronoUnit.HOURS));
			JGit.createRepoWithSubDir(now, nowTime);
			JGit.createRepoWithSubDir(late, nowTime.plus(1, ChronoUnit.HOURS));
			final GitFileSystemHistory emptyWithHist = GitFileSystemHistory.create(emptyGitFs,
					GitUtils.getHistory(emptyGitFs));
			final GitFileSystemHistory earlyWithHist = GitFileSystemHistory.create(earlyFs,
					GitUtils.getHistory(earlyFs));
			final GitFileSystemHistory nowWithHist = GitFileSystemHistory.create(nowFs, GitUtils.getHistory(nowFs));
			final GitFileSystemHistory lateWithHist = GitFileSystemHistory.create(lateFs, GitUtils.getHistory(lateFs));
			final GitHubUsername userEmpty = GitHubUsername.given("user-empty");
			final GitHubUsername userEarly = GitHubUsername.given("user-early");
			final GitHubUsername userNow = GitHubUsername.given("user-now");
			final GitHubUsername userLate = GitHubUsername.given("user-late");

			final ImmutableMap<GitHubUsername, GitFileSystemHistory> gitFses = ImmutableMap.of(userEmpty, emptyWithHist,
					userEarly, earlyWithHist, userNow, nowWithHist, userLate, lateWithHist);
			final BatchGitHistoryGrader<RuntimeException> batchGrader = new BatchGitHistoryGrader<>(
					() -> new StaticFetcher(gitFses));

			final Exam exam = batchGrader.getAndWriteGrades("testprefix", nowTime.plus(30, ChronoUnit.MINUTES),
					Duration.of(1, ChronoUnit.HOURS), new MyGrader(), USER_GRADE_WEIGHT, Path.of("test grades.json"));

			assertEquals(gitFses.keySet(), exam.getUsernames());
			assertEquals(Mark.zero("No commit found."), exam.getGrade(userEmpty).mark());
			assertEquals((W1 + W2 + W3) / W_TOT, exam.getGrade(userEarly).mark().getPoints(), 1e-6d);
			assertEquals(W1 / W_TOT, exam.getGrade(userNow).mark().getPoints(), 1e-6d);
			assertEquals(W1 / W_TOT / 2d, exam.getGrade(userLate).mark().getPoints(), 1e-6d);
		}
	}

	static class StaticFetcher implements GitFileSystemWithHistoryFetcher {
		private final ImmutableMap<GitHubUsername, GitFileSystemHistory> gitFsesByauthor;

		public StaticFetcher(Map<GitHubUsername, GitFileSystemHistory> gitFsesByauthor) {
			this.gitFsesByauthor = ImmutableMap.copyOf(gitFsesByauthor);
		}

		@Override
		public ImmutableSet<GitHubUsername> getAuthors() {
			return gitFsesByauthor.keySet();
		}

		@Override
		public GitFileSystemHistory goTo(GitHubUsername author) throws IOException {
			checkArgument(gitFsesByauthor.containsKey(author));
			return gitFsesByauthor.get(author);
		}

		@Override
		public void close() {
			/* Nothing to close. */
		}
	}
}
