package io.github.oliviercailloux.javagrade.graders;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Verify.verify;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterators;
import com.google.common.util.concurrent.SimpleTimeLimiter;
import io.github.oliviercailloux.grade.BatchGitHistoryGrader;
import io.github.oliviercailloux.grade.CodeGrader;
import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.GitFileSystemWithHistoryFetcher;
import io.github.oliviercailloux.grade.GitFileSystemWithHistoryFetcherByPrefix;
import io.github.oliviercailloux.grade.GitFsGraderUsingLast;
import io.github.oliviercailloux.grade.GradeAggregator;
import io.github.oliviercailloux.grade.Mark;
import io.github.oliviercailloux.grade.MarksTree;
import io.github.oliviercailloux.grade.MavenCodeGrader;
import io.github.oliviercailloux.grade.MavenCodeGrader.WarningsBehavior;
import io.github.oliviercailloux.jaris.exceptions.TryCatchAll;
import io.github.oliviercailloux.jaris.throwing.TSupplier;
import io.github.oliviercailloux.javagrade.bytecode.Instanciator;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Colors implements CodeGrader<RuntimeException> {
  @SuppressWarnings("unused")
  private static final Logger LOGGER = LoggerFactory.getLogger(Colors.class);

  public static final String PREFIX = "colors";

  public static final ZonedDateTime DEADLINE_ORIGINAL =
      LocalDateTime.parse("2023-05-24T14:55:00").atZone(ZoneId.of("Europe/Paris"));
  public static final ZonedDateTime DEADLINE_SECOND_CHANCE =
      LocalDateTime.parse("2023-06-09T23:59:59").atZone(ZoneId.of("Europe/Paris"));

  public static final double USER_WEIGHT = 0.0125d;

  public static Mark causeToMark(Throwable e) {
    final String messagePart =
        e.getMessage() == null ? "" : " with message ‘%s’".formatted(e.getMessage());
    return Mark.zero("Code failed with %s".formatted(e.getClass().getName()) + messagePart);
  }

  public static void main(String[] args) throws Exception {
    // original();
    second();
  }

  public static void original() throws IOException {
    final GitFileSystemWithHistoryFetcher fetcher =
        GitFileSystemWithHistoryFetcherByPrefix.getRetrievingByPrefix(PREFIX);
    final BatchGitHistoryGrader<RuntimeException> batchGrader =
        BatchGitHistoryGrader.given(() -> fetcher);

    final Colors grader = new Colors();
    final MavenCodeGrader<RuntimeException> m =
        MavenCodeGrader.penal(grader, UncheckedIOException::new, WarningsBehavior.DO_NOT_PENALIZE);

    batchGrader.getAndWriteGradesExp(DEADLINE_ORIGINAL, Duration.ofMinutes(20),
        GitFsGraderUsingLast.using(m),
        // USER_WEIGHT, Path.of("grades " + PREFIX + " original"),
        // PREFIX + " original " + Instant.now().atZone(DEADLINE_ORIGINAL.getZone()));
        USER_WEIGHT, Path.of("grades " + PREFIX),
        PREFIX + Instant.now().atZone(DEADLINE_ORIGINAL.getZone()));
    grader.close();
    LOGGER.info("Done original, closed.");
  }

  public static void second() throws IOException {
    final GitFileSystemWithHistoryFetcher fetcher = GitFileSystemWithHistoryFetcherByPrefix
        .getRetrievingByPrefixAndFiltering(PREFIX, "Arvindesss");
    // .getRetrievingByPrefix(PREFIX);

    final BatchGitHistoryGrader<RuntimeException> batchGrader =
        BatchGitHistoryGrader.given(() -> fetcher);

    final Colors grader = new Colors();
    final MavenCodeGrader<RuntimeException> m =
        MavenCodeGrader.penal(grader, UncheckedIOException::new, WarningsBehavior.DO_NOT_PENALIZE);

    batchGrader.getAndWriteGrades(DEADLINE_SECOND_CHANCE, Duration.ofMinutes(120),
        GitFsGraderUsingLast.using(m),
        // USER_WEIGHT, Path.of("grades " + PREFIX + " original"),
        // PREFIX + " original " + Instant.now().atZone(DEADLINE_ORIGINAL.getZone()));
        USER_WEIGHT, Path.of("grades " + PREFIX + " second"),
        PREFIX + Instant.now().atZone(DEADLINE_SECOND_CHANCE.getZone()));
    grader.close();
    LOGGER.info("Done original, closed.");
  }

  private static final String CLASS_CYCLING_COLORS =
      "io.github.oliviercailloux.exercices.colors.CyclingColors";

  private static final Criterion C0 = Criterion.given("Anything committed");

  private static final Criterion CYCLE_ONE = Criterion.given("Cycle with one");
  private static final Criterion CYCLE_THREE = Criterion.given("Cycle with three");
  private static final Criterion CYCLE_MANY = Criterion.given("Cycle with many");

  private static final Criterion SIZE_ONE = Criterion.given("Size with one");
  private static final Criterion SIZE_THREE = Criterion.given("Size with three");
  private static final Criterion SIZE_MANY = Criterion.given("Size with many");

  private static final Criterion DUPL_ONE = Criterion.given("Dupl with one");
  private static final Criterion DUPL_THREE = Criterion.given("Dupl with three");
  private static final Criterion DUPL_MANY = Criterion.given("Dupl with many");

  private static final Criterion AS_SET_ONE = Criterion.given("AsSet with one");

  private static final Criterion AS_SET_THREE = Criterion.given("AsSet with three");

  private static final Criterion AS_SET_MANY = Criterion.given("AsSet with many");

  private static final Criterion SNAPSHOTS_ONE = Criterion.given("Snapshots with one");

  private static final Criterion SNAPSHOTS_THREE = Criterion.given("Snapshots with three");

  private static final Criterion SNAPSHOTS_MANY = Criterion.given("Snapshots with many");

  private static final Criterion EQUALS = Criterion.given("Equality");

  private final ExecutorService executors;

  private final SimpleTimeLimiter limiter;

  public Colors() {
    executors = Executors.newCachedThreadPool();
    limiter = SimpleTimeLimiter.create(executors);
  }

  @Override
  public MarksTree gradeCode(Instanciator instanciator) {
    final ImmutableMap.Builder<Criterion, MarksTree> builder = ImmutableMap.builder();
    {
      builder.put(C0, Mark.one());
    }

    final ImmutableList<String> inputOne = ImmutableList.of("k", "k", "k");
    final ImmutableList<String> inputThree = ImmutableList.of("j", "kkk", "123");
    final ImmutableList<String> inputMany =
        IntStream.range(0, 30).boxed().map(i -> "elm" + i).collect(ImmutableList.toImmutableList());

    {
      final TryCatchAll<Boolean> attempt = TryCatchAll.get(() -> {
        final Iterable<?> cycling = init(instanciator, inputOne);

        return cycles(ImmutableSet.copyOf(inputOne), cycling);
      });
      builder.put(CYCLE_ONE, mark(attempt));
    }

    {
      final TryCatchAll<Boolean> attempt = TryCatchAll.get(() -> {
        final Iterable<?> cycling = init(instanciator, inputThree);

        return cycles(ImmutableSet.copyOf(inputThree), cycling);
      });
      builder.put(CYCLE_THREE, mark(attempt));
    }

    {
      final TryCatchAll<Boolean> attempt = TryCatchAll.get(() -> {
        final Iterable<?> cycling = init(instanciator, inputMany);

        return cycles(ImmutableSet.copyOf(inputMany), cycling);
      });
      builder.put(CYCLE_MANY, mark(attempt));
    }

    {
      final TryCatchAll<Boolean> attempt = TryCatchAll.get(() -> {
        final Iterable<?> cycling = init(instanciator, inputOne);
        final int size = Instanciator.invokeProducing(cycling, Integer.class, "size").orThrow();
        return size == ImmutableSet.copyOf(inputOne).size();
      });
      builder.put(SIZE_ONE, mark(attempt));
    }

    {
      final TryCatchAll<Boolean> attempt = TryCatchAll.get(() -> {
        final Iterable<?> cycling = init(instanciator, inputThree);
        final int size = Instanciator.invokeProducing(cycling, Integer.class, "size").orThrow();
        return size == ImmutableSet.copyOf(inputThree).size();
      });
      builder.put(SIZE_THREE, mark(attempt));
    }

    {
      final TryCatchAll<Boolean> attempt = TryCatchAll.get(() -> {
        final Iterable<?> cycling = init(instanciator, inputMany);
        final int size = Instanciator.invokeProducing(cycling, Integer.class, "size").orThrow();
        return size == ImmutableSet.copyOf(inputMany).size();
      });
      builder.put(SIZE_MANY, mark(attempt));
    }

    {
      final TryCatchAll<Mark> attempt = TryCatchAll.get(() -> {
        final Iterable<?> cycling = init(instanciator, inputOne);
        return callWithTimeout(() -> duplicates(ImmutableSet.copyOf(inputOne), cycling));
      });
      builder.put(DUPL_ONE, attempt.orMapCause(Colors::causeToMark));
    }

    {
      final TryCatchAll<Mark> attempt = TryCatchAll.get(() -> {
        final Iterable<?> cycling = init(instanciator, inputThree);
        return callWithTimeout(() -> duplicates(ImmutableSet.copyOf(inputThree), cycling));
      });
      builder.put(DUPL_THREE, attempt.orMapCause(Colors::causeToMark));
    }

    {
      final TryCatchAll<Mark> attempt = TryCatchAll.get(() -> {
        final Iterable<?> cycling = init(instanciator, inputMany);
        return callWithTimeout(() -> duplicates(ImmutableSet.copyOf(inputMany), cycling));
      });
      builder.put(DUPL_MANY, attempt.orMapCause(Colors::causeToMark));
    }

    {
      final TryCatchAll<Boolean> attempt = TryCatchAll.get(() -> {
        final Iterable<?> cycling = init(instanciator, inputOne);
        return transformsToSet(ImmutableSet.copyOf(inputOne), cycling);
      });
      builder.put(AS_SET_ONE, mark(attempt));
    }

    {
      final TryCatchAll<Boolean> attempt = TryCatchAll.get(() -> {
        final Iterable<?> cycling = init(instanciator, inputThree);
        return transformsToSet(ImmutableSet.copyOf(inputThree), cycling);
      });
      builder.put(AS_SET_THREE, mark(attempt));
    }

    {
      final TryCatchAll<Boolean> attempt = TryCatchAll.get(() -> {
        final Iterable<?> cycling = init(instanciator, inputMany);
        return transformsToSet(ImmutableSet.copyOf(inputMany), cycling);
      });
      builder.put(AS_SET_MANY, mark(attempt));
    }

    {
      final TryCatchAll<Boolean> attempt = TryCatchAll.get(() -> {
        final Iterable<?> cycling = init(instanciator, inputOne);
        return snapshots(ImmutableSet.copyOf(inputOne), cycling);
      });
      builder.put(SNAPSHOTS_ONE, mark(attempt));
    }

    {
      final TryCatchAll<Boolean> attempt = TryCatchAll.get(() -> {
        final Iterable<?> cycling = init(instanciator, inputThree);
        return snapshots(ImmutableSet.copyOf(inputThree), cycling);
      });
      builder.put(SNAPSHOTS_THREE, mark(attempt));
    }

    {
      final TryCatchAll<Boolean> attempt = TryCatchAll.get(() -> {
        final Iterable<?> cycling = init(instanciator, inputMany);
        return snapshots(ImmutableSet.copyOf(inputMany), cycling);
      });
      builder.put(SNAPSHOTS_MANY, mark(attempt));
    }

    {
      final TryCatchAll<Mark> attempt = TryCatchAll.get(() -> {
        final ImmutableMultiset.Builder<ImmutableList<String>> inputsMultBuilder =
            ImmutableMultiset.builder();
        for (int i = 0; i < 59; ++i) {
          final ImmutableList<String> zero = ImmutableList.of("aa", "aa", "bb", "col" + i);
          final int shift = i % 3;
          final ImmutableList<String> part1 = zero.subList(shift, zero.size());
          final ImmutableList<String> part2 = zero.subList(0, shift);
          final ImmutableList<String> colors =
              ImmutableList.<String>builder().addAll(part1).addAll(part2).build();
          verify(colors.size() == zero.size(), "Unexpected size.");

          final int occurrences = i < 50 ? 1 : (i - 48);

          inputsMultBuilder.addCopies(colors, occurrences);
        }
        final ImmutableMultiset<ImmutableList<String>> inputsMult = inputsMultBuilder.build();
        Mark mark = Mark.one();
        final ImmutableList<ImmutableList<String>> inputs = inputsMult.asList();
        for (ImmutableList<String> input1 : inputs) {
          final Iterable<?> cycling1 = init(instanciator, input1);
          for (ImmutableList<String> input2 : inputs) {
            final Iterable<?> cycling2 = init(instanciator, input2);
            final boolean obsEquals = cycling1.equals(cycling2);
            final boolean expectedEquals =
                ImmutableSet.copyOf(input1).equals(ImmutableSet.copyOf(input2));
            verify(mark.getPoints() == 1d, "Unexpected points state.");
            if (obsEquals != expectedEquals) {
              if (expectedEquals) {
                mark = Mark.zero(
                    "Using equivalent inputs %s and %s, observed non equal resulting instances"
                        .formatted(input1, input2));
              } else {
                mark =
                    Mark.zero("Using different inputs %s and %s, observed equal resulting instances"
                        .formatted(input1, input2));
              }
            } else if (expectedEquals) {
              final boolean equalH = cycling1.hashCode() == cycling2.hashCode();
              if (!equalH) {
                mark = Mark
                    .zero("Using equivalent inputs %s and %s, observed equal resulting instances "
                        + "but different hash codes".formatted(input1, input2));
              }
            }
            if (mark.getPoints() == 0d) {
              break;
            }
          }
          if (mark.getPoints() == 0d) {
            break;
          }
        }
        return mark;
      });
      builder.put(EQUALS, attempt.orMapCause(Colors::causeToMark));
    }

    return MarksTree.composite(builder.build());
  }

  private <T> T callWithTimeout(TSupplier<? extends T, ?> callable) throws Throwable {
    return limiter.callWithTimeout(() -> TryCatchAll.get(callable), Duration.ofSeconds(5))
        .orThrow();
  }

  private void addColors(Iterable<?> cycling, ImmutableSet<String> colors) throws Throwable {
    Instanciator.invoke(cycling, Void.class, "addColors", ImmutableList.of(colors)).orThrow();
  }

  private Iterable<?> init(Instanciator instanciator, ImmutableList<String> input)
      throws Throwable {
    checkArgument(input.size() >= 3);

    final ImmutableList<String> init;
    final ImmutableSet<String> next;
    if (input.size() >= 4) {
      init = input.subList(0, 3);
      next = ImmutableSet.copyOf(input.subList(3, input.size()));
    } else {
      init = input;
      next = ImmutableSet.of();
    }
    verify(init.size() == 3);

    final Iterable<?> cycling =
        instanciator.invokeConstructor(CLASS_CYCLING_COLORS, Iterable.class, init).orThrow();
    if (!next.isEmpty()) {
      addColors(cycling, next);
    }
    return cycling;
  }

  private ImmutableList<?> limitedContents(Iterable<?> cycling, int nbQueried) {
    // LOGGER.info("Querying for {}.", nbQueried);
    // final ImmutableList.Builder<Object> builderD = ImmutableList.builder();
    // builderD.addAll(Iterators.limit(cycling.iterator(), nbQueried));
    // final ImmutableList<?> elemsD = builderD.build();
    // LOGGER.info("Elems direct: {}.", elemsD);

    final Iterator<?> it = cycling.iterator();
    final int firstNb = nbQueried / 2;
    final ImmutableList.Builder<Object> builder = ImmutableList.builder();
    builder.addAll(Iterators.limit(it, firstNb));
    builder.addAll(Iterators.limit(it, nbQueried - firstNb));
    final ImmutableList<?> elems = builder.build();
    LOGGER.debug("Elems split: {}.", elems);
    return elems;
  }

  private boolean cycles(ImmutableSet<String> inputDistinct, Iterable<?> cycling) {
    final int nbDistinct = inputDistinct.size();
    final int nbQueried = nbDistinct + 5;
    final ImmutableList<?> elems = limitedContents(cycling, nbQueried);
    return elems.size() == nbQueried
        && ImmutableSet.copyOf(elems.subList(0, nbDistinct)).equals(inputDistinct)
        && ImmutableSet.copyOf(elems.subList(nbDistinct, nbQueried))
            .equals(ImmutableSet.copyOf(elems.subList(0, 5)));
  }

  private Mark duplicates(ImmutableSet<String> inputDistinct, Iterable<?> cycling)
      throws Throwable {
    checkArgument(!inputDistinct.isEmpty());
    checkArgument(!inputDistinct.contains("new1"));
    checkArgument(!inputDistinct.contains("new2"));

    cycling.iterator().next();

    final Collection<?> dupl = Instanciator
        .invokeProducing(cycling, Collection.class, "withDuplicatedFirstColor").orThrow();

    cycling.iterator().next();

    addColors(cycling, ImmutableSet.of("new1, new2"));
    final ImmutableList<?> dupls = limitedContents(dupl, inputDistinct.size() + 1);
    final ImmutableList<?> duplRest = dupls.subList(1, dupls.size());
    final boolean success = !dupl.isEmpty() && dupl.size() == inputDistinct.size() + 1
        && dupls.get(0).equals(dupls.get(1)) && inputDistinct.equals(ImmutableSet.copyOf(duplRest));
    return Mark.binary(success, "",
        "Expected (but first) %s, Seen (whole) %s".formatted(inputDistinct, dupls));
    // return Mark.binary(success);
  }

  private boolean transformsToSet(ImmutableSet<String> inputDistinct, Iterable<?> cycling)
      throws Throwable {
    checkArgument(!inputDistinct.isEmpty());
    checkArgument(!inputDistinct.contains("new1"));
    checkArgument(!inputDistinct.contains("new2"));

    final Set<?> asSet =
        Instanciator.invokeProducing(cycling, Set.class, "asSetOfColors").orThrow();
    final ImmutableSet<String> suppl = ImmutableSet.of("new1, new2");
    addColors(cycling, suppl);
    final ImmutableSet<String> expectedContent =
        ImmutableSet.<String>builder().addAll(inputDistinct).addAll(suppl).build();
    final Iterator<?> it = asSet.iterator();
    final ImmutableSet<?> asSetContent =
        ImmutableSet.copyOf(Iterators.limit(it, expectedContent.size() + 1));
    return !asSet.isEmpty() && expectedContent.size() == asSet.size()
        && expectedContent.equals(asSetContent);
  }

  private boolean snapshots(ImmutableSet<String> inputDistinct, Iterable<?> cycling)
      throws Throwable {
    checkArgument(!inputDistinct.isEmpty());

    final Set<?> snap = Instanciator.invokeProducing(cycling, Set.class, "snapshot").orThrow();
    final Iterator<?> it = snap.iterator();
    final ImmutableList<?> asSetContent =
        ImmutableList.copyOf(Iterators.limit(it, inputDistinct.size() + 1));
    return !snap.isEmpty() && inputDistinct.size() == snap.size()
        && ImmutableList.copyOf(snap).equals(asSetContent);
  }

  @SuppressWarnings("unused")
  private Mark mark(Throwable problem, boolean success) {
    final Mark mark;
    if (success) {
      mark = Mark.one();
    } else if (problem == null) {
      mark = Mark.zero();
    } else {
      mark = Mark.zero(problem.getMessage());
    }
    return mark;
  }

  private MarksTree mark(TryCatchAll<Boolean> attempt) {
    return attempt.map(s -> Mark.binary(s), e -> causeToMark(e));
  }

  @Override
  /**
   * For up to three colors without asSetOfColors() and equality: 7pts. More than three colors
   * without asSetOfColors() and equality: 6pts. More than three colors, asSetOfColors(): 4 pts.
   * More than three colors, equality: 3 pts.
   */
  public GradeAggregator getCodeAggregator() {
    final ImmutableMap.Builder<Criterion, Double> builder = ImmutableMap.builder();
    builder.put(C0, 0.25d);
    builder.put(CYCLE_ONE, 0.75d);
    builder.put(CYCLE_THREE, 1.25d);
    builder.put(SIZE_ONE, 0.75d);
    builder.put(SIZE_THREE, 0.75d);
    builder.put(DUPL_ONE, 0.75d);
    builder.put(DUPL_THREE, 0.75d);
    builder.put(SNAPSHOTS_ONE, 0.75d);
    builder.put(SNAPSHOTS_THREE, 0.75d);

    builder.put(CYCLE_MANY, 1.5d);
    builder.put(SIZE_MANY, 1.5d);
    builder.put(DUPL_MANY, 1.5d);
    builder.put(SNAPSHOTS_MANY, 1.5d);

    builder.put(AS_SET_ONE, 1d);
    builder.put(AS_SET_THREE, 1d);
    builder.put(AS_SET_MANY, 2d);
    builder.put(EQUALS, 3d);
    return GradeAggregator.staticAggregator(builder.build(), ImmutableMap.of());
  }

  public void close() {
    executors.shutdownNow();
  }
}
