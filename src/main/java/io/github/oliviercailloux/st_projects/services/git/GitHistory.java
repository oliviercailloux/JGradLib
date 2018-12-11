package io.github.oliviercailloux.st_projects.services.git;

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
		for (RevCommit revCommit : commits) {
			mutableGraph.addNode(revCommit);
			for (RevCommit parent : revCommit.getParents()) {
				mutableGraph.putEdge(revCommit, parent);
			}
		}
		this.graph = ImmutableGraph.copyOf(mutableGraph);
	}

	public Set<RevCommit> getFirsts() {
		/**
		 * We could start from any given node and simply follow the parent relation, but
		 * that finds only one root. Git allows for multiple roots.
		 */
		return graph.nodes().stream().filter((n) -> n.getParentCount() == 0).collect(Collectors.toSet());
	}

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GitHistory.class);

	/**
	 * @return a graph representing the parent of relation.
	 */
	public ImmutableGraph<RevCommit> getGraph() {
		return graph;
	}
}
