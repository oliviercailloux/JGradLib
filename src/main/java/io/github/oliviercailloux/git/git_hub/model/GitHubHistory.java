package io.github.oliviercailloux.git.git_hub.model;

import static com.google.common.base.Verify.verify;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import org.eclipse.jgit.lib.ObjectId;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Multiset;
import com.google.common.collect.Sets;
import com.google.common.graph.Graph;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.Graphs;
import com.google.common.graph.ImmutableGraph;

import io.github.oliviercailloux.git.GitCurrentHistory;
import io.github.oliviercailloux.git.GitRawHistoryDecorator;

public class GitHubHistory extends GitRawHistoryDecorator<ObjectId> implements GitCurrentHistory<ObjectId> {

	public static GitHubHistory given(Graph<ObjectId> history, Map<ObjectId, Instant> commitDates,
			Map<ObjectId, Instant> pushedDates) {
		final GitRawHistory<ObjectId> raw = GitRawHistoryDecorator.raw(history, commitDates);
		return new GitHubHistory(raw, pushedDates);
	}

	/**
	 * Starting with nodes with no predecessor, computes the change to be brought to
	 * the given initial map so that children have weakly “smaller” values (meaning,
	 * not “greater” values) that their parents, proposing only changes that “lower”
	 * the initial values.
	 *
	 * Words such as “greater” or “smallest” are understood as defined by the given
	 * comparator.
	 *
	 * @return for a given key oid, indicates as value which is the oid that should
	 *         give its date, if the date of the key oid is to be changed (is itself
	 *         iff not to be changed). For each key oid, the value is the ancestor
	 *         (including itself) whose date value is “smallest”.
	 */
	private static ImmutableMap<ObjectId, ObjectId> getLoweringPatchForNonIncreasing(Graph<ObjectId> graph,
			Map<ObjectId, Instant> initial, Comparator<Instant> comparator) {
		final Map<ObjectId, ObjectId> originatorOfDate = new LinkedHashMap<>();
		final Set<ObjectId> nodes = graph.nodes();
		final Map<ObjectId, Instant> modifiedPushedDates = new LinkedHashMap<>(initial);

		final Queue<ObjectId> visitNext = new ArrayDeque<>();
		final Multiset<ObjectId> remainingVisits = HashMultiset.create();
		for (ObjectId node : nodes) {
			final int nbIncoming = graph.predecessors(node).size();
			remainingVisits.add(node, nbIncoming);
			if (nbIncoming == 0) {
				visitNext.add(node);
				originatorOfDate.put(node, node);
			}
		}
		verify(nodes.isEmpty() || !visitNext.isEmpty());

		while (!visitNext.isEmpty()) {
			final ObjectId predecessor = visitNext.remove();
			verify(originatorOfDate.containsKey(predecessor));
			final Instant predecessorDate = modifiedPushedDates.get(predecessor);
			final Set<ObjectId> successors = graph.successors(predecessor);
			for (ObjectId successor : successors) {
				final Instant successorDate = modifiedPushedDates.get(successor);
				/**
				 * Ensures the value associated to this successor is the “smallest” one among
				 * all ancestors of this successor seen so far and the original value of this
				 * successor.
				 */
				final boolean change = comparator.compare(predecessorDate, successorDate) < 0;
				if (change) {
					modifiedPushedDates.put(successor, predecessorDate);
					originatorOfDate.put(successor, originatorOfDate.get(predecessor));
				}

				final int before = remainingVisits.remove(successor, 1);
				if (before == 1) {
					visitNext.add(successor);
					if (!originatorOfDate.containsKey(successor)) {
						originatorOfDate.put(successor, successor);
					}
				}
			}
		}
		verify(originatorOfDate.keySet().equals(nodes));
		return ImmutableMap.copyOf(originatorOfDate);
	}

	private final ImmutableMap<ObjectId, Instant> pushedDates;
	private ImmutableMap<ObjectId, Instant> finalPushedDates;
	private ImmutableGraph<ObjectId> patchedKnowns;

	private GitHubHistory(GitRawHistory<ObjectId> raw, Map<ObjectId, Instant> pushedDates) {
		super(raw);
		this.pushedDates = ImmutableMap.copyOf(pushedDates);
		checkAndCompletePushDates();
	}

