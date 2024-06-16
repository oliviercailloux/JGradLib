package io.github.oliviercailloux.javagrade.graders;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterators;
import com.google.common.collect.Maps;
import com.google.common.collect.Streams;
import com.google.common.util.concurrent.SimpleTimeLimiter;
import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.CriterionGradeWeight;
import io.github.oliviercailloux.grade.DeadlineGrader;
import io.github.oliviercailloux.grade.GitGeneralGrader;
import io.github.oliviercailloux.grade.IGrade;
import io.github.oliviercailloux.grade.RepositoryFetcher;
import io.github.oliviercailloux.grade.WeightingGrade;
import io.github.oliviercailloux.grade.old.Mark;
import io.github.oliviercailloux.jaris.exceptions.TryCatchAll;
import io.github.oliviercailloux.jaris.exceptions.TryCatchAllVoid;
import io.github.oliviercailloux.jaris.throwing.TSupplier;
import io.github.oliviercailloux.javagrade.bytecode.Instanciator;
import io.github.oliviercailloux.javagrade.utils.StdOutErrLogger;
import io.github.oliviercailloux.javagrade.utils.Summarizer;
import io.github.oliviercailloux.personsmanager.Person;
import io.github.oliviercailloux.personsmanager.PersonsManager;
import io.github.oliviercailloux.personsmanager.RedundancyCounter;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PersonsManagerGrader {

  public static <T, X extends Exception> Supplier<Optional<T>> wrap(TSupplier<T, X> supplier) {
    return () -> {
      try {
        return Optional.of(supplier.get());
      } catch (@SuppressWarnings("unused") Exception e) {
        return Optional.empty();
      }
    };
  }

  private static class MyInputStream extends InputStream {
    private final ByteArrayInputStream delegate;
    private boolean wasClosed;

    private MyInputStream(String content) {
      delegate = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
      wasClosed = false;
    }

    public boolean wasClosed() {
      return wasClosed;
    }

    @Override
    public int read() throws IOException {
      return delegate.read();
    }

    @Override
    public void close() throws IOException {
      wasClosed = true;
      delegate.close();
    }
  }

  @SuppressWarnings("unused")
  private static final Logger LOGGER = LoggerFactory.getLogger(PersonsManagerGrader.class);
  public static final String PREFIX = "persons-manager";
  public static final ZonedDateTime DEADLINE =
      ZonedDateTime.parse("2021-03-31T23:59:59+01:00[Europe/Paris]");

  public static void main(String[] args) throws Exception {
    /* Nb should give 0.5/20 pts for user.grade. */
    final RepositoryFetcher fetcher = RepositoryFetcher.withPrefix(PREFIX)
        .setRepositoriesFilter(r -> r.getUsername().equals("Student"));
    final GitGeneralGrader grader =
        GitGeneralGrader
            .using(fetcher,
                DeadlineGrader.usingInstantiatorGrader(PersonsManagerGrader::grade, DEADLINE)
                    .setPenalizer(DeadlineGrader.LinearPenalizer
                        .proportionalToLateness(Duration.ofSeconds(300))))
            .setExcludeCommitsByAuthors(
                ImmutableSet.of("Olivier Cailloux", "github-classroom[bot]"));
    grader.grade();

    Summarizer.create().setPrefix(PREFIX)
        .setDissolveCriteria(ImmutableSet.of(Criterion.given("Warnings"))).summarize();
  }

  public static IGrade grade(Instanciator instanciator) {
    return new PersonsManagerGrader(instanciator).grade();
  }

  private final Instanciator instanciator;
  private final ExecutorService executors;

  private PersonsManagerGrader(Instanciator instanciator) {
    this.instanciator = checkNotNull(instanciator);
    executors = Executors.newCachedThreadPool();
  }

  public IGrade grade() {
    final ImmutableSet.Builder<CriterionGradeWeight> gradeBuilder = ImmutableSet.builder();

    /* TODO redirect logger seems to also redirect info logs! */
    try (StdOutErrLogger redirecter = StdOutErrLogger.redirect()) {
      LOGGER.info("Grading");
      gradeBuilder.add(CriterionGradeWeight.from(Criterion.given("Size and static factories"),
          sizeAndStaticFactories(), 2.5d));
      gradeBuilder.add(CriterionGradeWeight.from(Criterion.given("Task force"), taskForce(), 2d));
      gradeBuilder.add(CriterionGradeWeight.from(Criterion.given("Contains"), contains(), 2d));
      gradeBuilder.add(
          CriterionGradeWeight.from(Criterion.given("Contains stream"), containsStream(), 2.5d));
      gradeBuilder.add(CriterionGradeWeight.from(Criterion.given("Map"), map(), 2d));
      gradeBuilder.add(CriterionGradeWeight.from(Criterion.given("Iterator"), iterator(), 2d));
      gradeBuilder.add(CriterionGradeWeight.from(Criterion.given("Id iterator"), idIterator(), 2d));
      gradeBuilder.add(CriterionGradeWeight.from(Criterion.given("Counter"), counter(), 2.5d));
      gradeBuilder
          .add(CriterionGradeWeight.from(Criterion.given("To string"), toStringGrade(), 2d));
    }
    executors.shutdownNow();

    return WeightingGrade.from(gradeBuilder.build());
  }

  private TryCatchAll<PersonsManager> getManager() {
    final TryCatchAll<PersonsManager> tryTarget = TryCatchAll.get(
        () -> instanciator.getInstanceOrThrow(PersonsManager.class, "empty", ImmutableList.of()))
        .or(() -> instanciator.getInstanceOrThrow(PersonsManager.class), (e1, e2) -> e1);
    return tryTarget.andApply(target -> SimpleTimeLimiter.create(executors).newProxy(target,
        PersonsManager.class, Duration.ofSeconds(5)));
  }

  private TryCatchAll<PersonsManager> getManagerGiven(Iterable<Person> arg) {
    return TryCatchAll.get(() -> instanciator.getInstanceOrThrow(PersonsManager.class, "given",
        ImmutableList.of(arg)));
  }

  private TryCatchAll<PersonsManager> getManagerWith(List<Person> persons) {
    return getManager().andConsume(m -> m.setPersons(persons));
  }

  TryCatchAll<PersonsManager> getManagerWithTaskForce(Person... persons) {
    return getManager().andConsume(m -> m.setTaskForce(persons));
  }

  private WeightingGrade sizeAndStaticFactories() {
    final ImmutableSet.Builder<CriterionGradeWeight> gradeBuilder = ImmutableSet.builder();
    {
      final TryCatchAll<PersonsManager> manager = TryCatchAll.get(
          () -> instanciator.getInstanceOrThrow(PersonsManager.class, "empty", ImmutableList.of()));
      final TryCatchAll<Integer> size = manager.andApply(PersonsManager::size);
      final Mark mark = size.map(s -> Mark.binary(s == 0), PersonsManagerGrader::exc);
      gradeBuilder.add(CriterionGradeWeight.from(Criterion.given("Empty"), mark, 1d));
    }

    {
      final TryCatchAll<Integer> sizeList =
          getManagerGiven(ImmutableList.of()).andApply(PersonsManager::size);
      final TryCatchAll<Integer> sizeSet =
          getManagerGiven(ImmutableMultiset.of()).andApply(PersonsManager::size);
      final Mark mark = sizeList.and(sizeSet, (sL, sS) -> Mark.binary(sL == 0 && sS == 0))
          .orMapCause(PersonsManagerGrader::exc);
      gradeBuilder.add(CriterionGradeWeight.from(Criterion.given("Given empty coll"), mark, 0.5d));
    }

    {
      final ImmutableList<Person> persons = IntStream.range(1000, 1100).boxed()
          .map(PersonsManagerGrader::personWithId).collect(ImmutableList.toImmutableList());
      final TryCatchAll<Integer> size =
          getManagerGiven(ImmutableMultiset.copyOf(persons)).andApply(PersonsManager::size);
      final Mark mark = size.map(s -> Mark.binary(s == 100), PersonsManagerGrader::exc);
      gradeBuilder.add(
          CriterionGradeWeight.from(Criterion.given("Given 100 distinct persons"), mark, 0.5d));
    }

    {
      final TryCatchAll<PersonsManager> manager = getManagerWith(ImmutableList.of());
      final TryCatchAll<Integer> size = manager.andApply(PersonsManager::size);
      final Mark mark = size.map(s -> Mark.binary(s == 0), PersonsManagerGrader::exc);
      gradeBuilder.add(CriterionGradeWeight.from(Criterion.given("Set to empty"), mark, 0.5d));
    }

    {
      final ImmutableList<Person> persons = IntStream.range(1000, 1100).boxed()
          .map(i -> Person.given(i, "The person " + i)).collect(ImmutableList.toImmutableList());
      final TryCatchAll<PersonsManager> manager = getManagerWith(persons);
      final TryCatchAll<Integer> size = manager.andApply(PersonsManager::size);
      final Mark mark = size.map(s -> Mark.binary(s == 100), PersonsManagerGrader::exc);
      gradeBuilder.add(CriterionGradeWeight.from(Criterion.given("Set 100 persons"), mark, 1d));
    }

    {
      final ImmutableList<Person> persons =
          Streams
              .concat(
                  IntStream.range(1000, 1100).boxed()
                      .map(i -> Person.given(455, "The person " + i)),
                  IntStream.range(1200, 1300).boxed().map(i -> Person.given(81, "The person " + i)))
              .collect(ImmutableList.toImmutableList());
      final TryCatchAll<PersonsManager> manager = getManagerWith(persons);
      final TryCatchAll<Integer> size = manager.andApply(PersonsManager::size);
      final Mark mark = size.map(s -> Mark.binary(s == 200), PersonsManagerGrader::exc);
      gradeBuilder.add(CriterionGradeWeight
          .from(Criterion.given("Set 200 persons with some duplicate ids"), mark, 1d));
    }

    {
      final ImmutableList<Person> persons = Streams
          .concat(
              IntStream.range(1000, 1100).boxed().map(i -> Person.given(455, "The person " + i)),
              IntStream.range(1000, 1100).boxed().map(i -> Person.given(455, "The person " + i)),
              IntStream.range(1000, 1100).boxed().map(i -> Person.given(455, "The person " + i)))
          .collect(ImmutableList.toImmutableList());
      final TryCatchAll<PersonsManager> manager = getManagerWith(persons);
      final TryCatchAll<Integer> size = manager.andApply(PersonsManager::size);
      final Mark mark = size.map(s -> Mark.binary(s == 100), PersonsManagerGrader::exc);
      gradeBuilder.add(CriterionGradeWeight.from(
          Criterion.given("Set 100 unique persons with some duplicates and duplicate ids"), mark,
          0.5d));
    }
    return WeightingGrade.from(gradeBuilder.build());
  }

  private WeightingGrade taskForce() {
    final ImmutableSet.Builder<CriterionGradeWeight> gradeBuilder = ImmutableSet.builder();
    {
      final TryCatchAll<Mark> managerSetMarked = getManager().andApply(m -> TryCatchAllVoid
          .run(m::setTaskForce).map(() -> Mark.zero("Set succeeded but shouldn’t have."),
              t -> Mark.binary(t instanceof IllegalArgumentException, "",
                  "Incorrect exception when set: " + t)));
      final Mark mark = managerSetMarked.orMapCause(t1 -> Mark.zero("Got init exception: " + t1));
      // final TryCatchAll<Optional<Throwable>> managerSet = getManager()
      // .flatMap(m -> TryCatchAllVoid.run(m::setTaskForce).map(Optional::empty, Optional::of));
      // final Mark mark = managerSet.map(
      // o -> o.map(t -> Mark.binary(t instanceof IllegalArgumentException, "",
      // "Incorrect exception when set: " + t))
      // .orElse(Mark.zero("Set succeeded but shouldn’t have.")),
      // t1 -> Mark.zero("Got init exception: " + t1));

      // final Try<PersonsManager> managerSet = manager.and(m -> m.setTaskForce());
      // final Mark setMark = managerSet.map(m2 -> Mark.zero("Set succeeded."),
      // t2 -> Mark.binary(t2 instanceof IllegalArgumentException));
      // final Mark mark = manager.map(m1 -> setMark, t1 -> Mark.zero("Got init exception: " + t1));

      // final boolean setFailed = manager.isSuccess() && managerSet.isFailure();
      // final boolean rightExc = setFailed && isIllegalArgumentFailure(managerSet);
      // final String comment = setFailed ? (rightExc ? "" : "Got exception: " +
      // managerSet.getCause()) : "";
      // final Mark mark = Mark.binary(setFailed && rightExc, "", comment);
      gradeBuilder.add(CriterionGradeWeight.from(
          Criterion.given("Set empty task force, thrown IllegalArgumentException"), mark, 1d));
    }

    {
      final TryCatchAll<PersonsManager> manager = getManager();
      final TryCatchAll<Optional<Throwable>> managerSet = manager.andApply(m -> TryCatchAllVoid
          .run(() -> m.setTaskForce(personWithId(416), personWithId(417), personWithId(418)))
          .map(Optional::empty, Optional::of));
      final Mark mark = managerSet.map(
          o -> o
              .map(t -> Mark.binary(t instanceof IllegalArgumentException, "",
                  "Incorrect exception when set: " + t))
              .orElse(Mark.zero("Set succeeded but shouldn’t have.")),
          t1 -> Mark.zero("Got init exception: " + t1));
      gradeBuilder.add(CriterionGradeWeight.from(
          Criterion.given("Set triple task force, thrown IllegalArgumentException"), mark, 1d));
    }

    {
      final TryCatchAll<PersonsManager> manager = getManager();
      final TryCatchAll<Integer> sizeStart = manager.andApply(PersonsManager::size);
      final TryCatchAll<PersonsManager> managerSet =
          manager.andConsume(m -> m.setTaskForce(personWithId(416)));
      final TryCatchAll<Integer> size = managerSet.andApply(PersonsManager::size);
      final Mark mark = sizeStart.and(size, (sS, s) -> Mark.binary(sS == 0 && s == 1))
          .orMapCause(PersonsManagerGrader::exc);
      gradeBuilder.add(
          CriterionGradeWeight.from(Criterion.given("Set singleton task force, size 1"), mark, 1d));
    }

    {
      final TryCatchAll<PersonsManager> manager = getManager();
      final TryCatchAll<Integer> sizeStart = manager.andApply(PersonsManager::size);
      final TryCatchAll<PersonsManager> managerSet =
          manager.andConsume(m -> m.setTaskForce(personWithId(416), personWithId(41)));
      final TryCatchAll<Integer> size = managerSet.andApply(PersonsManager::size);
      final Mark mark = sizeStart.and(size, (sS, s) -> Mark.binary(sS == 0 && s == 2))
          .orMapCause(PersonsManagerGrader::exc);
      gradeBuilder
          .add(CriterionGradeWeight.from(Criterion.given("Set dual task force, size 2"), mark, 1d));
    }
    return WeightingGrade.from(gradeBuilder.build());
  }

  private WeightingGrade contains() {
    final ImmutableSet.Builder<CriterionGradeWeight> gradeBuilder = ImmutableSet.builder();
    // {
    // final Try<PersonsManager> managerSet = getManagerWith(ImmutableList.of());
    // final Try<Boolean> containsEmptyName = managerSet.flatMap(m -> m.contains(""));
    // final Mark mark = containsEmptyName.map(b -> Mark.binary(!b), PersonsManagerGrader::exc);
    // gradeBuilder
    // .add(CriterionGradeWeight.from(Criterion.given("Set to empty, does not contain empty name"),
    // mark, 0.5d));
    // }

    {
      final ImmutableList<Person> persons = IntStream.range(1000, 1100).boxed()
          .map(i -> Person.given(i, "The person " + i)).collect(ImmutableList.toImmutableList());
      final TryCatchAll<PersonsManager> managerSet = getManagerWith(persons);
      final TryCatchAll<Boolean> containsRight100 =
          managerSet.andApply(m -> !m.contains("The person 100"));
      final TryCatchAll<Boolean> containsRight1000 =
          managerSet.andApply(m -> m.contains("The person 1000"));
      final TryCatchAll<Boolean> containsRight =
          containsRight100.and(containsRight1000, (r100, r1000) -> r100 && r1000);
      final Mark mark =
          containsRight.map(c -> Mark.binary(c, "", "Invalid contains"), PersonsManagerGrader::exc);
      gradeBuilder.add(CriterionGradeWeight.from(Criterion.given("Set 100 persons"), mark, 1d));
    }

    {
      final ImmutableList<Person> persons = Streams
          .concat(
              IntStream.range(1000, 1100).boxed().map(i -> Person.given(455, "The person " + i)),
              IntStream.range(1000, 1100).boxed().map(i -> Person.given(455, "The person " + i)),
              IntStream.range(1000, 1100).boxed().map(i -> Person.given(455, "The person " + i)))
          .collect(ImmutableList.toImmutableList());
      final TryCatchAll<PersonsManager> managerSet = getManagerWith(persons);
      final TryCatchAll<Boolean> containsRight100 =
          managerSet.andApply(m -> !m.contains("The person 100"));
      final TryCatchAll<Boolean> containsRight1000 =
          managerSet.andApply(m -> m.contains("The person 1000"));
      final TryCatchAll<Boolean> containsRight =
          containsRight100.and(containsRight1000, (r100, r1000) -> r100 && r1000);
      final Mark mark =
          containsRight.map(c -> Mark.binary(c, "", "Invalid contains"), PersonsManagerGrader::exc);
      gradeBuilder.add(CriterionGradeWeight.from(
          Criterion.given("Set 100 unique persons with some duplicates and duplicate ids"), mark,
          1d));
    }
    return WeightingGrade.from(gradeBuilder.build());
  }

  private WeightingGrade containsStream() {
    final ImmutableSet.Builder<CriterionGradeWeight> gradeBuilder = ImmutableSet.builder();
    {
      final ImmutableList<Person> persons = IntStream.range(1000, 1100).boxed()
          .map(i -> Person.given(i, "The person " + i)).collect(ImmutableList.toImmutableList());
      final TryCatchAll<PersonsManager> managerSet = getManagerWith(persons);
      final TryCatchAll<Boolean> containsRight1 =
          managerSet.andApply(m -> !m.contains(new MyInputStream("")));
      managerSet.andConsume(m -> m.setPersons(ImmutableList.of(Person.given(21, ""))));
      final TryCatchAll<Boolean> containsRight2 =
          managerSet.andApply(m -> m.contains(new MyInputStream("")));
      final TryCatchAll<Boolean> containsRight =
          containsRight1.and(containsRight2, (b1, b2) -> b1 && b2);
      final Mark mark =
          containsRight.map(c -> Mark.binary(c, "", "Invalid contains"), PersonsManagerGrader::exc);
      gradeBuilder
          .add(CriterionGradeWeight.from(Criterion.given("Contains stream empty name"), mark, 1d));
    }

    {
      final ImmutableList<Person> persons1 = Streams
          .concat(
              IntStream.range(1000, 1100).boxed().map(i -> Person.given(455, "The person " + i)),
              IntStream.range(1000, 1100).boxed().map(i -> Person.given(455, "The person " + i)),
              IntStream.range(1000, 1100).boxed().map(i -> Person.given(455, "The person " + i)))
          .collect(ImmutableList.toImmutableList());
      final TryCatchAll<PersonsManager> managerSet = getManagerWith(persons1);
      /** Thanks to https://www.babygaga.com/25-names-that-have-more-than-letters/. */
      @SuppressWarnings("resource")
      final MyInputStream stream1 = new MyInputStream("Asbjørn Nuñez");
      @SuppressWarnings("resource")
      final MyInputStream stream2 = new MyInputStream("Asbjørn Nuñez");
      final ImmutableList<Person> persons2 =
          Streams
              .concat(
                  IntStream.range(1000, 1100).boxed()
                      .map(i -> Person.given(455, "The person " + i)),
                  IntStream.range(1000, 1100).boxed().map(i -> Person.given(455, "Asbjørn Nuñez")),
                  IntStream.range(1000, 1100).boxed()
                      .map(i -> Person.given(455, "The person " + i)))
              .collect(ImmutableList.toImmutableList());
      final TryCatchAll<Boolean> containsRight = managerSet.andApply(m -> {
        final boolean contains1 = m.contains(stream1);
        m.setPersons(persons2);
        final boolean contains2 = m.contains(stream2);
        return !contains1 && contains2;
      });
      final Mark markContains =
          containsRight.map(c -> Mark.binary(c, "", "Invalid contains"), PersonsManagerGrader::exc);
      final Mark markAll =
          containsRight.map(c -> Mark.binary(c && !stream1.wasClosed() && !stream2.wasClosed()),
              PersonsManagerGrader::exc);
      gradeBuilder.add(CriterionGradeWeight.from(Criterion.given("Contains stream Asbjørn Nuñez"),
          WeightingGrade.from(ImmutableSet.of(
              CriterionGradeWeight.from(Criterion.given("Contains"), markContains, 0.75d),
              CriterionGradeWeight.from(Criterion.given("Contains and not closed"), markAll,
                  0.5d))),
          1d));
    }
    return WeightingGrade.from(gradeBuilder.build());
  }

  private WeightingGrade map() {
    final ImmutableSet.Builder<CriterionGradeWeight> gradeBuilder = ImmutableSet.builder();
    {
      // Unique names but one dupl id.
      final ImmutableList<Person> persons = Streams
          .concat(IntStream.range(1000, 1100).boxed().map(i -> Person.given(i, "The person " + i)),
              IntStream.range(1099, 1200).boxed().map(i -> Person.given(i, "The person " + i * 10)))
          .collect(ImmutableList.toImmutableList());
      final TryCatchAll<PersonsManager> managerSet = getManagerWith(persons);
      final TryCatchAll<Mark> managerSetMarked = managerSet.andApply(m -> TryCatchAllVoid
          .run(m::toMap).map(() -> Mark.zero("toMap succeeded but shouldn’t have."),
              t -> Mark.binary(
                  (t instanceof IllegalArgumentException) || (t instanceof IllegalStateException),
                  "", "Incorrect toMap exception: " + t)));
      final Mark mark = managerSetMarked.orMapCause(t1 -> Mark.zero("Got init exception: " + t1));
      gradeBuilder.add(CriterionGradeWeight
          .from(Criterion.given("Set a duplicate id, toMap throwing"), mark, 1d));
    }

    {
      final ImmutableList<Person> persons = IntStream.range(1000, 1100).boxed()
          .map(PersonsManagerGrader::personWithId).collect(ImmutableList.toImmutableList());
      final TryCatchAll<PersonsManager> managerSet = getManagerWith(persons);
      final TryCatchAll<Map<Integer, Person>> map = managerSet.andApply(m -> m.toMap());
      final TryCatchAll<Integer> mapSize = map.andApply(Map::size);
      final ImmutableMap<Integer, Person> mapPersons =
          Maps.toMap(IntStream.range(1000, 1100).boxed().collect(ImmutableSet.toImmutableSet()),
              PersonsManagerGrader::personWithId);
      final TryCatchAll<Boolean> mapEquals = map.andApply(m -> m.equals(mapPersons));
      final TryCatchAll<Map<Integer, Person>> withPut =
          map.andConsume(m -> m.put(1, personWithId(1)));
      final Mark markContent = Mark.binary(
          mapSize.equals(TryCatchAll.success(100)) && mapEquals.equals(TryCatchAll.success(true)));
      final Mark markImmutable = Mark.binary(map.isSuccess() && withPut.isFailure());
      gradeBuilder.add(CriterionGradeWeight.from(
          Criterion.given("Set 100 unique persons without duplicate ids, toMap"),
          WeightingGrade.proportional(Criterion.given("Expected map content"), markContent,
              Criterion.given("Immutable"), markImmutable),
          1d));
    }
    return WeightingGrade.from(gradeBuilder.build());
  }

  private WeightingGrade iterator() {
    final ImmutableSet.Builder<CriterionGradeWeight> gradeBuilder = ImmutableSet.builder();
    {
      final ImmutableList<Person> persons = IntStream.range(1000, 1100).boxed()
          .map(PersonsManagerGrader::personWithId).collect(ImmutableList.toImmutableList());
      final TryCatchAll<PersonsManager> managerSet = getManagerWith(persons);
      final TryCatchAll<Iterator<Person>> it = managerSet.andApply(PersonsManager::personIterator);
      final TryCatchAll<Iterator<Person>> itL = it.andApply(i -> Iterators.limit(i, 1000));
      final TryCatchAll<ImmutableMultiset<Person>> result = itL.andApply(ImmutableMultiset::copyOf);
      final ImmutableMultiset<Person> expected = ImmutableMultiset.copyOf(persons);
      final TryCatchAll<Boolean> resultRight = result.andApply(l -> l.equals(expected));
      final Mark mark = resultRight.map(b -> Mark.binary(b), PersonsManagerGrader::exc);
      gradeBuilder.add(CriterionGradeWeight
          .from(Criterion.given("Set 100 unique persons with no duplicate ids"), mark, 1d));
    }

    {
      final ImmutableList<Person> persons = Streams
          .concat(
              IntStream.range(1000, 1100).boxed().map(i -> Person.given(455, "The person " + i)),
              IntStream.range(1000, 1050).boxed().map(i -> Person.given(458, "The person " + i)),
              IntStream.range(1000, 1020).boxed().map(i -> Person.given(455, "The person " + i)))
          .collect(ImmutableList.toImmutableList());
      final TryCatchAll<PersonsManager> managerSet = getManagerWith(persons);
      final TryCatchAll<Iterator<Person>> it = managerSet.andApply(PersonsManager::personIterator);
      final TryCatchAll<Iterator<Person>> itL = it.andApply(i -> Iterators.limit(i, 1000));
      final TryCatchAll<ImmutableMultiset<Person>> result = itL.andApply(ImmutableMultiset::copyOf);
      final ImmutableMultiset<Person> expected =
          ImmutableMultiset.copyOf(ImmutableSet.copyOf(persons));
      final TryCatchAll<Boolean> resultRight = result.andApply(l -> l.equals(expected));
      final Mark mark = resultRight.map(b -> Mark.binary(b), PersonsManagerGrader::exc);
      gradeBuilder.add(CriterionGradeWeight.from(
          Criterion.given("Set 150 unique persons with some duplicates and duplicate ids"), mark,
          1d));
    }
    return WeightingGrade.from(gradeBuilder.build());
  }

  private WeightingGrade idIterator() {
    final ImmutableSet.Builder<CriterionGradeWeight> gradeBuilder = ImmutableSet.builder();
    {
      final ImmutableList<Person> persons =
          Streams.concat(IntStream.range(1000, 1020).boxed(), IntStream.range(1100, 1200).boxed())
              .map(PersonsManagerGrader::personWithId).collect(ImmutableList.toImmutableList());
      final TryCatchAll<PersonsManager> managerSet = getManagerWith(persons);
      final TryCatchAll<Iterator<Integer>> it = managerSet.andApply(PersonsManager::idIterator);
      final TryCatchAll<Iterator<Integer>> itL = it.andApply(i -> Iterators.limit(i, 1000));
      final TryCatchAll<ImmutableMultiset<Integer>> result =
          itL.andApply(ImmutableMultiset::copyOf);
      final ImmutableMultiset<Integer> expected =
          persons.stream().map(Person::getId).collect(ImmutableMultiset.toImmutableMultiset());
      final TryCatchAll<Boolean> resultRight = result.andApply(l -> l.equals(expected));
      final Mark mark = resultRight.map(b -> Mark.binary(b), PersonsManagerGrader::exc);
      gradeBuilder.add(CriterionGradeWeight
          .from(Criterion.given("Set 100 unique persons with no duplicate ids"), mark, 1d));
    }

    {
      final ImmutableList<Person> persons = Streams
          .concat(IntStream.range(1000, 1020).boxed(), IntStream.range(1100, 1199).boxed(),
              IntStream.range(1000, 1100).boxed())
          .map(PersonsManagerGrader::personWithId).collect(ImmutableList.toImmutableList());
      final TryCatchAll<PersonsManager> managerSet = getManagerWith(persons);
      final TryCatchAll<Iterator<Integer>> it = managerSet.andApply(PersonsManager::idIterator);
      final TryCatchAll<Iterator<Integer>> itL = it.andApply(i -> Iterators.limit(i, 1000));
      final TryCatchAll<ImmutableMultiset<Integer>> result =
          itL.andApply(ImmutableMultiset::copyOf);
      final ImmutableMultiset<Integer> expected = persons.stream().distinct().map(Person::getId)
          .collect(ImmutableMultiset.toImmutableMultiset());
      final TryCatchAll<Boolean> resultRight = result.andApply(l -> l.equals(expected));
      final Mark mark = resultRight.map(b -> Mark.binary(b), PersonsManagerGrader::exc);
      gradeBuilder.add(CriterionGradeWeight
          .from(Criterion.given("Set persons with some duplicates and duplicate ids"), mark, 1d));
    }
    return WeightingGrade.from(gradeBuilder.build());
  }

  private WeightingGrade counter() {
    final ImmutableSet.Builder<CriterionGradeWeight> gradeBuilder = ImmutableSet.builder();
    final TryCatchAll<PersonsManager> manager = getManagerWith(ImmutableList.of());
    final TryCatchAll<RedundancyCounter> counter =
        manager.andApply(PersonsManager::getRedundancyCounter);
    {
      final TryCatchAll<Integer> redundant =
          counter.andApply(RedundancyCounter::getRedundancyCount);
      final TryCatchAll<Integer> unique = counter.andApply(RedundancyCounter::getUniqueCount);
      final Mark mark = Mark.binary(
          redundant.equals(TryCatchAll.success(0)) && unique.equals(TryCatchAll.success(0)));
      gradeBuilder.add(
          CriterionGradeWeight.from(Criterion.given("Start with 0 redundancy count"), mark, 0.5d));
    }
    manager.andConsume(m -> m.setPersons(ImmutableList.of(personWithId(2))));
    {
      final TryCatchAll<Integer> redundant =
          counter.andApply(RedundancyCounter::getRedundancyCount);
      final TryCatchAll<Integer> unique = counter.andApply(RedundancyCounter::getUniqueCount);
      final Mark mark = Mark.binary(
          redundant.equals(TryCatchAll.success(0)) && unique.equals(TryCatchAll.success(1)));
      gradeBuilder
          .add(CriterionGradeWeight.from(Criterion.given("Switch to 1 unique count"), mark, 1d));
    }
    final ImmutableList<Person> persons = Streams
        .concat(IntStream.range(1000, 1100).boxed().map(i -> Person.given(458, "The person " + i)),
            IntStream.range(1000, 1020).boxed().map(i -> Person.given(455, "The person " + i)),
            IntStream.range(1050, 1060).boxed().map(i -> Person.given(458, "The person " + i)))
        .collect(ImmutableList.toImmutableList());
    manager.andConsume(m -> m.setPersons(persons));
    {
      final TryCatchAll<Integer> redundant =
          counter.andApply(RedundancyCounter::getRedundancyCount);
      final TryCatchAll<Integer> unique = counter.andApply(RedundancyCounter::getUniqueCount);
      final Mark mark = Mark.binary(
          redundant.equals(TryCatchAll.success(10)) && unique.equals(TryCatchAll.success(120)));
      gradeBuilder
          .add(CriterionGradeWeight.from(Criterion.given("Switch to “many” count"), mark, 1d));
    }
    return WeightingGrade.from(gradeBuilder.build());
  }

  private WeightingGrade toStringGrade() {
    final ImmutableSet.Builder<CriterionGradeWeight> gradeBuilder = ImmutableSet.builder();

    final TryCatchAll<PersonsManager> managerSet =
        getManagerWith(ImmutableList.of(personWithId(21), personWithId(24)));
    final TryCatchAll<String> str = managerSet.andApply(PersonsManager::toString);
    final Mark mark =
        str.map(s -> Mark.binary(s.equals("PersonsManager with 2 entries"), "", "Got: " + s),
            PersonsManagerGrader::exc);
    gradeBuilder
        .add(CriterionGradeWeight.from(Criterion.given("Set to two unique persons"), mark, 1d));

    return WeightingGrade.from(gradeBuilder.build());
  }

  private static Mark exc(Throwable t) {
    return Mark.zero("Got exception: " + t);
  }

  private static Person personWithId(int id) {
    return Person.given(id, "The person " + id);
  }
}
