package io.github.oliviercailloux.git;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;

import org.eclipse.jgit.lib.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSet;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.Graphs;
import com.google.common.graph.ImmutableGraph;
import com.google.common.graph.MutableGraph;
import com.google.common.graph.SuccessorsFunction;

public class GitGenericHistory<E extends ObjectId> {
	public static <E extends ObjectId> GitGenericHistory<E> from(SuccessorsFunction<E> parentsFunction, Set<E> tips) {
		return new GitGenericHistory<>(parentsFunction, tips);
	}

	private final ImmutableGraph<E> graph;

	GitGenericHistory(SuccessorsFunction<E> parentsFunction, Set<E> tips) {
		final Queue<E> toConsider = new LinkedList<>(tips);
		final Set<E> seen = new LinkedHashSet<>(tips);

		final MutableGraph<E> mutableGraph = GraphBuilder.directed().build();
		while (!toConsider.isEmpty()) {
			final E current = toConsider.remove();
			mutableGraph.addNode(current);
			final Iterable<? extends E> parents = parentsFunction.successors(current);
			LOGGER.debug("Parents of {}: {}.", current.getName(), parents);
			for (E parent : parents) {
				mutableGraph.putEdge(current, parent);
				if (!seen.contains(parent)) {
					toConsider.add(parent);
					seen.add(parent);
				}
			}
		}
		this.graph = ImmutableGraph.copyOf(mutableGraph);
		checkArgument(!Graphs.hasCycle(graph));
	}

	/**
	 * The children to which everything points; the starting points in time of the
	 * git history. Note that this departs from the usual forest-view of a DAG,
	 * where the edges go away from the root: here they go towards the roots (as is
	 * usual when representing a Git history).
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
		assert !roots.isEmpty();
		return roots;
	}

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GitGenericHistory.class);

	/**
	 * @return a graph representing the has-as-parent (child-of) relation: the
	 *         successors of a node are its parents; following the successors
	 *         relation (child-of) goes back in time; a pair (a, b) represents a
	 *         child a and its parent b.
	 */
	public ImmutableGraph<E> getGraph() {
		return graph;
	}
}
