package io.github.oliviercailloux.javagrade.graders;

import static com.google.common.base.Verify.verify;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
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
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.junit.platform.engine.discovery.ClassNameFilter;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;
import org.junit.platform.launcher.listeners.TestExecutionSummary.Failure;
import org.junit.platform.launcher.listeners.discovery.LauncherDiscoveryListeners;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Additioner implements CodeGrader<RuntimeException> {
  @SuppressWarnings("unused")
  private static final Logger LOGGER = LoggerFactory.getLogger(Additioner.class);

  public static final ZonedDateTime DEADLINE_ORIGINAL =
      LocalDateTime.parse("2024-04-10T19:00:00").atZone(ZoneId.of("Europe/Paris"));
  public static final double USER_WEIGHT = 0d;

  public static Instanciator staticInstanciator;

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

  private static final Criterion ADD = Criterion.given("pos");
  private static final Criterion ADD_NEG = Criterion.given("neg");

  @Override
  public MarksTree gradeCode(Instanciator instanciator) {
    Additioner.staticInstanciator = instanciator;

    String name = AdditionerTests.class.getCanonicalName();
    LOGGER.info("Discovering tests in {}.", name);
    LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
        .selectors(DiscoverySelectors.selectPackage(Additioner.class.getPackageName())).build();
    SummaryGeneratingListener listener = new SummaryGeneratingListener();
    try (LauncherSession session = LauncherFactory.openSession()) {
      Launcher launcher = session.getLauncher();
      launcher.registerTestExecutionListeners(listener);
      launcher.registerLauncherDiscoveryListeners(LauncherDiscoveryListeners.logging());
      TestPlan testPlan = launcher.discover(request);
      verify(testPlan.containsTests(), "No tests found.");
      verify(testPlan.getRoots().size() == 1);
      TestIdentifier root = Iterables.getOnlyElement(testPlan.getRoots());
      LOGGER.info("Root: {}.", root.getDisplayName());
      verify(root.isContainer());
      Set<TestIdentifier> children = testPlan.getChildren(root.getUniqueIdObject());
      verify(!children.isEmpty());
      verify(children.size() == 1);
      TestIdentifier child = Iterables.getOnlyElement(children);
      LOGGER.info("Child: {}.", child.getDisplayName());
      verify(child.isContainer());
      launcher.execute(testPlan);
      Set<TestIdentifier> childChildren = testPlan.getChildren(child.getUniqueIdObject());
      verify(!childChildren.isEmpty());
      verify(childChildren.size() == 2);
      ImmutableSet<String> testNames = childChildren.stream().map(TestIdentifier::getDisplayName).collect(ImmutableSet.toImmutableSet());
      verify(testNames.equals(ImmutableSet.of(ADD.getName() + "()", ADD_NEG.getName() + "()")), testNames.toString());

      TestExecutionSummary summary = listener.getSummary();
      verify(summary.getTestsFoundCount() == 2);
      verify(summary.getTestsStartedCount() == 2);
      verify(summary.getTestsSkippedCount() == 0);
      long ko = summary.getTestsAbortedCount() + summary.getTestsFailedCount();
      verify(ko + summary.getTestsSucceededCount() == 2);
      List<Failure> failures = summary.getFailures();
      ImmutableSet<String> failedTests = failures.stream().map(f -> f.getTestIdentifier().getDisplayName()).collect(ImmutableSet.toImmutableSet());
      ImmutableSet<String> succeededTests = Sets.difference(testNames, failedTests).immutableCopy();
      verify(failures.size() == ko);
      final ImmutableMap.Builder<Criterion, MarksTree> builder = ImmutableMap.builder();
      for (Failure f : failures) {
        builder.put(criterion(f.getTestIdentifier().getDisplayName()), Mark.zero(f.getException().getMessage()));
      }
      succeededTests.stream().forEach(t -> builder.put(criterion(t), Mark.one()));
      return MarksTree.composite(builder.build());
    }
  }

  private static Criterion criterion(String displayName) {
    String criterionName = displayName.substring(0, displayName.length() - 2);
    Criterion criterion = Criterion.given(criterionName);
    return criterion;
  }

  @Override
  public GradeAggregator getCodeAggregator() {
    final ImmutableMap.Builder<Criterion, Double> innerBuilder = ImmutableMap.builder();
    innerBuilder.put(ADD, 3d);
    innerBuilder.put(ADD_NEG, 2d);
    return GradeAggregator.staticAggregator(innerBuilder.build(), ImmutableMap.of());
  }
}
