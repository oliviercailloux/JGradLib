package io.github.oliviercailloux.java_grade.graders;

import com.google.common.base.VerifyException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.github.oliviercailloux.exercices.car.Car;
import io.github.oliviercailloux.exercices.car.Person;
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
import io.github.oliviercailloux.java_grade.bytecode.Instanciator;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CarGrader implements CodeGrader<RuntimeException> {
    @SuppressWarnings("unused")
    private static final Logger LOGGER = LoggerFactory.getLogger(CarGrader.class);

    public static final String PREFIX = "car";

    public static final ZonedDateTime DEADLINE_ORIGINAL =
            LocalDateTime.parse("2023-04-19T14:31:00").atZone(ZoneId.of("Europe/Paris"));
    public static final Instant CAP_ORIGINAL =
            DEADLINE_ORIGINAL.toInstant().plus(Duration.ofMinutes(10));

    public static final double USER_WEIGHT = 0.025d;

    private static boolean equalPersons(Person p1, Person p2) {
        return Objects.equals(p1.getName(), p2.getName())
                && p1.getFavoriteSpeed() == p2.getFavoriteSpeed();
    }

    public static void main(String[] args) throws Exception {
        original();
    }

    public static void original() throws IOException {
        final GitFileSystemWithHistoryFetcher fetcher = GitFileSystemWithHistoryFetcherByPrefix
                .getRetrievingByPrefixAndFiltering(PREFIX, "COLLARDEAU2000");
        // .getRetrievingByPrefix(PREFIX);
        final BatchGitHistoryGrader<RuntimeException> batchGrader =
                BatchGitHistoryGrader.given(() -> fetcher);

        final CarGrader grader421 = new CarGrader();
        final MavenCodeGrader<RuntimeException> m = MavenCodeGrader.penal(grader421,
                UncheckedIOException::new, WarningsBehavior.DO_NOT_PENALIZE);

        batchGrader.getAndWriteGradesExp(DEADLINE_ORIGINAL, Duration.ofMinutes(10),
                GitFsGraderUsingLast.using(m),
                // USER_WEIGHT, Path.of("grades " + PREFIX + " original"),
                // PREFIX + " original " + Instant.now().atZone(DEADLINE_ORIGINAL.getZone()));
                USER_WEIGHT, Path.of("grades " + PREFIX),
                PREFIX + Instant.now().atZone(DEADLINE_ORIGINAL.getZone()));
        grader421.close();
        LOGGER.info("Done original, closed.");
    }

    private static final Criterion C0 = Criterion.given("Anything committed");

    private static final Criterion C_DEFAULT_BLACK = Criterion.given("Default black");

    private static final Criterion C_PAINT = Criterion.given("Paint");
    private static final Criterion C_RENAME = Criterion.given("Rename");

    private static final Criterion C_DRIVER = Criterion.given("Driver");
    private static final Criterion C_PASSENGER = Criterion.given("Passenger swapped");

    private static final Criterion C_DRIVE = Criterion.given("Drive");
    private static final Criterion C_PASSENGER_DRIVE = Criterion.given("Drive after swap");
    private final ExecutorService executors;

    public CarGrader() {
        executors = Executors.newCachedThreadPool();
    }

    @Override
    public MarksTree gradeCode(Instanciator instanciator) {
        final ImmutableMap.Builder<Criterion, MarksTree> builder = ImmutableMap.builder();
        final String mPersonClass = "io.github.oliviercailloux.exercices.car.MutablePerson";
        final String immPersonClass = "io.github.oliviercailloux.exercices.car.ImmutablePerson";
        final String carClass = "io.github.oliviercailloux.exercices.car.SimpleCar";
        {
            builder.put(C0, Mark.one());
        }

        {
            final TryCatchAll<Person> driver = instanciator.invokeConstructor(immPersonClass,
                    Person.class, ImmutableList.of("name", 71));
            final TryCatchAll<Car> car = driver.map(
                    d -> instanciator.invokeConstructor(carClass, Car.class, ImmutableList.of(d)),
                    c -> TryCatchAll.failure(c));
            final TryCatchAll<Boolean> isBlack = car.andApply(c -> c.isBlack());
            builder.put(C_DEFAULT_BLACK,
                    isBlack.map(b -> Mark.binary(b, "", "Expected black but isn’t"),
                            c -> Mark.zero("Obtained %s".formatted(c))));
        }

        {
            final TryCatchAll<Person> driver = instanciator.invokeConstructor(immPersonClass,
                    Person.class, ImmutableList.of("name", 71));
            final TryCatchAll<Car> car = driver.map(
                    d -> instanciator.invokeConstructor(carClass, Car.class, ImmutableList.of(d)),
                    c -> TryCatchAll.failure(c));
            car.andConsume(c -> c.paintBlack());
            final TryCatchAll<Boolean> isBlack1 = car.andApply(c -> c.isBlack());
            car.andConsume(c -> c.paintBlack());
            final TryCatchAll<Boolean> isBlack2 = car.andApply(c -> c.isBlack());
            car.andConsume(c -> c.paintWhite());
            final TryCatchAll<Boolean> isWhite = car.andApply(c -> !c.isBlack());
            final TryCatchAll<Boolean> isCorrect =
                    isBlack1.and(isBlack2, (c1, c2) -> c1).and(isWhite, (c1, c2) -> c1);
            builder.put(C_PAINT,
                    isCorrect.map(b -> Mark.binary(b, "",
                            "Painted black, black, white but did not get appropriate results"),
                            c -> Mark.zero("Obtained %s".formatted(c))));
        }

        {
            final TryCatchAll<Person> mPerson = instanciator.invokeConstructor(mPersonClass,
                    Person.class, ImmutableList.of("nameHHKorig", 71));
            final TryCatchAll<Person> pRenamed = mPerson.andConsume(p -> Instanciator
                    .invoke(p, Void.class, "rename", ImmutableList.of("a new name!")).orThrow());
            final TryCatchAll<String> newName = pRenamed.andApply(p -> p.getName());
            builder.put(C_RENAME,
                    newName.map(
                            n -> Mark.binary(n.equals("a new name!"), "",
                                    "Expected new name but got %s".formatted(n)),
                            c -> Mark.zero("Obtained %s".formatted(c))));
        }

        {
            final TryCatchAll<Person> driver = instanciator.invokeConstructor(immPersonClass,
                    Person.class, ImmutableList.of("nameHHK", 71));
            final TryCatchAll<Car> car = driver.map(
                    d -> instanciator.invokeConstructor(carClass, Car.class, ImmutableList.of(d)),
                    c -> TryCatchAll.failure(c));
            final TryCatchAll<Person> driverBack = car.andApply(c -> c.getDriver());
            builder.put(C_DRIVER,
                    driverBack.map(
                            d -> Mark.binary(equalPersons(d, driver.orThrow(VerifyException::new)),
                                    "", "Expected original (and implemented) driver but isn’t"),
                            c -> Mark.zero("Obtained %s".formatted(c))));
        }

        {
            final TryCatchAll<Person> driver = instanciator.invokeConstructor(immPersonClass,
                    Person.class, ImmutableList.of("nameHHK", 71));
            final TryCatchAll<Person> passenger = instanciator.invokeConstructor(immPersonClass,
                    Person.class, ImmutableList.of("namePsHHK", 61));
            final TryCatchAll<Car> car = driver.map(
                    d -> instanciator.invokeConstructor(carClass, Car.class, ImmutableList.of(d)),
                    c -> TryCatchAll.failure(c));
            final TryCatchAll<Car> carSet =
                    car.andConsume(c -> c.setPassenger(passenger.orThrow(VerifyException::new)));
            final TryCatchAll<Car> carSwapped = carSet.andConsume(c -> c.swap());
            final TryCatchAll<Person> passengerBack = carSwapped.andApply(c -> c.getDriver());
            builder.put(C_PASSENGER, passengerBack.map(
                    p -> Mark.binary(equalPersons(p, passenger.orThrow(VerifyException::new)), "",
                            "Expected original (and implemented) passenger but isn’t"),
                    c -> Mark.zero("Obtained %s".formatted(c))));
        }

        {
            final TryCatchAll<Person> driver = instanciator.invokeConstructor(immPersonClass,
                    Person.class, ImmutableList.of("nameHHK", 71));
            final TryCatchAll<Car> car = driver.map(
                    d -> instanciator.invokeConstructor(carClass, Car.class, ImmutableList.of(d)),
                    c -> TryCatchAll.failure(c));
            final TryCatchAll<Car> carMoved = car.andConsume(c -> c.drive(1));
            final TryCatchAll<Car> carMoved2 = carMoved.andConsume(c -> c.drive(3));
            final TryCatchAll<Car> carMoved3 = carMoved2.andConsume(c -> c.drive(1));
            final TryCatchAll<Integer> dist = carMoved3.andApply(c -> c.getTotalTraveledDistance());
            builder.put(C_DRIVE,
                    dist.map(
                            d -> Mark.binary(d == 355, "",
                                    "Expected driven for 5 hours at 71 km / h = 355 km but got %s"
                                            .formatted(d)),
                            c -> Mark.zero("Obtained %s".formatted(c))));
        }

        {
            final TryCatchAll<Person> driver = instanciator.invokeConstructor(immPersonClass,
                    Person.class, ImmutableList.of("nameHHK", 71));
            final TryCatchAll<Person> passenger = instanciator.invokeConstructor(immPersonClass,
                    Person.class, ImmutableList.of("namePsHHK", 61));
            final TryCatchAll<Car> car = driver.map(
                    d -> instanciator.invokeConstructor(carClass, Car.class, ImmutableList.of(d)),
                    c -> TryCatchAll.failure(c));
            final TryCatchAll<Car> carMoved = car.andConsume(c -> c.drive(1));
            final TryCatchAll<Car> carMoved2 = carMoved.andConsume(c -> c.drive(3));
            final TryCatchAll<Car> carMoved3 = carMoved2.andConsume(c -> c.drive(1));
            final TryCatchAll<Car> carSet = carMoved3
                    .andConsume(c -> c.setPassenger(passenger.orThrow(VerifyException::new)));
            final TryCatchAll<Car> carSwapped = carSet.andConsume(c -> c.swap());
            final TryCatchAll<Car> carMovedA = carSwapped.andConsume(c -> c.drive(1));
            final TryCatchAll<Car> carMoved2A = carMovedA.andConsume(c -> c.drive(1));
            final TryCatchAll<Car> carMoved3A = carMoved2A.andConsume(c -> c.drive(1));
            final TryCatchAll<Integer> dist =
                    carMoved3A.andApply(c -> c.getTotalTraveledDistance());
            builder.put(C_PASSENGER_DRIVE, dist.map(d -> Mark.binary(d == 538, "",
                    "Expected driven for 5 hours at speed 71 km / h then 3 hours at (previously passenger) speed 61 km / h = 538 km but got %s"
                            .formatted(d)),
                    c -> Mark.zero("Obtained %s".formatted(c))));
        }

        return MarksTree.composite(builder.build());
    }

    @Override
    public GradeAggregator getCodeAggregator() {
        final ImmutableMap.Builder<Criterion, Double> builder = ImmutableMap.builder();
        builder.put(C0, 0.5d);
        builder.put(C_DEFAULT_BLACK, 3d);
        builder.put(C_PAINT, 3d);
        builder.put(C_RENAME, 1d);
        builder.put(C_DRIVER, 3d);
        builder.put(C_PASSENGER, 3d);
        builder.put(C_DRIVE, 3d);
        builder.put(C_PASSENGER_DRIVE, 3d);
        return GradeAggregator.staticAggregator(builder.build(), ImmutableMap.of());
    }

    public void close() {
        executors.shutdownNow();
    }
}
