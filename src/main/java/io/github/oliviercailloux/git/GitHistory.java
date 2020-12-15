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

public class GitHistory {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GitHistory.class);

	public static GitHistory create(Graph<ObjectId> graph, Map<ObjectId, Instant> dates) {
		return new GitHistory(graph, dates);
	}

	private final ImmutableGraph<ObjectId> graph;

	private final ImmutableMap<ObjectId, Instant> dates;

	private GitHistory(Graph<ObjectId> graph, Map<ObjectId, Instant> dates) {
		this.graph = ImmutableGraph.copyOf(graph);
		this.dates = ImmutableMap.copyOf(dates);
	}

	public ImmutableGraph<ObjectId> getGraph() {
		return graph;
	}

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
	public ImmutableSet<ObjectId> getRoots() {
		/**
		 * We could start from any given node and simply follow the successor
		 * (has-as-parent) relation, but that finds only one root.
		 */
		final ImmutableSet<ObjectId> roots = getGraph().nodes().stream()
				.filter((n) -> getGraph().successors(n).isEmpty()).collect(ImmutableSet.toImmutableSet());
		return roots;
	}

	/**
	 * @return the nodes with no children (no predecessor), from which the
	 *         “successors” (parent-of) relation starts.
	 *
	 * @return empty iff the graph is empty.
	 */
	public ImmutableSet<ObjectId> getLeaves() {
		final ImmutableSet<ObjectId> leaves = getGraph().nodes().stream()
				.filter((n) -> getGraph().predecessors(n).isEmpty()).collect(ImmutableSet.toImmutableSet());
		return leaves;
	}

	public Instant getCommitDate(ObjectId commitId) {
		checkArgument(dates.containsKey(commitId));
		return dates.get(commitId);
	}

	public ImmutableMap<ObjectId, Instant> getCommitDates() {
		return dates;
	}

}
