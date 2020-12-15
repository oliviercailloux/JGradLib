package io.github.oliviercailloux.git;

import java.time.Instant;
import java.util.Map;

import org.eclipse.jgit.lib.ObjectId;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.graph.Graph;
import com.google.common.graph.ImmutableGraph;

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
public interface GitHistory {

	/**
	 * @param graph
	 * @param dates its keyset must contain all nodes of the graph.
	 */
	public static GitHistory create(Graph<ObjectId> graph, Map<ObjectId, Instant> dates) {
		return GitHistoryImpl.create(graph, dates);
	}

	/**
	 * @return the nodes of the {@link #getGraph() graph} that have no children (no
	 *         predecessor); equivalently, the smallest set of nodes from which all
	 *         nodes are reachable by following the “successors” (parent-of)
	 *         relation.
	 *
	 * @return empty iff the graph is empty.
	 */
	public ImmutableSet<ObjectId> getLeaves();

	/**
	 * @throws IllegalArgumentException iff the given commit id is not a node of the
	 *                                  {@link #getGraph() graph}.
	 */
	public Instant getCommitDate(ObjectId commitId);

	/**
	 * @return a map whose key set equals the nodes of the {@link #getGraph() graph}
	 */
	public ImmutableMap<ObjectId, Instant> getCommitDates();

	/**
	 * Returns a graph representing the has-as-parent relation: the successors of a
	 * node are its parents; following the successors (parent) relation goes back in
	 * time; following the predecessors (child) relation goes back in time; a pair
	 * (a, b) in the graph represents a child a and its parent b.
	 *
	 * @return a DAG (thus, irreflexive)
	 */
	public ImmutableGraph<ObjectId> getGraph();

	/**
	 * The parents to which everything points; the starting points in time of the
	 * git history. Note that this departs from the usual forest-view of a DAG,
	 * where the edges go away from the root: here they go towards the roots (as is
	 * usual when representing a Git history). (Usually there’s a single root, but
	 * git allows for <a href=
	 * "https://git-scm.com/docs/git-checkout#Documentation/git-checkout.txt---orphanltnewbranchgt">multiple
	 * roots</a>.)
	 * <p>
	 * Root is typically first in time and leaf last, a convention adopted here;
	 * both in git and in GT. However, in GT, the convention is that the vertices
	 * flow in the direction of time, which git breaks. When it has only one root
	 * (most common case by far), a usual convention in graph theory consists in
	 * making the DAG flows outwards from the root (if the DAG is a tree, this is
	 * then called an out-tree or arborescence, in Wikipedia terminology); but git
	 * breaks it, or perhaps VCS more generally, I don’t know.
	 * https://math.stackexchange.com/questions/1374802
	 *
	 * @return empty iff the graph is empty.
	 */
	public ImmutableSet<ObjectId> getRoots();

}
