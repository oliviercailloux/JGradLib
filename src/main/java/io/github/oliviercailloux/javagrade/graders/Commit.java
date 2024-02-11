package io.github.oliviercailloux.javagrade.graders;

import static io.github.oliviercailloux.grade.GitGrader.Functions.resolve;
import static io.github.oliviercailloux.grade.GitGrader.Predicates.compose;
import static io.github.oliviercailloux.grade.GitGrader.Predicates.containsFileMatching;
import static io.github.oliviercailloux.grade.GitGrader.Predicates.contentMatches;
import static io.github.oliviercailloux.grade.GitGrader.Predicates.isFileNamed;
import static io.github.oliviercailloux.grade.GitGrader.Predicates.isRefBranch;

import com.google.common.collect.ImmutableSet;
import io.github.oliviercailloux.git.filter.GitHistorySimple;
import io.github.oliviercailloux.git.github.model.GitHubUsername;
import io.github.oliviercailloux.gitjfs.GitPath;
import io.github.oliviercailloux.gitjfs.GitPathRootRef;
import io.github.oliviercailloux.gitjfs.GitPathRootShaCached;
import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.CriterionGradeWeight;
import io.github.oliviercailloux.grade.DeadlineGrader;
import io.github.oliviercailloux.grade.GitGeneralGrader;
import io.github.oliviercailloux.grade.GitWork;
import io.github.oliviercailloux.grade.GradeUtils;
import io.github.oliviercailloux.grade.RepositoryFetcher;
import io.github.oliviercailloux.grade.WeightingGrade;
import io.github.oliviercailloux.grade.markers.Marks;
import io.github.oliviercailloux.grade.old.Mark;
import io.github.oliviercailloux.jaris.throwing.TPredicate;
import io.github.oliviercailloux.javagrade.testers.JavaMarkHelper;
import java.io.IOException;
import java.nio.file.Files;
import java.time.ZonedDateTime;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Commit {
  @SuppressWarnings("unused")
  private static final Logger LOGGER = LoggerFactory.getLogger(Commit.class);

  public static final String PREFIX = "commit";

  public static final ZonedDateTime DEADLINE =
      ZonedDateTime.parse("2021-01-11T14:10:00+01:00[Europe/Paris]");

  public static void main(String[] args) throws Exception {
    final RepositoryFetcher fetcher = RepositoryFetcher.withPrefix(PREFIX);
    GitGeneralGrader.using(fetcher, DeadlineGrader.usingGitGrader(Commit::grade, DEADLINE)).grade();
  }

  private Commit() {}

  public static WeightingGrade grade(GitWork work) throws IOException {
    final GitHubUsername author = work.getAuthor();
    final GitHistorySimple history = work.getHistory();
    final Set<GitPathRootShaCached> paths = history.graph().nodes();
    final ImmutableSet<GitPathRootRef> refs = history.fs().refs();

    final ImmutableSet.Builder<CriterionGradeWeight> gradeBuilder = ImmutableSet.builder();

    {
      final Mark hasCommit = Mark.binary(!history.graph().nodes().isEmpty());
      final Mark allCommitsRightName = Mark.fromNew(GradeUtils.allAndSomePathsMatchCommit(paths,
          c -> JavaMarkHelper.committerAndAuthorIs(c, author.getUsername())));
      final WeightingGrade commitsGrade = WeightingGrade.from(ImmutableSet.of(
          CriterionGradeWeight.from(Criterion.given("At least one"), hasCommit, 1d),
          CriterionGradeWeight.from(Criterion.given("Right identity"), allCommitsRightName, 3d)));
      gradeBuilder.add(CriterionGradeWeight.from(Criterion.given("Has commits"), commitsGrade, 2d));
    }

    final Pattern coucouPattern = Marks.extendWhite("coucou");
    {
      final Mark content = Mark.fromNew(
          GradeUtils.anyMatch(paths, compose(resolve("afile.txt"), contentMatches(coucouPattern))));
      final Mark branchAndContent = Mark.fromNew(GradeUtils.anyRefMatch(refs,
          isRefBranch("coucou").and(compose(resolve("afile.txt"), contentMatches(coucouPattern)))));
      final WeightingGrade coucouCommit =
          WeightingGrade.proportional(Criterion.given("'afile.txt' content (anywhere)"), content,
              Criterion.given("'coucou' content"), branchAndContent);
      gradeBuilder
          .add(CriterionGradeWeight.from(Criterion.given("Commit 'coucou'"), coucouCommit, 3d));
    }
    {
      final Pattern digitPattern = Marks.extendWhite("\\d+");
      final Mark myIdContent = Mark.fromNew(
          GradeUtils.anyMatch(paths, compose(resolve("myid.txt"), contentMatches(digitPattern))));
      final TPredicate<GitPath, IOException> p1 =
          compose(resolve("myid.txt"), contentMatches(digitPattern));
      final TPredicate<GitPath, IOException> p2 =
          compose(resolve("afile.txt"), contentMatches(coucouPattern));
      final TPredicate<GitPath, IOException> both = p1.and(p2);
      final Mark myIdAndAFileContent = Mark.fromNew(GradeUtils.anyMatch(paths, both));
      final TPredicate<GitPathRootRef, IOException> branch =
          isRefBranch("main").or(isRefBranch("master"));
      final Mark mainContent = Mark.fromNew(GradeUtils.anyRefMatch(refs, branch.and(both)));
      final CriterionGradeWeight myIdGrade =
          CriterionGradeWeight.from(Criterion.given("'myid.txt' content"), myIdContent, 1d);
      final CriterionGradeWeight myIdAndAFileGrade = CriterionGradeWeight.from(
          Criterion.given("'myid.txt' and 'afile.txt' content (anywhere)"), myIdAndAFileContent,
          1d);
      final CriterionGradeWeight mainGrade = CriterionGradeWeight
          .from(Criterion.given("'main' (or 'master') content"), mainContent, 2d);
      gradeBuilder.add(CriterionGradeWeight.from(Criterion.given("Commit 'main'"),
          WeightingGrade.from(ImmutableSet.of(myIdGrade, myIdAndAFileGrade, mainGrade)), 3d));
    }
    {
      final Mark anotherFile = Mark.fromNew(
          GradeUtils.anyMatch(paths, containsFileMatching(isFileNamed("another file.txt"))));
      final Mark devRightFile = Mark.fromNew(GradeUtils.anyRefMatch(refs,
          isRefBranch("dev").and(compose(resolve("sub/a/another file.txt"), Files::exists))));
      final WeightingGrade commit =
          WeightingGrade.proportional(Criterion.given("'another file.txt' exists"), anotherFile,
              Criterion.given("'dev' content"), devRightFile);
      gradeBuilder.add(CriterionGradeWeight.from(Criterion.given("Commit 'dev'"), commit, 2d));
    }

    return WeightingGrade.from(gradeBuilder.build());
  }
}
