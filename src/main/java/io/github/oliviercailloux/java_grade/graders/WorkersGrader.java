package io.github.oliviercailloux.java_grade.graders;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
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
import io.github.oliviercailloux.grade.utils.LogCaptor;
import io.github.oliviercailloux.jaris.exceptions.TryCatchAll;
import io.github.oliviercailloux.java_grade.bytecode.Instanciator;
import io.github.oliviercailloux.java_grade.utils.Summarizer;
import io.github.oliviercailloux.workers.Person;
import io.github.oliviercailloux.workers.Workers;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WorkersGrader {

  @SuppressWarnings("unused")
  private static final Logger LOGGER = LoggerFactory.getLogger(WorkersGrader.class);
  public static final String PREFIX = "workers";
  public static final ZonedDateTime DEADLINE_ONE = ZonedDateTime.parse("2021-05-05T14:48:00+02:00[Europe/Paris]");

  public static final ZonedDateTime DEADLINE = ZonedDateTime.parse("2021-06-21T00:00:00+02:00[Europe/Paris]");

  public static void main(String[] args) throws Exception {
    final RepositoryFetcher fetcher = RepositoryFetcher.withPrefix(PREFIX);
//        .setRepositoriesFilter(r -> r.getUsername().equals("EwenMarguet"));
    final GitGeneralGrader grader = GitGeneralGrader
        .using(fetcher,
            DeadlineGrader.usingInstantiatorGrader(WorkersGrader::grade, DEADLINE).setPenalizer(
                DeadlineGrader.LinearPenalizer.proportionalToLateness(Duration.ofSeconds(300))))
        .setExcludeCommitsByAuthors(ImmutableSet.of("Olivier Cailloux")).setExcludeCommitsByGitHub(true);
    grader.grade();
    // final Path src = Path.of("/tmp/coffee-…/.git/");
    // final GitFileFileSystem gitFs =
    // GitFileSystemProvider.getInstance().newFileSystemFromGitDir(src);

    Summarizer.create().setPrefix(PREFIX).setDissolveCriteria(ImmutableSet.of(Criterion.given("Warnings")))
//    .setPatched()
        // .restrictTo(ImmutableSet.of(GitHubUsername.given(""),
        // GitHubUsername.given("")))
        .summarize();
  }

  public static IGrade grade(Instanciator instanciator) {
    return new WorkersGrader(instanciator).grade();
  }

  private final Instanciator instanciator;
  private final ExecutorService executors;

  private WorkersGrader(Instanciator instanciator) {
    this.instanciator = checkNotNull(instanciator);
    executors = Executors.newCachedThreadPool();
  }

  public IGrade grade() {
    final ImmutableSet.Builder<CriterionGradeWeight> gradeBuilder = ImmutableSet.builder();

    try (LogCaptor capt = LogCaptor.redirecting("io.github.oliviercailloux.workers")) {
      LOGGER.info("Grading");

      gradeBuilder.add(CriterionGradeWeight.from(Criterion.given("Add"), add(), 4.5d));
      gradeBuilder.add(CriterionGradeWeight.from(Criterion.given("Log"), log(capt), 3.0d));
      gradeBuilder.add(CriterionGradeWeight.from(Criterion.given("Set"), set(), 4.5d));
      gradeBuilder.add(CriterionGradeWeight.from(Criterion.given("To string"), toStringGrade(), 3.0d));
      gradeBuilder.add(CriterionGradeWeight.from(Criterion.given("As teams"), asTeams(), 4.5d));
    }
    executors.shutdownNow();

    final WeightingGrade original = WeightingGrade.from(gradeBuilder.build()
//        , "Using an ordered weighted average with weights from 1 to 24."
    );
    return original;

//    final DoubleStream streamIncr = IntStream.range(1, 9).map(i -> i * 6).asDoubleStream();
//    final ImmutableList<Double> increasingWeights = DoubleStream
//        .concat(DoubleStream.of(2d, 2d, 2d, 3d, 3d), streamIncr).boxed()
//        .collect(ImmutableList.toImmutableList());
//
//    return GradeUtils.toOwa(original, increasingWeights);
  }

  private TryCatchAll<Workers> getWorkers() {
    final TryCatchAll<Workers> tryTarget = TryCatchAll
        .get(() -> instanciator.getInstanceOrThrow(Workers.class, "empty", ImmutableList.of()))
        .or(() -> instanciator.getInstanceOrThrow(Workers.class), (e1, e2) -> e1);
    return tryTarget.andApply(
        target -> SimpleTimeLimiter.create(executors).newProxy(target, Workers.class, Duration.ofSeconds(5)));
  }

  private TryCatchAll<Workers> getWorkersWith(Person... persons) {
    return getWorkersWith(ImmutableList.copyOf(persons));
  }

  private TryCatchAll<Workers> getWorkersWith(Collection<Person> persons) {
    TryCatchAll<Workers> w = getWorkers();
    for (Person person : persons) {
      w = w.andConsume(w0 -> w0.add(person));
    }
    return w;
  }

  @SuppressWarnings("unused")
  private <K> Mark compare(List<K> expected, List<TryCatchAll<K>> actual) {
    final int s = expected.size();
    checkArgument(s == actual.size());
    final ImmutableList.Builder<Mark> builder = ImmutableList.builder();
    for (int i = 0; i < s; ++i) {
      final K exp = expected.get(i);
      final TryCatchAll<K> act = actual.get(i);
      final Mark conform = act.map(k -> Mark.binary(Objects.equals(k, exp)), WorkersGrader::exc);
      builder.add(conform);
    }
    final ImmutableList<Mark> compareMarks = builder.build();
    return firstBadOrSuccess(compareMarks);
  }

  private Mark compare(List<Optional<Person>> expected, TryCatchAll<Workers> workers) {
    int i = 0;
    for (Optional<Person> personOpt : expected) {
      final int finalI = i;
      final TryCatchAll<Optional<Person>> p = workers.andApply(w -> w.get(finalI));
      final TryCatchAll<Boolean> eq = p.andApply(o -> Objects.equals(o, personOpt));
      final Mark conform = eq.map(
          b -> Mark.binary(b, "", "Differs at pos " + finalI + ", expected " + personOpt + ", got " + p),
          WorkersGrader::exc);
      if (conform.getPoints() != 1d) {
        return conform;
      }
      ++i;
    }
    final int finalI = i;
    final TryCatchAll<Optional<Person>> last = workers.andApply(w -> w.get(finalI));
    return Mark.binary(last.equals(TryCatchAll.success(Optional.empty())), "", "Ended with unexpected " + last);
  }

  private Mark firstBadOrSuccess(Collection<Mark> marks) {
    final Optional<Mark> bad = marks.stream().filter(m -> m.getPoints() != 1d).findFirst();
    return bad.orElse(Mark.one());
  }

  @SuppressWarnings("unused")
  private ImmutableList<TryCatchAll<Optional<Person>>> getFirsts(TryCatchAll<Workers> workers, int nb) {
    final ImmutableList.Builder<TryCatchAll<Optional<Person>>> builder = ImmutableList.builder();
    for (int i = 0; i < nb; ++i) {
      final int finalI = i;
      builder.add(workers.andApply(w -> w.get(finalI)));
    }
    return builder.build();
  }

  private WeightingGrade add() {
    final Person p0 = Person.named("khhhayy");
    final Person p1 = Person.named("dfdkhhhayy");

    final ImmutableSet.Builder<CriterionGradeWeight> gradeBuilder = ImmutableSet.builder();
    {
      final TryCatchAll<Workers> workers = getWorkers();
      final Mark mark = compare(Stream.<Optional<Person>>generate(Optional::empty).limit(3)
          .collect(ImmutableList.toImmutableList()), workers);
      gradeBuilder.add(CriterionGradeWeight.from(Criterion.given("Empty"), mark, 1d));
    }
    {
      final TryCatchAll<Workers> workers = getWorkersWith(p0);
      final Mark mark = compare(ImmutableList.of(Optional.of(p0), Optional.empty(), Optional.empty()), workers);
      gradeBuilder.add(CriterionGradeWeight.from(Criterion.given("One"), mark, 1.5d));
    }
    {
      final TryCatchAll<Workers> workers = getWorkersWith(p0, p1);
      final Mark mark = compare(ImmutableList.of(Optional.of(p0), Optional.of(p1), Optional.empty()), workers);
      gradeBuilder.add(CriterionGradeWeight.from(Criterion.given("Two"), mark, 1d));
    }
    {
      final TryCatchAll<Workers> workers = getWorkersWith(p0, p0);
      final Mark mark = compare(ImmutableList.of(Optional.of(p0), Optional.of(p0), Optional.empty()), workers);
      gradeBuilder.add(CriterionGradeWeight.from(Criterion.given("Two dupl"), mark, 1d));
    }

    return WeightingGrade.from(gradeBuilder.build());
  }

  private IGrade log(LogCaptor logCaptor) {
    final int before = logCaptor.getEvents().size();
    getWorkersWith(Person.named("just logging"));
    final int after = logCaptor.getEvents().size();
    return Mark.binary(after > before);
  }

  private WeightingGrade set() {
    final Person pk = Person.named("khhhayy");

    final ImmutableSet.Builder<CriterionGradeWeight> gradeBuilder = ImmutableSet.builder();

    {
      final TryCatchAll<Workers> workers = getWorkers();
      final TryCatchAll<Workers> workersPos = workers.andConsume(w -> w.setPosition(pk, 0));
      final Mark mark = workersPos.map(w -> Mark.zero("Did not throw"), e -> Mark.binary(workers.isSuccess()
          && ((e instanceof IllegalArgumentException) || (e instanceof IllegalStateException))));
      gradeBuilder.add(CriterionGradeWeight.from(Criterion.given("Trying to set empty instance"), mark, 1d));
    }
    {
      final TryCatchAll<Workers> workers = getWorkersWith(pk).andConsume(w -> w.setPosition(pk, 0));
      final Mark mark = compare(ImmutableList.of(Optional.of(pk), Optional.empty(), Optional.empty()), workers);
      gradeBuilder.add(CriterionGradeWeight.from(Criterion.given("Set unique person"), mark, 1d));
    }
    {
      final ImmutableList<Person> orig = IntStream.range(0, 100).mapToObj(i -> Person.named("P" + i))
          .collect(ImmutableList.toImmutableList());
      final TryCatchAll<Workers> workers = getWorkersWith(orig)
          .andConsume(w -> w.setPosition(Person.named("P" + 40), 90));

      final ImmutableList<Optional<Person>> reordered = Streams
          .concat(IntStream.range(0, 40).<Person>mapToObj(i -> Person.named("P" + i)),
              IntStream.range(41, 91).<Person>mapToObj(i -> Person.named("P" + i)),
              Stream.of(Person.named("P" + 40)),
              IntStream.range(91, 100).<Person>mapToObj(i -> Person.named("P" + i)))
          .map(Optional::of).collect(ImmutableList.toImmutableList());

      final Mark mark = compare(reordered, workers);

      gradeBuilder.add(CriterionGradeWeight.from(Criterion.given("Set with many persons"), mark, 1d));
    }

    return WeightingGrade.from(gradeBuilder.build());
  }

  private WeightingGrade asTeams() {
    final Person pk = Person.named("khhhayy");
    final Person pd = Person.named("dkhhhayddy");

    final ImmutableSet.Builder<CriterionGradeWeight> gradeBuilder = ImmutableSet.builder();

    {
      final TryCatchAll<Set<List<Person>>> workers = getWorkersWith(pk).andApply(w -> w.getAsTeamsOfSize(1));
      final ImmutableSet<ImmutableList<Person>> exp = ImmutableSet.of(ImmutableList.of(pk));
      final Mark mark = workers.andApply(s -> Mark.binary(Objects.equals(s, exp))).orMapCause(WorkersGrader::exc);
      gradeBuilder.add(CriterionGradeWeight.from(Criterion.given("Get unique team of 1"), mark, 1d));
    }
    {
      final TryCatchAll<Set<List<Person>>> teams = getWorkersWith(pk, pd).andApply(w -> w.getAsTeamsOfSize(1));
      final ImmutableSet<ImmutableList<Person>> exp = ImmutableSet.of(ImmutableList.of(pk), ImmutableList.of(pd));
      final Mark mark = teams.andApply(s -> Mark.binary(Objects.equals(s, exp))).orMapCause(WorkersGrader::exc);
      gradeBuilder.add(CriterionGradeWeight.from(Criterion.given("Get teams of 1"), mark, 1d));
    }
    {
      final ImmutableList<Person> many = Streams
          .concat(IntStream.range(12, 100).<Person>mapToObj(WorkersGrader::personWithId),
              IntStream.range(1, 93).map(i -> 100 - i).<Person>mapToObj(WorkersGrader::personWithId))
          .collect(ImmutableList.toImmutableList());
      final ImmutableSet<List<Person>> exp = ImmutableSet.copyOf(Lists.partition(many, 12));
      final TryCatchAll<Workers> workers = getWorkersWith(many);
      LOGGER.info("Teams orig: {}, teams to: {}, workers: {}.", many, exp, workers);
      final TryCatchAll<Set<List<Person>>> teams = workers.andApply(w -> w.getAsTeamsOfSize(12));
      final Mark mark = teams.andApply(s -> Mark.binary(Objects.equals(s, exp))).orMapCause(WorkersGrader::exc);
      gradeBuilder.add(CriterionGradeWeight.from(Criterion.given("Get teams of 12"), mark, 1.5d));
    }
    {
      final ImmutableList<Person> many = IntStream.range(0, 101).<Person>mapToObj(WorkersGrader::personWithId)
          .collect(ImmutableList.toImmutableList());
      final TryCatchAll<Workers> workers = getWorkersWith(many);
      LOGGER.info("Many: {}.", workers);
      final TryCatchAll<Set<List<Person>>> teams = workers.andApply(w -> w.getAsTeamsOfSize(100));
      LOGGER.info("Teams: {}.", teams);
      final Mark mark = teams.map(s -> Mark.zero("Did not throw"), e -> Mark.binary(workers.isSuccess()
          && ((e instanceof IllegalArgumentException) || (e instanceof IllegalStateException))));
      LOGGER.info("Mark: {}.", mark);
      gradeBuilder.add(CriterionGradeWeight.from(Criterion.given("Wrong argument"), mark, 1d));
    }

    return WeightingGrade.from(gradeBuilder.build());
  }

  private IGrade toStringGrade() {
    final TryCatchAll<Workers> workers = getWorkersWith(personWithId(21), personWithId(24));
    final TryCatchAll<String> str = workers.andApply(Workers::toString);
    final Mark mark = str.map(s -> Mark.binary(s.equals("A list of 2 workers"), "", "Got: " + s),
        WorkersGrader::exc);

    return mark;
  }

  private static Person personWithId(int id) {
    return Person.named("Pesn " + id);
  }

  private static Mark exc(Throwable t) {
    return Mark.zero("Got exception: " + t);
  }

}
