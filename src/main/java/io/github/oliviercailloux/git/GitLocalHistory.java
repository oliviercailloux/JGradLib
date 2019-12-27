package io.github.oliviercailloux.git;

import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.function.Function;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.graph.EndpointPair;
import com.google.common.graph.Graph;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.ImmutableGraph;

import io.github.oliviercailloux.utils.Utils;

public class GitLocalHistory extends GitRawHistoryDecorator<RevCommit> implements GitHistory<RevCommit> {
	public static GitLocalHistory from(Iterable<RevCommit> commits) {
		final Graph<RevCommit> graph = Utils.asGraph((c) -> Arrays.asList(c.getParents()),
				ImmutableSet.copyOf(commits));
		return new GitLocalHistory(new GitRaw(graph));
	}

	private static class GitRaw implements GitRawHistory<RevCommit> {

		private final ImmutableGraph<RevCommit> graph;

		private GitRaw(Graph<RevCommit> graph) {
			this.graph = Utils.asImmutableGraph(graph);
		}

		@Override
		public ImmutableGraph<RevCommit> getGraph() {
			return graph;
		}

		@Override
		public ImmutableGraph<ObjectId> getRawGraph() {
			final ImmutableGraph.Builder<ObjectId> builder = GraphBuilder.directed().immutable();
			final Set<EndpointPair<RevCommit>> edges = getGraph().edges();
			for (EndpointPair<RevCommit> endpointPair : edges) {
				builder.putEdge(endpointPair.nodeU(), endpointPair.nodeV());
			}
			return builder.build();
		}

		@Override
		public Instant getCommitDate(RevCommit objectId) {
			return GitUtils.getCreationTime(objectId).toInstant();
		}

		@Override
		public ImmutableMap<RevCommit, Instant> getCommitDates() {
			return getGraph().nodes().stream()
					.collect(ImmutableMap.toImmutableMap(Function.identity(), this::getCommitDate));
		}

	}

	private GitLocalHistory(GitRaw raw) {
		super(raw);
	}

}
