package io.github.oliviercailloux.git;

import static com.google.common.base.Preconditions.checkArgument;

import java.time.Instant;
import java.util.Arrays;
import java.util.function.Function;
import java.util.function.Predicate;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;

import com.google.common.base.Verify;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.graph.Graph;
import com.google.common.graph.Graphs;
import com.google.common.graph.ImmutableGraph;

import io.github.oliviercailloux.utils.Utils;

public class GitLocalHistory extends GitRawHistoryDecorator<RevCommit> implements GitCurrentHistory<RevCommit> {
	public static GitLocalHistory from(Iterable<RevCommit> commits) {
		final Graph<RevCommit> graph = Utils.asGraph((c) -> Arrays.asList(c.getParents()),
				ImmutableSet.copyOf(commits));
		return new GitLocalHistory(new GitRaw(graph));
	}

	static class GitRaw implements GitRawHistory<RevCommit> {

		private final ImmutableGraph<RevCommit> graph;

		GitRaw(Graph<RevCommit> graph) {
			this.graph = Utils.asImmutableGraph(graph);
			Verify.verify(!Graphs.hasCycle(graph));
		}

		@Override
		public ImmutableGraph<RevCommit> getGraph() {
			return graph;
		}

		@Override
		public ImmutableGraph<ObjectId> getRawGraph() {
			return Utils.asImmutableGraph(graph, r -> r);
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

	private ImmutableBiMap<RevCommit, ObjectId> commitToObjectId;

	GitLocalHistory(GitRawHistory<RevCommit> raw) {
		super(raw);
		commitToObjectId = null;
	}

	@Override
	public GitLocalHistory filter(Predicate<RevCommit> predicate) {
		final GitRawHistory<RevCommit> filtered = filter(raw, predicate);
		return new GitLocalHistory(filtered);
	}

	public RevCommit getCommit(ObjectId objectId) {
		if (commitToObjectId == null) {
			commitToObjectId = getGraph().nodes().stream().collect(ImmutableBiMap.toImmutableBiMap((c) -> c, (c) -> c));
		}
		final RevCommit revCommit = commitToObjectId.inverse().get(objectId);
		checkArgument(revCommit != null);
		return revCommit;
	}

	public Instant getCommitDateById(ObjectId objectId) {
		return getCommitDate(getCommit(objectId));
	}

}
