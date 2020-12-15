package io.github.oliviercailloux.git;

import static com.google.common.base.Preconditions.checkArgument;

import java.time.Instant;
import java.util.Map;

import org.eclipse.jgit.lib.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.graph.Graph;
import com.google.common.graph.ImmutableGraph;

class GitHistoryImpl implements GitHistory {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GitHistoryImpl.class);

	public static GitHistoryImpl create(Graph<ObjectId> graph, Map<ObjectId, Instant> dates) {
		return new GitHistoryImpl(graph, dates);
	}

	private final ImmutableGraph<ObjectId> graph;

	private final ImmutableMap<ObjectId, Instant> dates;

	private GitHistoryImpl(Graph<ObjectId> graph, Map<ObjectId, Instant> dates) {
		this.graph = ImmutableGraph.copyOf(graph);
		this.dates = ImmutableMap.copyOf(dates);
	}

	@Override
	public ImmutableGraph<ObjectId> getGraph() {
		return graph;
	}

	@Override
	public ImmutableSet<ObjectId> getRoots() {
		/**
		 * We could start from any given node and simply follow the successor
		 * (has-as-parent) relation, but that finds only one root.
		 */
		final ImmutableSet<ObjectId> roots = getGraph().nodes().stream()
				.filter((n) -> getGraph().successors(n).isEmpty()).collect(ImmutableSet.toImmutableSet());
		return roots;
	}

	@Override
	public ImmutableSet<ObjectId> getLeaves() {
		final ImmutableSet<ObjectId> leaves = getGraph().nodes().stream()
				.filter((n) -> getGraph().predecessors(n).isEmpty()).collect(ImmutableSet.toImmutableSet());
		return leaves;
	}

	@Override
	public Instant getCommitDate(ObjectId commitId) {
		checkArgument(dates.containsKey(commitId));
		return dates.get(commitId);
	}

	@Override
	public ImmutableMap<ObjectId, Instant> getCommitDates() {
		return dates;
	}

}
