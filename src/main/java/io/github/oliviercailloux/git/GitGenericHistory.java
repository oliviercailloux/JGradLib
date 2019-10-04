package io.github.oliviercailloux.git;

import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.jgit.lib.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.graph.GraphBuilder;
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
		final MutableGraph<E> mutableGraph = GraphBuilder.directed().build();
		while (!toConsider.isEmpty()) {
			final E current = toConsider.remove();
			mutableGraph.addNode(current);
			final Iterable<? extends E> parents = parentsFunction.successors(current);
			LOGGER.debug("Parents of {}: {}.", current.getName(), parents);
			for (E parent : parents) {
				mutableGraph.putEdge(current, parent);
				toConsider.add(parent);
			}
		}
		this.graph = ImmutableGraph.copyOf(mutableGraph);
	}

	/**
	 * TODO consider renaming, these are the ancestors to which everything points.
	 * (Starters? But wrong direction, misleading.)
	 *
	 * @return
	 */
	public Set<E> getRoots() {
		/**
		 * We could start from any given node and simply follow the successor
		 * (has-as-parent) relation, but that finds only one root. Git allows for
		 * multiple roots.
		 */
		return graph.nodes().stream().filter((n) -> graph.successors(n).isEmpty()).collect(Collectors.toSet());
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
