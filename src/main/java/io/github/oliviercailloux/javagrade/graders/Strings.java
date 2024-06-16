package io.github.oliviercailloux.javagrade.graders;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.github.oliviercailloux.grade.BatchGitHistoryGrader;
import io.github.oliviercailloux.grade.CodeGrader;
import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.DoubleGrader;
import io.github.oliviercailloux.grade.GitFileSystemWithHistoryFetcher;
import io.github.oliviercailloux.grade.GitFileSystemWithHistoryFetcherByPrefix;
import io.github.oliviercailloux.grade.GitFsGraderUsingLast;
import io.github.oliviercailloux.grade.GradeAggregator;
import io.github.oliviercailloux.grade.Mark;
import io.github.oliviercailloux.grade.MarksTree;
import io.github.oliviercailloux.grade.MavenCodeGrader;
import io.github.oliviercailloux.grade.MavenCodeGrader.WarningsBehavior;
import io.github.oliviercailloux.jaris.exceptions.TryCatchAll;
import io.github.oliviercailloux.javagrade.bytecode.Instanciator;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Strings implements CodeGrader<RuntimeException> {
  @SuppressWarnings("unused")
  private static final Logger LOGGER = LoggerFactory.getLogger(Strings.class);

  public static final String PREFIX = "strings";

  public static final ZonedDateTime DEADLINE_ORIGINAL =
      LocalDateTime.parse("2023-04-07T14:15:00").atZone(ZoneId.of("Europe/Paris"));

  public static final Instant CAP_ORIGINAL =
      DEADLINE_ORIGINAL.toInstant().plus(Duration.ofMinutes(8));

  public static final ZonedDateTime DEADLINE_SECOND_CHANCE =
      LocalDateTime.parse("2023-05-12T23:59:59").atZone(ZoneId.of("Europe/Paris"));

  public static final double USER_WEIGHT = 0.025d;

  public static void main(String[] args) throws Exception {
    second();
  }

  public static void original() throws IOException {
    final GitFileSystemWithHistoryFetcher fetcher =
        GitFileSystemWithHistoryFetcherByPrefix.getRetrievingByPrefix(PREFIX);
    final BatchGitHistoryGrader<RuntimeException> batchGrader =
        BatchGitHistoryGrader.given(() -> fetcher);

    final Strings grader = new Strings();
    final MavenCodeGrader<RuntimeException> m =
        MavenCodeGrader.penal(grader, UncheckedIOException::new, WarningsBehavior.DO_NOT_PENALIZE);

    batchGrader.getAndWriteGrades(DEADLINE_ORIGINAL, Duration.ofMinutes(5),
        GitFsGraderUsingLast.using(m), USER_WEIGHT, Path.of("grades " + PREFIX + " original"),
        PREFIX + " original " + Instant.now().atZone(DEADLINE_ORIGINAL.getZone()));
    grader.close();
    LOGGER.info("Done original, closed.");
  }

  public static void second() throws IOException {
    final GitFileSystemWithHistoryFetcher fetcher = GitFileSystemWithHistoryFetcherByPrefix
    .getRetrievingByPrefix(PREFIX);
    final BatchGitHistoryGrader<RuntimeException> batchGrader =
        BatchGitHistoryGrader.given(() -> fetcher);

    final Strings grader = new Strings();
    final MavenCodeGrader<RuntimeException> m =
        MavenCodeGrader.penal(grader, UncheckedIOException::new, WarningsBehavior.DO_NOT_PENALIZE);
    final DoubleGrader doubleGrader = new DoubleGrader(m, DEADLINE_ORIGINAL.toInstant(),
        DEADLINE_SECOND_CHANCE.toInstant(), DEADLINE_ORIGINAL.getZone(), CAP_ORIGINAL, USER_WEIGHT);

    batchGrader.getAndWriteGrades(doubleGrader, Path.of("grades " + PREFIX + " second"),
        PREFIX + " second " + Instant.now().atZone(DEADLINE_SECOND_CHANCE.getZone()));
    grader.close();
    LOGGER.info("Done second, closed.");
  }

  private static final Criterion C_PREFIX_ONCE_EMPTY = Criterion.given("Prefix once empty");

  private static final Criterion C_PREFIX_ONCE = Criterion.given("Prefix once");
  private static final Criterion C_PREFIX_NEG_REP = Criterion.given("Prefix neg rep");
  private static final Criterion C_PREFIX_ZERO_REP = Criterion.given("Prefix zero rep");

  private static final Criterion C_PREFIX_ONE_REP = Criterion.given("Prefix one rep");
  private static final Criterion C_PREFIX_MULT_REP = Criterion.given("Prefix mult rep");
  private static final Criterion C_SUFFIX_NOT_SET = Criterion.given("Suffix not set");

  private static final Criterion C_SUFFIX = Criterion.given("Suffix");

  private final ExecutorService executors;

  public Strings() {
    executors = Executors.newCachedThreadPool();
  }

  @Override
  public MarksTree gradeCode(Instanciator instanciator) {
    final ImmutableMap.Builder<Criterion, MarksTree> builder = ImmutableMap.builder();
    final String className = "io.github.oliviercailloux.strings.StringManiper";

    {
      final TryCatchAll<Optional<String>> got = instanciator.invokeStatic(className, String.class,
          "prefixOnce", ImmutableList.of("", "a"));
      final String expected = "a";
      final boolean pass = got.map(r -> r.equals(Optional.of(expected)), c -> false);
      builder.put(C_PREFIX_ONCE_EMPTY,
          Mark.binary(pass, "",
              got.map(r -> "Expected prefixed %s, got '%s'".formatted(expected, r.orElse(null)),
                  c -> "Obtained %s".formatted(c))));
    }

    {
      final TryCatchAll<Optional<String>> got = instanciator.invokeStatic(className, String.class,
          "prefixOnce", ImmutableList.of("prefhh", "ggorig"));
      final String expected = "prefhhggorig";
      final boolean pass = got.map(r -> r.equals(Optional.of(expected)), c -> false);
      builder.put(C_PREFIX_ONCE,
          Mark.binary(pass, "",
              got.map(r -> "Expected prefixed %s, got '%s'".formatted(expected, r.orElse(null)),
                  c -> "Obtained %s".formatted(c))));
    }

    {
      final TryCatchAll<Optional<String>> got = instanciator.invokeStatic(className, String.class,
          "prefix", ImmutableList.of(2, -4, "ploum"));
      final boolean pass =
          got.map(r -> false, c -> c.getClass().equals(IllegalArgumentException.class));
      builder.put(C_PREFIX_NEG_REP,
          Mark.binary(pass, "",
              got.map(r -> "Expected exception, got '%s'".formatted(r.orElse(null)),
                  c -> "Unexpected exception: %s".formatted(c))));
    }

    {
      final TryCatchAll<Optional<String>> got = instanciator.invokeStatic(className, String.class,
          "prefix", ImmutableList.of(100, 0, "ggorig"));
      final String expected = "ggorig";
      final boolean pass = got.map(r -> r.equals(Optional.of(expected)), c -> false);
      builder.put(C_PREFIX_ZERO_REP,
          Mark.binary(pass, "",
              got.map(r -> "Expected prefixed %s, got '%s'".formatted(expected, r.orElse(null)),
                  c -> "Obtained %s".formatted(c))));
    }

    {
      final TryCatchAll<Optional<String>> got = instanciator.invokeStatic(className, String.class,
          "prefix", ImmutableList.of(100, 1, "gforig"));
      final String expected = "100gforig";
      final boolean pass = got.map(r -> r.equals(Optional.of(expected)), c -> false);
      builder.put(C_PREFIX_ONE_REP,
          Mark.binary(pass, "",
              got.map(r -> "Expected prefixed %s, got '%s'".formatted(expected, r.orElse(null)),
                  c -> "Obtained %s".formatted(c))));
    }

    {
      final TryCatchAll<Optional<String>> got = instanciator.invokeStatic(className, String.class,
          "prefix", ImmutableList.of(100, 12, "gporig"));
      final String expected = "100".repeat(12) + "gporig";
      final boolean pass = got.map(r -> r.equals(Optional.of(expected)), c -> false);
      builder.put(C_PREFIX_MULT_REP,
          Mark.binary(pass, "",
              got.map(
                  r -> "Expected prefixed '100100…100ggorig', got '%s'".formatted(r.orElse(null)),
                  c -> "Obtained %s".formatted(c))));
    }

    {
      final TryCatchAll<Optional<String>> got =
          instanciator.invokeStatic(className, String.class, "suffix", ImmutableList.of("gsorig"));
      builder.put(C_SUFFIX_NOT_SET,
          got.map(r -> Mark.zero("Expected exception, got '%s'".formatted(r.orElse(null))),
              c -> Mark.binary(c.getClass().equals(IllegalStateException.class), "",
                  "Unexpected exception: %s".formatted(c))));
    }

    {
      final TryCatchAll<Optional<String>> first = instanciator.invokeStatic(className, String.class,
          "setSuffix", ImmutableList.of("a simple suffix"));
      final Mark firstCall = first.map(r -> Mark.one(), c -> Mark.zero("Obtained %s".formatted(c)));
      final Mark mark;
      if (firstCall.getPoints() == 0d) {
        mark = firstCall;
      } else {
        final TryCatchAll<Optional<String>> got = instanciator.invokeStatic(className, String.class,
            "suffix", ImmutableList.of("gnorig"));
        final String expected = "gnoriga simple suffix";
        mark = got.map(
            r -> Mark.binary(r.equals(Optional.of(expected)), "",
                "Expected suffixed %s, got '%s'".formatted(expected, r.orElse(null))),
            c -> Mark.zero("Obtained %s".formatted(c)));
      }
      builder.put(C_SUFFIX, mark);
    }

    return MarksTree.composite(builder.build());
  }

  @Override
  public GradeAggregator getCodeAggregator() {
    final ImmutableMap.Builder<Criterion, Double> builder = ImmutableMap.builder();
    builder.put(C_PREFIX_ONCE_EMPTY, 3d);
    builder.put(C_PREFIX_ONCE, 3d);
    builder.put(C_PREFIX_NEG_REP, 3d);
    builder.put(C_PREFIX_ZERO_REP, 2.5d);
    builder.put(C_PREFIX_ONE_REP, 3d);
    builder.put(C_PREFIX_MULT_REP, 3d);
    builder.put(C_SUFFIX_NOT_SET, 1d);
    builder.put(C_SUFFIX, 1d);
    return GradeAggregator.staticAggregator(builder.build(), ImmutableMap.of());
  }

  public void close() {
    executors.shutdownNow();
  }
}
