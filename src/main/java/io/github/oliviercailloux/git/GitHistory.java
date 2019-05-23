package io.github.oliviercailloux.git;

import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.graph.GraphBuilder;
import com.google.common.graph.ImmutableGraph;
import com.google.common.graph.MutableGraph;

public class GitHistory {
	public static GitHistory from(Iterable<RevCommit> commits) {
		return new GitHistory(commits);
	}

	private final ImmutableGraph<RevCommit> graph;

	private GitHistory(Iterable<RevCommit> commits) {
		MutableGraph<RevCommit> mutableGraph = GraphBuilder.directed().build();
		for (RevCommit child : commits) {
			mutableGraph.addNode(child);
			final RevCommit[] parents = child.getParents();
			LOGGER.debug("Parents of {}: {}.", child.getName(), parents);
			for (RevCommit parent : parents) {
				mutableGraph.putEdge(child, parent);
			}
		}
		this.graph = ImmutableGraph.copyOf(mutableGraph);
	}

	public Set<RevCommit> getRoots() {
		/**
		 * We could start from any given node and simply follow the successor
		 * (has-as-parent) relation, but that finds only one root. Git allows for
		 * multiple roots.
		 */
		return graph.nodes().stream().filter((n) -> n.getParentCount() == 0).collect(Collectors.toSet());
	}

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GitHistory.class);

	/**
	 * @return a graph representing the has-as-parent (child-of) relation: the
	 *         successors of a node are its parents; following the successors
	 *         relation (child-of) goes back in time; a pair (a, b) represents a
	 *         child a and its parent b.
	 */
	public ImmutableGraph<RevCommit> getGraph() {
		return graph;
	}
}
