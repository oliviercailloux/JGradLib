package io.github.oliviercailloux.st_projects.model;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Comparators;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Streams;
import com.google.common.graph.ImmutableGraph;
import com.google.common.graph.Traverser;

import io.github.oliviercailloux.git.Client;

public class GradingContextWithTimeline {

	private final Client client;

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
		LOGGER.info("Commits: {}; on time: {}.", ImmutableList.copyOf(children), commitsOnTime);
		checkState(Comparators.isInOrder(commitsOnTime,
				Comparator.<RevCommit, Instant>comparing((c) -> commitsReceptionTime.get(c.getId())).reversed()));
		return commitsOnTime.stream().findFirst();
	}

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GradingContextWithTimeline.class);
}
