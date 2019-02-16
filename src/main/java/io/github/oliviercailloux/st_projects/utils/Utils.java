package io.github.oliviercailloux.st_projects.utils;

import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import com.google.common.graph.Graph;
import com.google.common.graph.Graphs;
import com.google.common.graph.MutableGraph;

public class Utils {

	public static <E> Queue<E> topologicalSort(Graph<E> prerequisites, Set<E> roots) {
		for (E root : roots) {
			prerequisites.predecessors(root).isEmpty();
		}

		final Queue<E> sorted = new LinkedList<>();
		final MutableGraph<E> temp = Graphs.copyOf(prerequisites);
		final Queue<E> foundRoots = new LinkedList<>(roots);
		LOGGER.debug("Starting with {}.", foundRoots);
		while (!foundRoots.isEmpty()) {
			final E cur = foundRoots.remove();
			sorted.add(cur);
			final ImmutableSet<E> s = ImmutableSet.copyOf(prerequisites.successors(cur));
			LOGGER.debug("Considering {} and {}.", cur, s);
			for (E succ : s) {
				temp.removeEdge(cur, succ);
				final Set<E> p = temp.predecessors(succ);
				LOGGER.debug("Considering successor {} with {}.", succ, p);
				if (p.isEmpty()) {
					foundRoots.add(succ);
				}
			}
		}
		assert temp.edges().isEmpty() : "Remaining: " + temp.edges();
		final SetView<E> missed = Sets.difference(prerequisites.nodes(), ImmutableSet.copyOf(sorted));
		assert missed.isEmpty() : "Missed: " + missed;
		return sorted;
	}

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(Utils.class);

}
