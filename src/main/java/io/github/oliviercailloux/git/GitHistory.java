package io.github.oliviercailloux.git;

import static com.google.common.base.Preconditions.checkArgument;

import java.time.Instant;
import java.util.Map;
import java.util.function.Predicate;

import org.eclipse.jgit.lib.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.graph.Graph;
import com.google.common.graph.Graphs;
import com.google.common.graph.ImmutableGraph;
import com.google.common.graph.MutableGraph;

/**
 * A history of commits. It can use the author dates, the commit dates, or dates
 * from any other source. It guarantees that every node in the graph has an
 * associated date.
 * <p>
 * An alternative design would admit partial date information (some nodes being
 * associated to no date). But this complicates use, and is really only useful,
 * probably, for push dates coming from GitHub, which are incomplete. Better,
 * for that specific use case, complete the information, as done in
 * {@link GitHubHistory}.
 */
public class GitHistory {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GitHistory.class);

	/**
	 * @param graph successors = children (time-based view)
	 * @param dates its keyset must contain all nodes of the graph.
	 */
	public static GitHistory create(Graph<ObjectId> graph, Map<ObjectId, Instant> dates) {
		return new GitHistory(graph, dates);
	}

	private final ImmutableGraph<ObjectId> graph;

	private final ImmutableMap<ObjectId, Instant> dates;

	private ImmutableSet<ObjectId> roots;

	private ImmutableSet<ObjectId> leaves;

	private GitHistory(Graph<ObjectId> graph, Map<ObjectId, Instant> dates) {
		this.graph = ImmutableGraph.copyOf(graph);
		this.dates = ImmutableMap.copyOf(Maps.filterKeys(dates, k -> graph.nodes().contains(k)));
		checkArgument(this.graph.nodes().equals(this.dates.keySet()));
		roots = null;
		leaves = null;
	}

	/**
	 * Returns a graph representing the has-as-child relation: the successors of a
	 * node are its children; following the successors (children) relation goes
	 * forward in time; following the predecessors (parents) relation goes back in
	 * time; a pair (a, b) in the graph represents a parent a and its child b.
	 * <p>
	 * This contradicts the usual git vision, where the edge represents the parent
	 * relation; but it is more intuitive that the “successors” relation goes
	 * forward in time, in other words, that the vertices flow in the direction of
	 * time. Furthermore, in graph theory, the usual convention is that the roots
	 * are the node without predecessors, and that the DAG flows outwards from the
	 * root (if the DAG is a tree, this is then called an out-tree or arborescence,
	 * in Wikipedia terminology). With the convention adopted here, the roots, in
	 * that sense, are also the first nodes in time, which makes sense intuitively.
	 * With the git convention (where the edges flow from parent to children), you
	 * have to choose between calling a “root” a node which has no successors but
	 * possibly predecessors, or calling a “root” a node which is at the end of
	 * time, which both feel intuitively awkward. I suppose this discrepancy between
	 * the common edge orientation and the time-flow is a usual problem with VCSes
	 * more generally (but I am not sure).
	 * https://math.stackexchange.com/questions/1374802
	 *
	 * @return a DAG (thus, irreflexive)
	 */
	public ImmutableGraph<ObjectId> getGraph() {
		return graph;
	}

	/**
	 * The children from which everything starts; the starting points in time of the
	 * git history; equivalently, the smallest set of nodes from which all nodes are
	 * reachable by following the “successors” (children) relation.
	 * <p>
	 * Usually there’s a single root, but git allows for <a href=
	 * "https://git-scm.com/docs/git-checkout#Documentation/git-checkout.txt---orphanltnewbranchgt">multiple
	 * roots</a>.
	 *
	 * @return empty iff the graph is empty.
	 */
	public ImmutableSet<ObjectId> getRoots() {
		if (roots == null) {
			/**
			 * We could start from any given node and simply follow the predecessor
			 * (parents) relation, but that finds only one root.
			 */
			roots = getGraph().nodes().stream().filter(n -> getGraph().predecessors(n).isEmpty())
					.collect(ImmutableSet.toImmutableSet());
		}
		return roots;
	}

	/**
	 * @return the nodes of the {@link #getGraph() graph} that have no children (no
	 *         successor); equivalently, the smallest set of nodes such that
	 *         starting from any node and following the “successors” (children)
	 *         relation necessarily ends in the set.
	 *
	 * @return empty iff the graph is empty.
	 */
	public ImmutableSet<ObjectId> getLeaves() {
		if (leaves == null) {
			leaves = getGraph().nodes().stream().filter(n -> getGraph().successors(n).isEmpty())
					.collect(ImmutableSet.toImmutableSet());
		}
		return leaves;
	}

	/**
	 * @throws IllegalArgumentException iff the given commit id is not a node of the
	 *                                  {@link #getGraph() graph}.
	 */
	public Instant getCommitDate(ObjectId commitId) {
		checkArgument(dates.containsKey(commitId));
		return dates.get(commitId);
	}

	/**
	 * @return a map whose key set equals the nodes of the {@link #getGraph() graph}
	 */
	public ImmutableMap<ObjectId, Instant> getCommitDates() {
		return dates;
	}

	public GitHistory filter(Predicate<ObjectId> predicate) {
		/**
		 * Two choices are reasonable here. Either I just compute the induced subgraph,
		 * or I try not to disconnect the graph. E.g., with a ← b ← c, filtering out b
		 * will disconnect the graph, whereas I could instead return a ← b. The drawback
		 * of this last solution is that it is difficult to implement, and it changes
		 * the parent relation.
		 */
		final ImmutableSet<ObjectId> kept = graph.nodes().stream().filter(predicate)
				.collect(ImmutableSet.toImmutableSet());
		final MutableGraph<ObjectId> outGraph = Graphs.inducedSubgraph(graph, kept);
		return create(outGraph, Maps.filterKeys(dates, predicate::test));
//		final ImmutableGraph.Builder<E> builder = GraphBuilder.directed().immutable();
//		kept.stream().forEach(builder::addNode);
//		LOGGER.info("Kept {}.", kept);
//
//		/**
//		 * I did not find any algorithm for this in JGraphT as of Feb, 2020.
//		 *
//		 * Roughly speaking, I want a filtered graph that has edges from the original
//		 * one only if a was reachable from b but is not deducible by transitive closure
//		 * from the reduct.
//		 */
//		for (E a : kept) {
//			LOGGER.debug("Treating {}.", a);
//			final Set<E> queue = new LinkedHashSet<>();
//			final Set<E> predecessorsA = graph.predecessors(a);
//			queue.addAll(predecessorsA);
//			LOGGER.debug("Enqueued (A) {}.", predecessorsA);
//			while (!queue.isEmpty()) {
//				final E b = queue.iterator().next();
//				queue.remove(b);
//				if (predicate.test(b)) {
//					builder.putEdge(b, a);
//					LOGGER.debug("Put {} → {}.", b, a);
//				} else {
//					final Set<E> predecessors = graph.predecessors(b);
//					queue.addAll(predecessors);
//					LOGGER.debug("Enqueued (B) {}.", predecessors);
//				}
//			}
//		}
//		/**
//		 * Here I may still have created too many paths. E.g. a <- b ← c and a ← f ← c,
//		 * filter out f, the algorithm above creates a spurious link a ← c. To solve
//		 * this, I should 1) ensure that the transitive reduction is the same as the
//		 * original graph, to start with (I suppose it is necessary with git); 2)
//		 * compute the transitive reduction when out of the algorithm, just before
//		 * returning.
//		 */
//		final ImmutableGraph<E> outGraph = builder.build();
//		return raw(outGraph, Maps.filterKeys(history.getCommitDates(), predicate::test));
	}

}
