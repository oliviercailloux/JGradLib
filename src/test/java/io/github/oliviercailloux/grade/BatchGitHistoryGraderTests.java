package io.github.oliviercailloux.grade;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.common.collect.ImmutableMap;
import io.github.oliviercailloux.factogit.JGit;
import io.github.oliviercailloux.git.filter.GitHistorySimple;
import io.github.oliviercailloux.git.git_hub.model.GitHubUsername;
import io.github.oliviercailloux.gitjfs.GitFileSystem;
import io.github.oliviercailloux.gitjfs.GitFileSystemProvider;
import io.github.oliviercailloux.grade.format.json.JsonSimpleGrade;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
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

  private static class MyGrader implements GitFsGrader<RuntimeException> {
    private static final Criterion C1 = Criterion.given("c1");
    private static final Criterion C2 = Criterion.given("c2");
    private static final Criterion C3 = Criterion.given("c3");

    @Override
    public MarksTree grade(GitHistorySimple history) {
      final int nbCommits = history.graph().nodes().size();
      final ImmutableMap<Criterion, Mark> subGrades =
          ImmutableMap.of(C1, Mark.binary(nbCommits >= 1), C2, Mark.binary(nbCommits >= 2), C3,
              Mark.binary(nbCommits >= 3));
      return MarksTree.composite(subGrades);
    }

    @Override
    public GradeAggregator getAggregator() {
      return GradeAggregator.staticAggregator(ImmutableMap.of(C1, W1, C2, W2, C3, W3),
          ImmutableMap.of());
    }
  }

  @Test
  void testBatch() throws Exception {
    try (Repository empty = new InMemoryRepository(new DfsRepositoryDescription("empty"));
        GitFileSystem emptyGitFs =
            GitFileSystemProvider.instance().newFileSystemFromRepository(empty);
        Repository early = new InMemoryRepository(new DfsRepositoryDescription("early"));
        GitFileSystem earlyFs = GitFileSystemProvider.instance().newFileSystemFromRepository(early);
        Repository now = new InMemoryRepository(new DfsRepositoryDescription("now"));
        GitFileSystem nowFs = GitFileSystemProvider.instance().newFileSystemFromRepository(now);
        Repository late = new InMemoryRepository(new DfsRepositoryDescription("late"));
        GitFileSystem lateFs = GitFileSystemProvider.instance().newFileSystemFromRepository(late)) {
      final ZonedDateTime nowTime = ZonedDateTime.parse("2022-01-01T10:00:00+01:00[Europe/Paris]");

      JGit.createRepoWithSubDir(early, nowTime.minus(10, ChronoUnit.HOURS));
      JGit.createRepoWithSubDir(now, nowTime);
      JGit.createRepoWithSubDir(late, nowTime.plus(1, ChronoUnit.HOURS));
      final GitHistorySimple emptyWithHist = GitHistorySimple.usingCommitterDates(emptyGitFs);
      final GitHistorySimple earlyWithHist = GitHistorySimple.usingCommitterDates(earlyFs);
      final GitHistorySimple nowWithHist = GitHistorySimple.usingCommitterDates(nowFs);
      final GitHistorySimple lateWithHist = GitHistorySimple.usingCommitterDates(lateFs);
      final GitHubUsername userEmpty = GitHubUsername.given("user-empty");
      final GitHubUsername userEarly = GitHubUsername.given("user-early");
      final GitHubUsername userNow = GitHubUsername.given("user-now");
      final GitHubUsername userLate = GitHubUsername.given("user-late");

      final ImmutableMap<GitHubUsername, GitHistorySimple> gitFses = ImmutableMap.of(userEmpty,
          emptyWithHist, userEarly, earlyWithHist, userNow, nowWithHist, userLate, lateWithHist);
      final BatchGitHistoryGrader<RuntimeException> batchGrader =
          BatchGitHistoryGrader.given(() -> StaticFetcher.multiple(gitFses));

      // final Exam exam = batchGrader.getAndWriteGrades(nowTime.plus(30, ChronoUnit.MINUTES),
      // Duration.of(1, ChronoUnit.HOURS), new MyGrader(), USER_GRADE_WEIGHT, Path.of("test
      // grades.json"),
      // "testprefix");
      final Exam exam = batchGrader.getGrades(nowTime.plus(30, ChronoUnit.MINUTES),
          Duration.of(1, ChronoUnit.HOURS), new MyGrader(), USER_GRADE_WEIGHT);
      Files.writeString(Path.of("exam.json"), JsonSimpleGrade.toJson(exam));

      assertEquals(gitFses.keySet(), exam.getUsernames());
      assertEquals(0d, exam.getGrade(userEmpty).mark().getPoints());
      /* To check. */
      // assertEquals(Mark.zero("No commit found."), exam.getGrade(userEmpty).mark());
      assertEquals((W1 + W2 + W3) / W_TOT, exam.getGrade(userEarly).mark().getPoints(), 1e-6d);
      assertEquals(W1 / W_TOT, exam.getGrade(userNow).mark().getPoints(), 1e-6d);
      assertEquals(W1 / W_TOT / 2d, exam.getGrade(userLate).mark().getPoints(), 1e-6d);
    }
  }
}
