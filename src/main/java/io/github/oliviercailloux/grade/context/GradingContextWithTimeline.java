package io.github.oliviercailloux.grade.context;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Streams;
import com.google.common.graph.Graphs;
import com.google.common.graph.ImmutableGraph;
import com.google.common.graph.Traverser;

import io.github.oliviercailloux.git.Client;

public class GradingContextWithTimeline {

	private final Client client;

	/**
	 * @param commitsReceptionTime in case of uncertaintly, indicate the earlier
	 *                             time that the commit may exist: before that, we
	 *                             know it didn’t exist. This permits to ensure that
	 *                             conclusions about lateness are certain.
	 */
	public static GradingContextWithTimeline given(Client client, Map<ObjectId, Instant> commitsReceptionTime) {
		return new GradingContextWithTimeline(client, commitsReceptionTime);
	}

	private GradingContextWithTimeline(Client client, Map<ObjectId, Instant> commitsReceptionTime) {
		this.client = requireNonNull(client);
		this.commitsReceptionTime = ImmutableMap.copyOf(requireNonNull(commitsReceptionTime));
	}

	private final ImmutableMap<ObjectId, Instant> commitsReceptionTime;

	public ImmutableMap<ObjectId, Instant> getCommitsReceptionTime() {
		return commitsReceptionTime;
	}

	public Optional<Instant> getReceivedAt(ObjectId id) {
		return Optional.ofNullable(commitsReceptionTime.get(id));
	}

	public Optional<RevCommit> getLatestNotIgnoredChildOf(RevCommit startCommit, Instant ignoreAfter) {
		final ImmutableGraph<RevCommit> graph = client.getAllHistoryCached().getGraph();
		final Iterable<RevCommit> children = Traverser.forGraph(graph).breadthFirst(startCommit);
		final ImmutableList<RevCommit> commitsOnTime = Streams.stream(children)
				.filter((c) -> !commitsReceptionTime.get(c.getId()).isAfter(ignoreAfter))
				.collect(ImmutableList.toImmutableList());
		LOGGER.debug("Commits: {}; on time: {}.", ImmutableList.copyOf(children), commitsOnTime);
		final Comparator<RevCommit> comparingReceptionTime = Comparator
				.comparing((c) -> commitsReceptionTime.get(c.getId()));
		final Optional<RevCommit> amongLatest = commitsOnTime.stream().findFirst();
		checkState(commitsOnTime.stream().allMatch((c) -> comparingReceptionTime.compare(amongLatest.get(), c) >= 0));
		/**
		 * In case two commits have been received at the same moment and are both the
		 * latest ones not ignored.
		 */
		final ImmutableList<RevCommit> allLatest = commitsOnTime.stream()
				.filter((c) -> comparingReceptionTime.compare(amongLatest.get(), c) == 0)
				.collect(ImmutableList.toImmutableList());
		if (allLatest.size() >= 2) {
			final RevCommit shouldBeParent = amongLatest.get();
			final Set<RevCommit> allItsChildren = Graphs.reachableNodes(graph, shouldBeParent);
			checkState(allLatest.get(0).equals(shouldBeParent));
			checkState(allItsChildren.containsAll(allLatest.subList(1, allLatest.size())));
		}
		/**
		 * Perhaps it could be that the two latest commits are not in parent-children
		 * relationship, both being child of a parent that is ignored. This is quite
		 * unlikely, I’ll think about it if it happens.
		 */
		return amongLatest;
	}

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GradingContextWithTimeline.class);
}
