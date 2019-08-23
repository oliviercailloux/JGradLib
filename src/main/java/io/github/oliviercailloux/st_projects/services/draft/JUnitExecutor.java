package io.github.oliviercailloux.st_projects.services.draft;

import static com.google.common.base.Preconditions.checkState;

import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class JUnitExecutor {
	public static void main(String[] args) {
		LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
				.selectors(DiscoverySelectors.selectClass(SimpleTester.class)).build();

		Launcher launcher = LauncherFactory.create();

		// Register a listener of your choice
		SummaryGeneratingListener listener = new SummaryGeneratingListener();
//		launcher.registerTestExecutionListeners(listener);

		launcher.execute(request, listener);

		TestExecutionSummary summary = listener.getSummary();
		checkState(summary.getContainersAbortedCount() == 0);
		checkState(summary.getContainersFailedCount() == 0);
		checkState(summary.getTestsAbortedCount() == 0);
		final int nbTests = 3;
		checkState(summary.getTestsFoundCount() == nbTests);
		checkState(summary.getTestsSkippedCount() == 0);
		final long failureCount = summary.getTestsFailedCount();
		final long successCount = summary.getTestsSucceededCount();
		checkState(failureCount + successCount == nbTests);
		LOGGER.warn("Failed: {}, succeeded: {}.", failureCount, successCount);
		/** Should time out if code cycles. */
	}

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(JUnitExecutor.class);
}
