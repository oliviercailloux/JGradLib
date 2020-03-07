package io.github.oliviercailloux.git;

import java.time.Instant;

import org.eclipse.jgit.lib.ObjectId;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.graph.ImmutableGraph;

public interface GitHistory<E extends ObjectId> {

	/**
	 * @return a graph representing the has-as-parent (child-of) relation: the
	 *         successors of a node are its parents; following the successors
	 *         relation (child-of) goes back in time; a pair (a, b) represents a
	 *         child a and its parent b.
	 */
	public ImmutableGraph<E> getGraph();

	/**
	 * @return the same graph as {@link #getGraph()} but typed differently,
	 *         permitting to search for example for the successors of a given
	 *         ObjectId even when not knowing the corresponding instance of kind E.
	 */
	public ImmutableGraph<ObjectId> getRawGraph();

	/**
	 * The parents to which everything points; the starting points in time of the
	 * git history. Note that this departs from the usual forest-view of a DAG,
	 * where the edges go away from the root: here they go towards the roots (as is
	 * usual when representing a Git history). (Usually there’s a single root, but
	 * git allows for <a href=
	 * "https://git-scm.com/docs/git-checkout#Documentation/git-checkout.txt---orphanltnewbranchgt">multiple
	 * roots</a>.)
	 *
	 * @return empty iff the graph is empty.
	 */
	public ImmutableSet<E> getRoots();

	/**
	 * @return the nodes with no children (no predecessor), from which the
	 *         “successors” (parent-of) relation starts; the most recent node on
	 *         each branch.
	 *
	 * @return empty iff the graph is empty.
	 */
	public ImmutableSet<E> getTips();

	public ImmutableMap<E, Instant> getCommitDates();

	public Instant getCommitDate(E objectId);
}