package io.github.oliviercailloux.git;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Objects;
import java.util.Set;

import org.eclipse.jgit.lib.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.MoreObjects;
import com.google.common.base.Verify;
import com.google.common.collect.ImmutableSet;
import com.google.common.graph.EndpointPair;
import com.google.common.graph.Graph;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.Graphs;
import com.google.common.graph.ImmutableGraph;
import com.google.common.graph.MutableGraph;
import com.google.common.graph.SuccessorsFunction;

import io.github.oliviercailloux.utils.Utils;

public class GitGenericHistory<E extends ObjectId> {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GitGenericHistory.class);

	public static <E extends ObjectId> GitGenericHistory<E> from(SuccessorsFunction<E> parentsFunction, Set<E> tips) {
		final Graph<E> graph = Utils.asGraph(parentsFunction, tips);
		return new GitGenericHistory<>(graph);
	}

	private final ImmutableGraph<E> graph;

	GitGenericHistory(Graph<E> graph) {
		this.graph = ImmutableGraph.copyOf(graph);
		checkArgument(!graph.nodes().isEmpty());
		checkArgument(!Graphs.hasCycle(graph));
	}

	/**
	 * The parents to which everything points; the starting points in time of the
	 * git history. Note that this departs from the usual forest-view of a DAG,
	 * where the edges go away from the root: here they go towards the roots (as is
	 * usual when representing a Git history). (Usually there’s a single root, but
	 * git allows for <a href=
	 * "https://git-scm.com/docs/git-checkout#Documentation/git-checkout.txt---orphanltnewbranchgt">multiple
	 * roots</a>.)
	 *
	 * @return a non-empty set.
	 */
	public ImmutableSet<E> getRoots() {
		/**
		 * We could start from any given node and simply follow the successor
		 * (has-as-parent) relation, but that finds only one root. Git allows for
		 * multiple roots.
		 */
		final ImmutableSet<E> roots = graph.nodes().stream().filter((n) -> graph.successors(n).isEmpty())
				.collect(ImmutableSet.toImmutableSet());
		Verify.verify(!roots.isEmpty());
		return roots;
	}

	/**
	 * @return the nodes with no children (no predecessor), from which the
	 *         “successors” (parent-of) relation starts; the most recent node on
	 *         each branch.
	 */
	public ImmutableSet<E> getTips() {
		final ImmutableSet<E> tips = graph.nodes().stream().filter((n) -> graph.predecessors(n).isEmpty())
				.collect(ImmutableSet.toImmutableSet());
		Verify.verify(!tips.isEmpty());
		return tips;
	}

	/**
	 * @return a graph representing the has-as-parent (child-of) relation: the
	 *         successors of a node are its parents; following the successors
	 *         relation (child-of) goes back in time; a pair (a, b) represents a
	 *         child a and its parent b.
	 */
	public ImmutableGraph<E> getGraph() {
		return graph;
	}

	/**
	 * @return the same graph as {@link #getGraph()} but typed differently,
	 *         permitting to search for example for the successors of a given
	 *         ObjectId even when not knowing the corresponding instance of kind E.
	 */
	public ImmutableGraph<ObjectId> getRawGraph() {
		final MutableGraph<ObjectId> rawGraph = GraphBuilder.directed().build();
		final Set<EndpointPair<E>> edges = graph.edges();
		for (EndpointPair<E> endpointPair : edges) {
			rawGraph.putEdge(endpointPair.nodeU(), endpointPair.nodeV());
		}
		return ImmutableGraph.copyOf(rawGraph);
	}

	@Override
	public boolean equals(Object o2) {
		if (!(o2 instanceof GitGenericHistory)) {
			return false;
		}

		final GitGenericHistory<?> h2 = (GitGenericHistory<?>) o2;
		return graph.equals(h2.graph);
	}

	@Override
	public int hashCode() {
		return Objects.hash(graph);
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this).add("graph", graph).toString();
	}
}
