package io.github.oliviercailloux.git.git_hub.model;

import static com.google.common.base.Preconditions.checkArgument;

import java.time.Instant;
import java.util.Map;

import org.eclipse.jgit.lib.ObjectId;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Sets;
import com.google.common.collect.Streams;
import com.google.common.graph.ImmutableGraph;
import com.google.common.graph.Traverser;

import io.github.oliviercailloux.git.GitGenericHistory;

public class GitHubHistory {

	public static GitHubHistory given(GitGenericHistory<ObjectId> history, Map<ObjectId, Instant> pushedDates) {
		return new GitHubHistory(history, pushedDates);
	}

	private GitHubHistory(GitGenericHistory<ObjectId> history, Map<ObjectId, Instant> pushedDates) {
		this.history = history;
		this.pushedDates = ImmutableMap.copyOf(pushedDates);
	}

	private final GitGenericHistory<ObjectId> history;
	private final ImmutableMap<ObjectId, Instant> pushedDates;

	public GitGenericHistory<ObjectId> getHistory() {
		return history;
	}

	public ImmutableMap<ObjectId, Instant> getPushedDates() {
		return pushedDates;
	}

	public ImmutableMap<ObjectId, Instant> getPushedDatesWithDeductions() {
		final ImmutableMap<ObjectId, Instant> observedPushedDates = ImmutableMap.copyOf(pushedDates);
		final ImmutableMap.Builder<ObjectId, Instant> deducedPushedDates = ImmutableMap.builder();
		deducedPushedDates.putAll(observedPushedDates);
		for (ObjectId oid : Sets.difference(history.getGraph().nodes(), observedPushedDates.keySet())) {
			final ImmutableGraph<ObjectId> graph = history.getGraph();
			final Iterable<ObjectId> thoseChildren = Traverser.forGraph(graph::predecessors).breadthFirst(oid);
			final Instant minPushedDateAmongThoseChildren = Streams.stream(thoseChildren)
					.map((o) -> observedPushedDates.getOrDefault(o, Instant.MAX)).min(Instant::compareTo)
					.orElse(Instant.MAX);
			checkArgument(!minPushedDateAmongThoseChildren.equals(Instant.MAX));
			deducedPushedDates.put(oid, minPushedDateAmongThoseChildren);
		}

		return deducedPushedDates.build();
	}

	public ImmutableSortedMap<Instant, ImmutableSet<ObjectId>> getRefsBySortedPushedDates() {
//		return ImmutableSortedMap.copyOf(Multimaps.asMap(getRefsByPushedDates()));
		/** https://github.com/google/guava/issues/3750 */
		return ImmutableSortedMap.copyOf(Multimaps.asMap(getRefsByPushedDates()).entrySet().stream()
				.collect(ImmutableMap.toImmutableMap((e) -> e.getKey(), (e) -> ImmutableSet.copyOf(e.getValue()))));
	}

	public ImmutableSetMultimap<Instant, ObjectId> getRefsByPushedDates() {
		return pushedDates.asMultimap().inverse();
	}
}
