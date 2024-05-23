package io.github.oliviercailloux.javagrade;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Verify.verify;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.Mark;
import io.github.oliviercailloux.grade.MarksTree;
import io.github.oliviercailloux.javagrade.bytecode.Instanciator;
import java.util.List;
import java.util.Set;
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

public class JUnitHelper {
  @SuppressWarnings("unused")
  private static final Logger LOGGER = LoggerFactory.getLogger(JUnitHelper.class);

  public static Instanciator staticInstanciator;

  public static MarksTree grade(String packageName, Instanciator instanciator) {
    JUnitHelper.staticInstanciator = instanciator;

    LOGGER.debug("Discovering tests in {}.", packageName);
    LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
        .selectors(DiscoverySelectors.selectPackage(packageName)).build();
    SummaryGeneratingListener listener = new SummaryGeneratingListener();
    try (LauncherSession session = LauncherFactory.openSession()) {
      Launcher launcher = session.getLauncher();
      launcher.registerTestExecutionListeners(listener);
      launcher.registerLauncherDiscoveryListeners(LauncherDiscoveryListeners.logging());
      TestPlan testPlan = launcher.discover(request);
      verify(testPlan.containsTests(), "No tests found.");
      verify(testPlan.getRoots().size() == 1);
      TestIdentifier root = Iterables.getOnlyElement(testPlan.getRoots());
      verify(root.isContainer(), root.getDisplayName());
      Set<TestIdentifier> children = testPlan.getChildren(root.getUniqueIdObject());
      verify(!children.isEmpty());
      verify(children.size() == 1);
      TestIdentifier child = Iterables.getOnlyElement(children);
      verify(child.isContainer(), child.getDisplayName());
      Set<TestIdentifier> childChildren = testPlan.getChildren(child.getUniqueIdObject());
      verify(!childChildren.isEmpty());
      ImmutableSet<String> testNames = childChildren.stream().map(TestIdentifier::getDisplayName).collect(ImmutableSet.toImmutableSet());
      
      launcher.execute(testPlan);
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
    checkArgument(displayName.length() >= 3, displayName);
    String end = displayName.substring(displayName.length()-2, displayName.length());
    checkArgument(end.equals("()"), end);
    String criterionName = displayName.substring(0, displayName.length() - 2);
    Criterion criterion = Criterion.given(criterionName);
    return criterion;
  }

  private static String testName(Criterion criterion) {
    return criterion.getName() + "()";
  }
}
