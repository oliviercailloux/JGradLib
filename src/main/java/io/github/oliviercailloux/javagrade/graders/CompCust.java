package io.github.oliviercailloux.javagrade.graders;

import static com.google.common.base.Verify.verify;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.UnmodifiableIterator;
import com.google.common.jimfs.Jimfs;
import com.google.common.math.DoubleMath;
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
import io.github.oliviercailloux.jaris.exceptions.TryCatchAllVoid;
import io.github.oliviercailloux.jaris.throwing.TSupplier;
import io.github.oliviercailloux.javagrade.bytecode.Instanciator;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CompCust implements CodeGrader<RuntimeException> {
  @SuppressWarnings("unused")
  private static final Logger LOGGER = LoggerFactory.getLogger(CompCust.class);

  private static final Instant INSTANT_EARLY = Instant.parse("1907-12-03T10:15:30.00Z");

  private static final Instant INSTANT_1 = Instant.parse("2007-12-03T10:15:30.00Z");

  private static final Instant INSTANT_2 = Instant.parse("2020-03-03T10:15:30.00Z");

  private static record OrderNoTime (int customerNbOrders, ImmutableList<String> simples) {
  }

  private static record OrderTime (int customerNbOrders, Instant time,
      ImmutableList<String> simples) {
  }

  public static final String PREFIX = "computer-customer";

  public static final ZonedDateTime DEADLINE_ORIGINAL =
      LocalDateTime.parse("2023-06-19T15:50:00").atZone(ZoneId.of("Europe/Paris"));
  public static final ZonedDateTime DEADLINE_SECOND_CHANCE =
      LocalDateTime.parse("2023-06-09T23:59:59").atZone(ZoneId.of("Europe/Paris"));

  public static final double USER_WEIGHT = 0d;

  public static Mark causeToMark(Throwable e) {
    final String messagePart =
        e.getMessage() == null ? "" : " with message ‘%s’".formatted(e.getMessage());
    return Mark.zero("Code failed with %s".formatted(e.getClass().getName()) + messagePart);
  }

  public static void main(String[] args) throws Exception {
    original();
    // second();
  }

  public static void original() throws IOException {
    final GitFileSystemWithHistoryFetcher fetcher = GitFileSystemWithHistoryFetcherByPrefix
        .getRetrievingByPrefixAndFilteringAndUsingCommitDates(PREFIX, "Student");
    // .getRetrievingByPrefix(PREFIX);
    final BatchGitHistoryGrader<RuntimeException> batchGrader =
        BatchGitHistoryGrader.given(() -> fetcher);

    final CompCust grader = new CompCust();
    final MavenCodeGrader<RuntimeException> m =
        MavenCodeGrader.penal(grader, UncheckedIOException::new, WarningsBehavior.DO_NOT_PENALIZE);

    batchGrader.getAndWriteGradesExp(DEADLINE_ORIGINAL, Duration.ofMinutes(30),
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
        .getRetrievingByPrefixAndFiltering(PREFIX, "Student");
    // .getRetrievingByPrefix(PREFIX);

    final BatchGitHistoryGrader<RuntimeException> batchGrader =
        BatchGitHistoryGrader.given(() -> fetcher);

    final CompCust grader = new CompCust();
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

  private static final String CL_COMP = "io.github.oliviercailloux.exercices.computer.Computer";

  private static final String CL_CUST = "io.github.oliviercailloux.exercices.customer.Customer";
  private static final Criterion COMP = Criterion.given("Computer");
  private static final Criterion ADD = Criterion.given("Add");
  private static final Criterion ONE_THEN_SPURIOUS = Criterion.given("One then spurious");
  private static final Criterion ONE_THEN_DIV = Criterion.given("One then div");
  private static final Criterion DUPL_THEN_MULT = Criterion.given("Dupl then mult");
  private static final Criterion LOGS = Criterion.given("Logs");
  private static final Criterion INVALID_OP = Criterion.given("Invalid op");
  private static final Criterion INVALID_STATE_OP = Criterion.given("Invalid state op");

  private static final Criterion CUST = Criterion.given("Customer");
  private static final Criterion READS_ONE_PRODUCT = Criterion.given("Reads one product");
  private static final Criterion READS_PRODUCTS = Criterion.given("Reads products");
  private static final Criterion EMPTY = Criterion.given("Empty");
  private static final Criterion USING_ONE = Criterion.given("Using one");
  private static final Criterion PLACING_ONE = Criterion.given("Placing one");
  private static final Criterion PLACING_TWO = Criterion.given("Placing two");
  private static final Criterion PLACING_MANY = Criterion.given("Placing many");
  private static final Criterion PLACING_EARLY = Criterion.given("Placing early");
  private static final Criterion ALL_WRITE_THROWS = Criterion.given("All write throws");
  private static final Criterion ALL_ITERATES = Criterion.given("Iterates in order");
  private static final Criterion ORDERED_WRITES_BACK = Criterion.given("Ordered writes back");

  private final ExecutorService executors;

  private final SimpleTimeLimiter limiter;

  public CompCust() {
    executors = Executors.newCachedThreadPool();
    limiter = SimpleTimeLimiter.create(executors);
  }

  @Override
  public MarksTree gradeCode(Instanciator instanciator) {
    final ImmutableMap.Builder<Criterion, MarksTree> builder = ImmutableMap.builder();
    builder.put(COMP, gradeComp(instanciator));
    builder.put(CUST, gradeCust(instanciator));

    return MarksTree.composite(builder.build());
  }

  public MarksTree gradeComp(Instanciator instanciator) {
    final ImmutableMap.Builder<Criterion, MarksTree> builder = ImmutableMap.builder();
    {
      final TryCatchAll<Object> comp =
          instanciator.invokeStatic(CL_COMP, Object.class, "instance", ImmutableList.of())
              .andApply(o -> o.orElseThrow());
      LOGGER.debug("Comp: {}.", comp);
      comp.andApply(c -> Instanciator.invoke(c, Void.class, "addOperand", 1d));
      final TryCatchAll<Object> comp2 =
          instanciator.invokeStatic(CL_COMP, Object.class, "instance", ImmutableList.of())
              .andApply(o -> o.orElseThrow());
      comp2.andApply(c -> Instanciator.invoke(c, Void.class, "addOperand", 3d));
      comp.andApply(c -> Instanciator.invoke(c, Void.class, "addOperand", 2d));
      comp2.andApply(c -> Instanciator.invoke(c, Void.class, "addOperand", 17d));
      final TryCatchAll<Double> obs = comp2
          .andApply(c -> Instanciator.invokeProducing(c, Double.class, "apply", "+").orThrow());
      final MarksTree mapped = markG(obs, o -> DoubleMath.fuzzyEquals(20d, o, 1e-6d));
      builder.put(ADD, mapped);
    }

    {
      final TryCatchAll<Object> comp =
          instanciator.invokeStatic(CL_COMP, Object.class, "oneOp", ImmutableList.of(30d))
              .andApply(o -> o.orElseThrow());
      comp.andApply(c -> Instanciator.invoke(c, Void.class, "addOperand", 1d));
      final TryCatchAll<Object> comp2 =
          instanciator.invokeStatic(CL_COMP, Object.class, "oneOp", ImmutableList.of(3d))
              .andApply(o -> o.orElseThrow());
      comp2.andApply(c -> Instanciator.invoke(c, Void.class, "addOperand", 3d));
      final TryCatchAll<Optional<Void>> secondAdd =
          comp2.andApply(c -> Instanciator.invoke(c, Void.class, "addOperand", 17d).orThrow());
      final MarksTree mapped = secondAdd.map(r -> Mark.zero("Unexpected answer to spurious add."),
          c -> Mark.binary(c instanceof IllegalStateException));
      builder.put(ONE_THEN_SPURIOUS, mapped);
    }

    {
      final TryCatchAll<Object> comp =
          instanciator.invokeStatic(CL_COMP, Object.class, "oneOp", ImmutableList.of(30d))
              .andApply(o -> o.orElseThrow());
      Instanciator.invoke(comp, Void.class, "addOperand", 1d);
      final TryCatchAll<Object> comp2 =
          instanciator.invokeStatic(CL_COMP, Object.class, "oneOp", ImmutableList.of(3d))
              .andApply(o -> o.orElseThrow());
      comp2.andApply(c -> Instanciator.invoke(c, Void.class, "addOperand", 2d));
      comp.andApply(c -> Instanciator.invoke(c, Void.class, "addOperand", 5d));
      final TryCatchAll<Double> obs = comp2
          .andApply(c -> Instanciator.invokeProducing(c, Double.class, "apply", "/").orThrow());
      final MarksTree mapped = markG(obs, o -> DoubleMath.fuzzyEquals(1.5d, o, 1e-6d));
      builder.put(ONE_THEN_DIV, mapped);
    }

    {
      final TryCatchAll<Object> comp =
          instanciator.invokeStatic(CL_COMP, Object.class, "duplOp", ImmutableList.of(3d))
              .andApply(o -> o.orElseThrow());
      instanciator.invokeStatic(CL_COMP, Object.class, "duplOp", ImmutableList.of(30d))
          .andApply(o -> o.orElseThrow());
      final TryCatchAll<Double> obs =
          comp.andApply(c -> Instanciator.invokeProducing(c, Double.class, "apply", "*").orThrow());
      final MarksTree mapped = markG(obs, o -> DoubleMath.fuzzyEquals(9d, o, 1e-6d));
      builder.put(DUPL_THEN_MULT, mapped);
    }

    {
      final ch.qos.logback.classic.Logger rootLogger =
          (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
      final ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
      listAppender.start();
      rootLogger.addAppender(listAppender);
      final TryCatchAll<Object> comp =
          instanciator.invokeStatic(CL_COMP, Object.class, "instance", ImmutableList.of())
              .andApply(o -> o.orElseThrow());
      comp.andApply(c -> Instanciator.invoke(c, Void.class, "addOperand", 1d));
      comp.andApply(c -> Instanciator.invoke(c, Void.class, "addOperand", 2d));
      final TryCatchAll<Double> obs =
          comp.andApply(c -> Instanciator.invokeProducing(c, Double.class, "apply", "+").orThrow());
      final List<ILoggingEvent> logsList = listAppender.list;
      final MarksTree mapped = markG(obs, o -> !logsList.isEmpty());
      builder.put(LOGS, mapped);
    }

    {
      final TryCatchAll<Object> comp =
          instanciator.invokeStatic(CL_COMP, Object.class, "instance", ImmutableList.of())
              .andApply(o -> o.orElseThrow());
      comp.andApply(c -> Instanciator.invoke(c, Void.class, "addOperand", 1d));
      comp.andApply(c -> Instanciator.invoke(c, Void.class, "addOperand", 2d));
      final TryCatchAll<Double> secondAdd = comp.andApply(
          c -> Instanciator.invokeProducing(c, Double.class, "apply", "non-op").orThrow());
      final MarksTree mapped = secondAdd.map(r -> Mark.zero("Unexpected answer to invalid op."),
          c -> Mark.binary(c instanceof IllegalArgumentException));
      builder.put(INVALID_OP, mapped);
    }

    {
      final TryCatchAll<Object> comp =
          instanciator.invokeStatic(CL_COMP, Object.class, "instance", ImmutableList.of())
              .andApply(o -> o.orElseThrow());
      comp.andApply(c -> Instanciator.invoke(c, Void.class, "addOperand", 1d));
      final TryCatchAll<Double> secondAdd =
          comp.andApply(c -> Instanciator.invokeProducing(c, Double.class, "apply", "+").orThrow());
      final MarksTree mapped =
          secondAdd.map(r -> Mark.zero("Unexpected answer to invalid state op."),
              c -> Mark.binary(c instanceof IllegalStateException));
      builder.put(INVALID_STATE_OP, mapped);
    }
    return MarksTree.composite(builder.build());
  }

  public MarksTree gradeCust(Instanciator instanciator) {
    final ImmutableMap.Builder<Criterion, MarksTree> builder = ImmutableMap.builder();

    {
      final TryCatchAll<Boolean> attempt = TryCatchAll.get(() -> {
        try (FileSystem fs = Jimfs.newFileSystem()) {
          final Path path = Files.createDirectories(fs.getPath("somedir")).resolve("somefile");
          Files.writeString(path, "productxxy" + System.lineSeparator());
          final List<?> read =
              instanciator.invokeStatic(CL_CUST, List.class, "readOrders", ImmutableList.of(path))
                  .orThrow().orElseThrow();
          return ImmutableList.of("productxxy").equals(read);
        }
      });
      builder.put(READS_ONE_PRODUCT, mark(attempt));
    }

    {
      final TryCatchAll<Boolean> attempt = TryCatchAll.get(() -> {
        try (FileSystem fs = Jimfs.newFileSystem()) {
          final Path path = Files.createDirectories(fs.getPath("somedir")).resolve("somefile");
          Files.writeString(path,
              "productxxy" + System.lineSeparator() + "" + System.lineSeparator() + "another"
                  + System.lineSeparator() + "" + System.lineSeparator());
          final List<?> read =
              instanciator.invokeStatic(CL_CUST, List.class, "readOrders", ImmutableList.of(path))
                  .orThrow().orElseThrow();
          return ImmutableList.of("productxxy", "another").equals(read);
        }
      });
      builder.put(READS_PRODUCTS, mark(attempt));
    }

    {
      final TryCatchAll<Boolean> attempt = TryCatchAll.get(() -> {
        final Object cust =
            instanciator.invokeStatic(CL_CUST, Object.class, "empty", ImmutableList.of()).orThrow()
                .orElseThrow();
        final Set<?> orders = Instanciator.invokeProducing(cust, Set.class, "allOrders").orThrow();
        return orders.isEmpty();
      });
      builder.put(EMPTY, mark(attempt));
    }

    {
      final TryCatchAll<Boolean> attempt = TryCatchAll.get(() -> {
        try (FileSystem fs = Jimfs.newFileSystem()) {
          final Path path = Files.createDirectories(fs.getPath("somedir")).resolve("somefile");
          Files.writeString(path, "productxxy" + System.lineSeparator());
          final Object cust = instanciator
              .invokeStatic(CL_CUST, Object.class, "usingOrders", ImmutableList.of(path)).orThrow()
              .orElseThrow();
          final ImmutableSet<OrderTime> observed = allOrders(cust);
          final OrderNoTime expected = new OrderNoTime(1, ImmutableList.of("productxxy"));
          return allEqual(ImmutableSet.of(expected), observed);
        }
      });
      builder.put(USING_ONE, mark(attempt));
    }

    {
      final TryCatchAll<Boolean> attempt = TryCatchAll.get(() -> {
        final Object cust =
            instanciator.invokeStatic(CL_CUST, Object.class, "empty", ImmutableList.of()).orThrow()
                .orElseThrow();
        Instanciator.invoke(cust, Void.class, "placeOrder",
            ImmutableList.of(INSTANT_1, "productxxy"));
        final ImmutableMap<Instant, ImmutableList<String>> expected =
            ImmutableMap.of(INSTANT_1, ImmutableList.of("productxxy"));
        return equalCust(expected, cust);
      });
      builder.put(PLACING_ONE, mark(attempt));
    }

    {
      final TryCatchAll<Boolean> attempt = TryCatchAll.get(() -> {
        final Object cust =
            instanciator.invokeStatic(CL_CUST, Object.class, "empty", ImmutableList.of()).orThrow()
                .orElseThrow();
        Instanciator.invoke(cust, Void.class, "placeOrder",
            ImmutableList.of(INSTANT_1, "productxxy"));
        Instanciator.invoke(cust, Void.class, "placeOrder",
            ImmutableList.of(INSTANT_2, "productxxyz"));
        final ImmutableMap<Instant, ImmutableList<String>> expected = ImmutableMap.of(INSTANT_1,
            ImmutableList.of("productxxy"), INSTANT_2, ImmutableList.of("productxxyz"));
        return equalCust(expected, cust);
      });
      builder.put(PLACING_TWO, mark(attempt));
    }

    {
      final TryCatchAll<Boolean> attempt = TryCatchAll.get(() -> {
        final Object cust =
            instanciator.invokeStatic(CL_CUST, Object.class, "empty", ImmutableList.of()).orThrow()
                .orElseThrow();
        Instanciator.invoke(cust, Void.class, "placeOrder",
            ImmutableList.of(INSTANT_1, "productxxy"));
        Instanciator.invoke(cust, Void.class, "placeOrder",
            ImmutableList.of(INSTANT_2, "productxxyz"));
        Instanciator.invoke(cust, Void.class, "placeOrder",
            ImmutableList.of(INSTANT_1, "productxxy2"));
        Instanciator.invoke(cust, Void.class, "placeOrder",
            ImmutableList.of(INSTANT_2, "productxxyz2"));
        final ImmutableMap<Instant, ImmutableList<String>> expected =
            ImmutableMap.of(INSTANT_1, ImmutableList.of("productxxy", "productxxy2"), INSTANT_2,
                ImmutableList.of("productxxyz", "productxxyz2"));
        return equalCust(expected, cust);
      });
      builder.put(PLACING_MANY, mark(attempt));
    }

    {
      final TryCatchAll<Object> attempt = TryCatchAll.get(() -> {
        final Object cust =
            instanciator.invokeStatic(CL_CUST, Object.class, "empty", ImmutableList.of()).orThrow()
                .orElseThrow();
        Instanciator
            .invoke(cust, Void.class, "placeOrder", ImmutableList.of(INSTANT_EARLY, "productxxy"))
            .orThrow().orElseThrow();
        return cust;
      });
      builder.put(PLACING_EARLY, attempt.map(c -> Mark.zero("got %s".formatted(allOrdersOrNull(c))),
          e -> Mark.binary(e instanceof IllegalArgumentException)));
    }

    {
      final TryCatchAllVoid attempt = TryCatchAllVoid.run(() -> {
        final Object cust =
            instanciator.invokeStatic(CL_CUST, Object.class, "empty", ImmutableList.of()).orThrow()
                .orElseThrow();
        final Set<String> all =
            Instanciator.invokeProducing(cust, Set.class, "allOrders").orThrow();
        all.add("ploum");
      });
      builder.put(ALL_WRITE_THROWS, attempt.map(() -> Mark.zero(),
          e -> Mark.binary(e instanceof UnsupportedOperationException)));
    }

    {
      final TryCatchAll<Boolean> attempt = TryCatchAll.get(() -> {
        final Object cust =
            instanciator.invokeStatic(CL_CUST, Object.class, "empty", ImmutableList.of()).orThrow()
                .orElseThrow();
        final Stream<Instant> latterOnes =
            Stream.iterate(INSTANT_2, i -> i.plus(Duration.ofMinutes(5l))).limit(50);
        final Stream<Instant> formerOnes =
            Stream.iterate(INSTANT_1, i -> i.plus(Duration.ofMinutes(5l))).limit(50);
        final ImmutableList<Instant> instants =
            Stream.concat(latterOnes, formerOnes).collect(ImmutableList.toImmutableList());
        for (Instant instant : instants) {
          Instanciator.invoke(cust, Void.class, "placeOrder",
              ImmutableList.of(instant, instant.toString()));
        }
        final ImmutableSet<OrderTime> allOrders = allOrders(cust);
        final ImmutableList<Instant> times =
            allOrders.stream().map(OrderTime::time).collect(ImmutableList.toImmutableList());
        return times.equals(ImmutableList.sortedCopyOf(Comparator.naturalOrder(), instants));
      });
      builder.put(ALL_ITERATES, mark(attempt));
    }

    {
      final TryCatchAll<Boolean> attempt = TryCatchAll.get(() -> {
        final Object cust =
            instanciator.invokeStatic(CL_CUST, Object.class, "empty", ImmutableList.of()).orThrow()
                .orElseThrow();
        Instanciator.invoke(cust, Void.class, "placeOrder", ImmutableList.of(INSTANT_1, "one"));
        Instanciator.invoke(cust, Void.class, "placeOrder", ImmutableList.of(INSTANT_2, "two"));
        final List<String> ordered = ordered(cust, INSTANT_1);
        ordered.add("anotherone");
        final List<?> orderedOne = ordered(cust, INSTANT_1);
        final List<?> orderedTwo = ordered(cust, INSTANT_2);
        return ImmutableSet.copyOf(orderedOne).equals(ImmutableSet.of("one", "anotherone"))
            && orderedTwo.equals(ImmutableList.of("two"));
      });
      builder.put(ORDERED_WRITES_BACK, mark(attempt));
    }
    return MarksTree.composite(builder.build());
  }

  private List<String> ordered(final Object cust, final Instant instant) throws Throwable {
    final Object orders =
        Instanciator.invokeProducing(cust, Object.class, "ordered", instant).orThrow();
    final List<String> ordered =
        Instanciator.invokeProducing(orders, List.class, "simpleOrders").orThrow();
    return ordered;
  }

  private boolean equalCust(Map<Instant, ImmutableList<String>> expected, Object observed)
      throws Throwable {
    final ImmutableSet<OrderTime> allOrders = allOrders(observed);
    final ImmutableSet.Builder<OrderTime> indivOrderBuilder = ImmutableSet.builder();
    for (Instant instant : expected.keySet()) {
      final OrderTime inst1 = toOrder(
          Instanciator.invokeProducing(observed, Object.class, "ordered", instant).orThrow());
      indivOrderBuilder.add(inst1);
    }
    final ImmutableSet<OrderTime> indivOrders = indivOrderBuilder.build();
    final ImmutableSet<OrderTime> expectedIndivs = expected.entrySet().stream()
        .map(e -> new OrderTime(expected.size(), e.getKey(), e.getValue()))
        .collect(ImmutableSet.toImmutableSet());
    return allEqualWithTime(expectedIndivs, indivOrders)
        && allEqualWithTime(expectedIndivs, allOrders);
  }

  private ImmutableSet<OrderTime> allOrders(Object cust) throws Throwable {
    final Set<?> orders = Instanciator.invokeProducing(cust, Set.class, "allOrders").orThrow();
    final ImmutableSet.Builder<OrderTime> ordersBuilder = ImmutableSet.builder();
    for (Object order : orders) {
      final OrderTime asOrder = toOrder(order);
      ordersBuilder.add(asOrder);
    }
    final ImmutableSet<OrderTime> observed = ordersBuilder.build();
    return observed;
  }

  private ImmutableSet<OrderTime> allOrdersOrNull(Object cust) {
    return TryCatchAll.get(() -> allOrders(cust)).orMapCause(e -> null);
  }

  private boolean allEqual(ImmutableSet<OrderNoTime> expected, ImmutableSet<OrderTime> observed) {
    if (expected.size() != observed.size()) {
      return false;
    }
    final UnmodifiableIterator<OrderNoTime> it1 = expected.iterator();
    final UnmodifiableIterator<OrderTime> it2 = observed.iterator();
    while (it1.hasNext()) {
      final OrderNoTime e = it1.next();
      final OrderTime o = it2.next();
      if (!equals(e, o)) {
        return false;
      }
    }
    verify(!it2.hasNext());
    return true;
  }

  private boolean allEqualWithTime(ImmutableSet<OrderTime> expected,
      ImmutableSet<OrderTime> observed) {
    if (expected.size() != observed.size()) {
      return false;
    }
    final UnmodifiableIterator<OrderTime> it1 = expected.iterator();
    final UnmodifiableIterator<OrderTime> it2 = observed.iterator();
    while (it1.hasNext()) {
      final OrderTime e = it1.next();
      final OrderTime o = it2.next();
      if (!equals(e, o)) {
        return false;
      }
    }
    verify(!it2.hasNext());
    return true;
  }

  private boolean equals(OrderNoTime expected, OrderTime observed) {
    return expected.customerNbOrders == observed.customerNbOrders
        && expected.simples.equals(observed.simples);
  }

  private boolean equals(OrderTime expected, OrderTime observed) {
    return expected.customerNbOrders == observed.customerNbOrders
        && expected.time.equals(observed.time) && expected.simples.equals(observed.simples);
  }

  private OrderTime toOrder(Object order) throws Throwable {
    final Object cust = Instanciator.invokeProducing(order, Object.class, "customer").orThrow();
    final Set<?> allOrders = Instanciator.invokeProducing(cust, Set.class, "allOrders").orThrow();
    final Instant time = Instanciator.invokeProducing(order, Instant.class, "time").orThrow();
    final List<?> theseOrders =
        Instanciator.invokeProducing(order, List.class, "simpleOrders").orThrow();
    final ImmutableList<String> theseOrdersAsStrings =
        theseOrders.stream().map(o -> (String) o).collect(ImmutableList.toImmutableList());
    return new OrderTime(allOrders.size(), time, theseOrdersAsStrings);
  }

  private <T> T callWithTimeout(TSupplier<? extends T, ?> callable) throws Throwable {
    return limiter.callWithTimeout(() -> TryCatchAll.get(callable), Duration.ofSeconds(5))
        .orThrow();
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

  private <T> MarksTree markG(TryCatchAll<T> attempt, Function<T, Boolean> grader) {
    return attempt.map(t -> Mark.binary(grader.apply(t)), e -> causeToMark(e));
  }

  private MarksTree mark(TryCatchAll<Boolean> attempt) {
    return attempt.map(s -> Mark.binary(s), e -> causeToMark(e));
  }

  @Override
  public GradeAggregator getCodeAggregator() {
    final ImmutableMap.Builder<Criterion, Double> builder = ImmutableMap.builder();
    builder.put(COMP, 6d);
    builder.put(CUST, 14d);
    return GradeAggregator.staticAggregator(builder.build(),
        ImmutableMap.of(COMP, getCompAggregator(), CUST, getCustAggregator()));
  }

  private GradeAggregator getCompAggregator() {
    final ImmutableMap.Builder<Criterion, Double> innerBuilder = ImmutableMap.builder();
    innerBuilder.put(ADD, 1d);
    innerBuilder.put(ONE_THEN_SPURIOUS, 1d);
    innerBuilder.put(ONE_THEN_DIV, 1d);
    innerBuilder.put(DUPL_THEN_MULT, 1d);
    innerBuilder.put(LOGS, 1d);
    innerBuilder.put(INVALID_OP, 0.5d);
    innerBuilder.put(INVALID_STATE_OP, 0.5d);
    return GradeAggregator.staticAggregator(innerBuilder.build(), ImmutableMap.of());
  }

  private GradeAggregator getCustAggregator() {
    final ImmutableMap.Builder<Criterion, Double> innerBuilder = ImmutableMap.builder();
    innerBuilder.put(EMPTY, 1.5d);
    innerBuilder.put(PLACING_ONE, 1d);
    innerBuilder.put(PLACING_TWO, 1d);
    innerBuilder.put(PLACING_MANY, 2d);
    innerBuilder.put(PLACING_EARLY, 0.75d);
    innerBuilder.put(ALL_WRITE_THROWS, 0.75d);

    innerBuilder.put(READS_ONE_PRODUCT, 0.5d);
    innerBuilder.put(READS_PRODUCTS, 1d);
    innerBuilder.put(USING_ONE, 0.5d);

    innerBuilder.put(ALL_ITERATES, 2d);
    innerBuilder.put(ORDERED_WRITES_BACK, 3d);
    return GradeAggregator.staticAggregator(innerBuilder.build(), ImmutableMap.of());
  }

  public void close() {
    executors.shutdownNow();
  }
}
