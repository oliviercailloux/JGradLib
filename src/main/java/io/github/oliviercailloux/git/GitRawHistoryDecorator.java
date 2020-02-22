package io.github.oliviercailloux.git;

import static com.google.common.base.Preconditions.checkArgument;

import java.time.Instant;
import java.util.LinkedList;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.function.Predicate;

import org.eclipse.jgit.lib.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.graph.Graph;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.Graphs;
import com.google.common.graph.ImmutableGraph;

import io.github.oliviercailloux.utils.Utils;

public class GitRawHistoryDecorator<E extends ObjectId> implements GitHistory<E> {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GitRawHistoryDecorator.class);

	public static <E extends ObjectId> GitRawHistory<E> filter(GitRawHistory<E> history, Predicate<E> predicate) {
		final ImmutableGraph.Builder<E> builder = GraphBuilder.directed().immutable();
		final ImmutableGraph<E> graph = history.getGraph();
		final ImmutableSet<E> kept = graph.nodes().stream().filter(predicate).collect(ImmutableSet.toImmutableSet());
		kept.stream().forEach(builder::addNode);

		/**
		 * I did not find any algorithm for this in JGraphT as of Feb, 2020.
		 *
		 * Roughly speaking, I want a filtered graph that has edges from the original
		 * one only if a was reachable from b but is not deducible by transitive closure
		 * from the reduct.
		 */
		for (E a : kept) {
			final Queue<E> queue = new LinkedList<>();
			queue.addAll(graph.predecessors(a));
			while (!queue.isEmpty()) {
				final E b = queue.remove();
				if (predicate.test(b)) {
					builder.putEdge(b, a);
				} else {
					queue.addAll(graph.predecessors(b));
				}
			}
		}

		return raw(builder.build(), Maps.filterKeys(history.getCommitDates(), predicate::test));
	}

	public static interface GitRawHistory<E extends ObjectId> {
		public ImmutableGraph<E> getGraph();

		public ImmutableGraph<ObjectId> getRawGraph();

		public Instant getCommitDate(E objectId);

		public ImmutableMap<E, Instant> getCommitDates();
	}

	public static <E extends ObjectId> GitRawHistoryImpl<E> raw(Graph<E> graph, Map<E, Instant> commitDates) {
		return new GitRawHistoryImpl<>(graph, commitDates);
	}

	private static class GitRawHistoryImpl<E extends ObjectId> implements GitRawHistory<E> {

		private final ImmutableGraph<E> graph;
		private ImmutableMap<E, Instant> commitDates;

		private GitRawHistoryImpl(Graph<E> graph, Map<E, Instant> commitDates) {
			this.graph = Utils.asImmutableGraph(graph);
			this.commitDates = ImmutableMap.copyOf(commitDates);
		}

		@Override
		public ImmutableGraph<E> getGraph() {
			return graph;
		}

		@SuppressWarnings("unchecked")
		@Override
		public ImmutableGraph<ObjectId> getRawGraph() {
			return (ImmutableGraph<ObjectId>) graph;
		}

		@Override
		public Instant getCommitDate(E objectId) {
			return commitDates.get(objectId);
		}

		@Override
		public ImmutableMap<E, Instant> getCommitDates() {
			return commitDates;
		}

	}

	public static <E extends ObjectId> GitHistory<E> wrap(GitRawHistory<E> raw) {
		return new GitRawHistoryDecorator<>(raw);
	}

	protected final GitRawHistory<E> raw;
	private ImmutableGraph<E> transitiveClosure;

	protected GitRawHistoryDecorator(GitRawHistory<E> raw) {
		this.raw = raw;
		checkArgument(!Graphs.hasCycle(raw.getGraph()));
		final ImmutableSet<E> dateKeys = raw.getCommitDates().keySet();
		final Set<E> nodes = raw.getGraph().nodes();
		checkArgument(dateKeys.equals(nodes),
				String.format("Commit dates: %s; graph nodes: %s, dates contain nodes: %s, missing: %s.",
						dateKeys.size(), nodes.size(), dateKeys.containsAll(nodes), Sets.difference(dateKeys, nodes)));
		transitiveClosure = null;
	}

	@Override
	public ImmutableGraph<E> getGraph() {
		return raw.getGraph();
	}

	public ImmutableGraph<E> getTransitivelyClosedGraph() {
		if (transitiveClosure == null) {
			transitiveClosure = ImmutableGraph.copyOf(Graphs.transitiveClosure(getGraph()));
		}
		return transitiveClosure;
	}

	@Override
	public ImmutableGraph<ObjectId> getRawGraph() {
		return raw.getRawGraph();
	}

	@Override
	public ImmutableSet<E> getRoots() {
		/**
		 * We could start from any given node and simply follow the successor
		 * (has-as-parent) relation, but that finds only one root.
		 */
		final ImmutableSet<E> roots = getGraph().nodes().stream().filter((n) -> getGraph().successors(n).isEmpty())
				.collect(ImmutableSet.toImmutableSet());
		return roots;
	}

	@Override
	public ImmutableSet<E> getTips() {
		final ImmutableSet<E> tips = getGraph().nodes().stream().filter((n) -> getGraph().predecessors(n).isEmpty())
				.collect(ImmutableSet.toImmutableSet());
		return tips;
	}

	@Override
	public Instant getCommitDate(E objectId) {
		return raw.getCommitDate(objectId);
	}

	@Override
	public ImmutableMap<E, Instant> getCommitDates() {
		return raw.getCommitDates();
	}

	public GitHistory<E> filter(Predicate<E> predicate) {
		return wrap(filter(raw, predicate));
	}

	@Override
	public boolean equals(Object o2) {
		if (!(o2 instanceof GitHistory<?>)) {
			return false;
		}

		final GitHistory<?> h2 = (GitHistory<?>) o2;
		return getGraph().equals(h2.getGraph()) && getCommitDates().equals(h2.getCommitDates());
	}

	@Override
	public int hashCode() {
		return Objects.hash(getGraph(), getCommitDates());
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this).add("graph", getGraph()).add("commitDates", getCommitDates())
				.toString();
	}
}