	/**
	 * From tips downwards, propagate a “ceiling” information: a parent of a child
	 * can have at most the push date of the child. This step is sufficient to patch
	 * possible bugs in the dates reported by GitHub. But it is not sufficient to
	 * obtain lower bounds for the missing values. As a second step, from roots
	 * upwards, propagate a “floor” information, in order to fill-in the missing
	 * pushedDate values.
	 */
	private void checkAndCompletePushDates() {
		final ImmutableGraph<ObjectId> graph = raw.getGraph();

		final ImmutableMap.Builder<ObjectId, Instant> initialBuilder = ImmutableMap.builder();
		initialBuilder.putAll(pushedDates);
		final Set<ObjectId> nodes = graph.nodes();
		final ImmutableSet<ObjectId> unobservedPushedDates = Sets.difference(nodes, pushedDates.keySet())
				.immutableCopy();
		for (ObjectId unobserved : unobservedPushedDates) {
			initialBuilder.put(unobserved, Instant.MAX);
		}
		final ImmutableMap<ObjectId, Instant> initial = initialBuilder.build();
		verify(initial.keySet().equals(nodes));

		final Comparator<Instant> comparator = Comparator.naturalOrder();

		final ImmutableMap<ObjectId, ObjectId> patch = getLoweringPatchForNonIncreasing(graph, initial, comparator);

		final ImmutableMap<ObjectId, Instant> modifiedPushedDates = nodes.stream().collect(ImmutableMap.toImmutableMap(
				Function.identity(), (n) -> pushedDates.containsKey(n) ? pushedDates.get(patch.get(n)) : Instant.MIN));
		final ImmutableMap<ObjectId, ObjectId> patchedKnownsMap = ImmutableMap
				.copyOf(Maps.filterKeys(patch, (o) -> pushedDates.containsKey(o) && !patch.get(o).equals(o)));
		final ImmutableSet<Entry<ObjectId, ObjectId>> entrySet = patchedKnownsMap.entrySet();
		final ImmutableGraph.Builder<ObjectId> graphBuilder = GraphBuilder.directed().immutable();
		for (Entry<ObjectId, ObjectId> patchEntry : entrySet) {
			graphBuilder.putEdge(patchEntry.getKey(), patchEntry.getValue());
		}
		patchedKnowns = graphBuilder.build();

		final ImmutableMap<ObjectId, ObjectId> unknownsPatch = getLoweringPatchForNonIncreasing(Graphs.transpose(graph),
				modifiedPushedDates, comparator.reversed());
		verify(unknownsPatch.keySet().stream().filter((o) -> !unknownsPatch.get(o).equals(o))
				.allMatch((o) -> !pushedDates.containsKey(o)));
		finalPushedDates = nodes.stream().collect(
				ImmutableMap.toImmutableMap(Function.identity(), (n) -> modifiedPushedDates.get(unknownsPatch.get(n))));
	}

	public ImmutableMap<ObjectId, Instant> getPushedDates() {
		return pushedDates;
	}

	public ImmutableSortedMap<Instant, ImmutableSet<ObjectId>> getRefsBySortedPushedDates(boolean patchedAndCompleted) {
//		return ImmutableSortedMap.copyOf(Multimaps.asMap(getRefsByPushedDates()));
		/** https://github.com/google/guava/issues/3750 */
		return ImmutableSortedMap.copyOf(Multimaps.asMap(getRefsByPushedDates(patchedAndCompleted)).entrySet().stream()
				.collect(ImmutableMap.toImmutableMap((e) -> e.getKey(), (e) -> ImmutableSet.copyOf(e.getValue()))));
	}

	public ImmutableSetMultimap<Instant, ObjectId> getRefsByPushedDates(boolean patchedAndCompleted) {
		final ImmutableMap<ObjectId, Instant> useDates = patchedAndCompleted ? finalPushedDates : pushedDates;
		return useDates.asMultimap().inverse();
	}

	public ImmutableMap<ObjectId, Instant> getCorrectedAndCompletedPushedDates() {
		return finalPushedDates;
	}

	/**
	 * @return the object ids that have been patched (changed compared to the
	 *         reported values) due to a suspected bug in GitHub.
	 */
	public ImmutableGraph<ObjectId> getPatchedKnowns() {
		return patchedKnowns;
	}

	/**
	 * Among the observed pushed dates, and after possible patching.
	 */
	public ImmutableSet<ObjectId> getPushedBeforeCommitted() {
		return pushedDates.keySet().stream().filter((o) -> finalPushedDates.get(o).isBefore(getCommitDate(o)))
				.collect(ImmutableSet.toImmutableSet());
	}

	@Override
	public GitHubHistory filter(Predicate<ObjectId> predicate) {
		return new GitHubHistory(filter(raw, predicate), Maps.filterKeys(pushedDates, predicate::test));
	}
}
