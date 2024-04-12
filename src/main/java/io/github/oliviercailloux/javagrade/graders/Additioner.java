package io.github.oliviercailloux.javagrade.graders;

import com.google.common.collect.ImmutableMap;
import io.github.oliviercailloux.exercices.additioner.MyAdditioner;
import io.github.oliviercailloux.git.github.model.GitHubUsername;
import io.github.oliviercailloux.git.github.model.RepositoryCoordinates;
import io.github.oliviercailloux.git.github.model.RepositoryCoordinatesWithPrefix;
import io.github.oliviercailloux.grade.BatchGitHistoryGrader;
import io.github.oliviercailloux.grade.CodeGrader;
import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.GitFileSystemWithHistoryFetcher;
import io.github.oliviercailloux.grade.GitFileSystemWithHistoryFetcherFromMap;
import io.github.oliviercailloux.grade.GitFsGraderUsingLast;
import io.github.oliviercailloux.grade.GradeAggregator;
import io.github.oliviercailloux.grade.Mark;
import io.github.oliviercailloux.grade.MarksTree;
import io.github.oliviercailloux.grade.MavenCodeGrader;
import io.github.oliviercailloux.grade.MavenCodeGrader.WarningsBehavior;
import io.github.oliviercailloux.grade.format.CsvGrades;
import io.github.oliviercailloux.jaris.exceptions.TryCatchAll;
import io.github.oliviercailloux.javagrade.bytecode.Instanciator;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Additioner implements CodeGrader<RuntimeException> {
  @SuppressWarnings("unused")
  private static final Logger LOGGER = LoggerFactory.getLogger(Additioner.class);

  public static final ZonedDateTime DEADLINE_ORIGINAL =
      LocalDateTime.parse("2024-04-10T19:00:00").atZone(ZoneId.of("Europe/Paris"));
  public static final double USER_WEIGHT = 0d;

  public static void main(String[] args) throws Exception {
    final GitFileSystemWithHistoryFetcher fetcher =
        GitFileSystemWithHistoryFetcherFromMap
            .fromMap(ImmutableMap.of(GitHubUsername.given("oliviercailloux-org"),
                RepositoryCoordinatesWithPrefix.from("oliviercailloux-org", "add",
                    "oliviercailloux"),
                GitHubUsername.given("oliviercailloux"),
                RepositoryCoordinates.from("oliviercailloux", "superadd")), true);
    final BatchGitHistoryGrader<RuntimeException> batchGrader =
        BatchGitHistoryGrader.given(() -> fetcher);
    batchGrader.setIdentityFunction(CsvGrades.STUDENT_USERNAME_FUNCTION);

    final Additioner grader = new Additioner();
    final MavenCodeGrader<RuntimeException> m =
        MavenCodeGrader.penal(grader, UncheckedIOException::new, WarningsBehavior.DO_NOT_PENALIZE);

    batchGrader.getAndWriteGradesExp(DEADLINE_ORIGINAL, Duration.ofMinutes(30),
        GitFsGraderUsingLast.using(m), USER_WEIGHT, Path.of("grades add"),
        "add" + Instant.now().atZone(DEADLINE_ORIGINAL.getZone()));
    LOGGER.info("Done original, closed.");
  }

  private static final Criterion ADD = Criterion.given("Three plus two");
  private static final Criterion ADD_NEG = Criterion.given("Minus four plus two");

  @Override
  public MarksTree gradeCode(Instanciator instanciator) {
    final ImmutableMap.Builder<Criterion, MarksTree> builder = ImmutableMap.builder();

    final TryCatchAll<MyAdditioner> my = instanciator.tryGetInstance(MyAdditioner.class);
    final TryCatchAll<Integer> added32 = my.andApply(c -> c.add(3, 2));
    builder.put(ADD, added32.map(b -> Mark.binary(b == 5, "", "3 + 2 should equal 5"),
    c -> Mark.zero("Obtained %s".formatted(c))));
    final TryCatchAll<Integer> addedNeg = my.andApply(c -> c.add(-4, 2));
    builder.put(ADD_NEG, addedNeg.map(b -> Mark.binary(b == -2, "", "-4 + 2 should equal -2"),
        c -> Mark.zero("Obtained %s".formatted(c))));

    return MarksTree.composite(builder.build());
  }

  @Override
  public GradeAggregator getCodeAggregator() {
    final ImmutableMap.Builder<Criterion, Double> innerBuilder = ImmutableMap.builder();
    innerBuilder.put(ADD, 3d);
    innerBuilder.put(ADD_NEG, 2d);
    return GradeAggregator.staticAggregator(innerBuilder.build(), ImmutableMap.of());
  }

}
