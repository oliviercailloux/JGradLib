package io.github.oliviercailloux.git;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Verify.verify;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Multiset;
import com.google.common.collect.Sets;
import com.google.common.graph.Graph;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.Graphs;
import com.google.common.graph.ImmutableGraph;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;
import java.util.function.Function;
import org.eclipse.jgit.lib.ObjectId;

/**
 * Interface GitHistory.
 *
 * + create(Map, Graph) (using either author or commit date from RevCommits, for
 * example)
 *
 * + getGraph() // unmodifiable
 *
 * + getRoot()
 *
 * Class GitHistorySimple.
 *
 * Class GitHistoryIncreasable.
 *
 * + addCommits(Stream<>).
 *
 * Class MultiDatesHistory.
 *
 * + create(Map authorDates, Map commitDates, Map pushsDates, Graph commits)
 *
 * - graph
 *
 * + getCommitHistory
 *
 * + getAuthorHistory
 *
 * + getPushHistory // all three the same graph
 *
 * + getPushedBeforeCommitted
 *
 * GitHubUtils::toCoherent(MultiDatesHistory): MultiDatesHistory.
 * <p>
 * I could have chosen to make this a more general MultiDatesHistory, with
 * authorDates, commitDates, pushDates, but then I also need one with the GH
 * specific interface where the provided push information is incomplete.
 *
 * @param <E>
 */
public class GitHubHistory {
	/**
	 * @param authorDates must be complete
	 * @param commitDates must be complete
	 * @param pushDates   may be incomplete
	 */
	public static GitHubHistory create(Graph<ObjectId> commits, Map<ObjectId, Instant> authorDates,
			Map<ObjectId, Instant> commitDates, Map<ObjectId, Instant> pushDates) {
		return new GitHubHistory(commits, authorDates, commitDates, pushDates);
	}

	private final ImmutableGraph<ObjectId> graph;
	private final ImmutableMap<ObjectId, Instant> authorDates;
	private final ImmutableMap<ObjectId, Instant> commitDates;
	private final ImmutableMap<ObjectId, Instant> pushDates;
	private ImmutableMap<ObjectId, Instant> finalPushDates;
	private ImmutableGraph<ObjectId> patchedKnowns;

	public GitHubHistory(Graph<ObjectId> commits, Map<ObjectId, Instant> authorDates,
			Map<ObjectId, Instant> commitDates, Map<ObjectId, Instant> pushDates) {
		this.graph = ImmutableGraph.copyOf(commits);
		this.authorDates = ImmutableMap.copyOf(Maps.filterKeys(authorDates, k -> graph.nodes().contains(k)));
		this.commitDates = ImmutableMap.copyOf(Maps.filterKeys(commitDates, k -> graph.nodes().contains(k)));
		this.pushDates = ImmutableMap.copyOf(Maps.filterKeys(pushDates, k -> graph.nodes().contains(k)));
		checkArgument(this.graph.nodes().equals(this.authorDates.keySet()));
		checkArgument(this.graph.nodes().equals(this.commitDates.keySet()));
		checkAndCompletePushDates();
	}

	public ImmutableGraph<ObjectId> getGraph() {
		return graph;
	}

	public GitHistory getAuthorHistory() {
		return GitHistory.create(graph, authorDates);
	}

	public GitHistory getCommitterHistory() {
		return GitHistory.create(graph, commitDates);
	}

	public ImmutableMap<ObjectId, Instant> getPushDates() {
		return pushDates;
	}

	public ImmutableMap<ObjectId, Instant> getConsistentPushDates() {
		return finalPushDates;
	}

	/**
	 * Many null values among the pushedDate information sent by GitHub. Also,
	 * there’s probably occasional bugs, where a commit is reportedly pushed before
	 * its parent (which I don’t think is possible). This class attempts to correct.
	 *
	 * Corrected by taking the most favorable hypothesis for the student (the one
	 * that yields the earliest push date) among those that do not put everything in
	 * question. Perhaps one precise definition of this is as follows (probably
	 * incorrect, better refer to the algorithm!). Conflicts in a set of commits:
	 * the pairs of commits taken in that set such that the earlier commit has a
	 * later date. Conflicting set: a set of commits that has at least one conflict.
	 * Related conflicting set: a conflicting set such that each conflict pair have
	 * a common child or a common parent that is in conflict with each of the
	 * elements of the pair. Minimal conflicting set: a related conflicting set such
	 * that any superset that is a related conflicting set has the same conflicts.
	 * [Perhaps unnecessary because a related conflicting set would be minimal?]
	 * Reconciliation of a related conflicting set: assignment of dates to each
	 * commit in the set such that it is no more a conflicting set when considering
	 * the assignment. Min reconciliation: the reconciliation that chooses pushed
	 * dates as early as possible among the reconciliations that leave at least one
	 * value unchanged among all the conflicts and does not touch the commits that
	 * are in no conflicting pairs.
	 *
	 * The resulting pushed dates, when they have been patched, are coherent but
	 * should be taken with caution. Only when {@link #getPatchedKnowns()} is empty
	 * should they be used, ideally. Even in that case, the completion (about the
	 * pushed dates that were missing in the reported data) is to be taken only as
	 * lower bounds.
	 *
	 */
	public GitHistory getConsistentPushHistory() {
		return GitHistory.create(graph, finalPushDates);
	}

	/**
	 * Starting with nodes with no successor, computes the change to be brought to
	 * the given initial map so that predecessors have weakly “smaller” values
	 * (meaning, not “greater” values) that their successors, proposing only changes
	 * that “lower” the initial values.
	 *
	 * Words such as “greater” or “smallest” are understood as defined by the given
	 * comparator.
	 *
	 * @return for a given key oid, indicates as value which is the oid that should
	 *         give its date, if the date of the key oid is to be changed (is itself
	 *         iff not to be changed). For each key oid, the value is the descendant
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
			final int nbIncoming = graph.successors(node).size();
			remainingVisits.add(node, nbIncoming);
			if (nbIncoming == 0) {
				visitNext.add(node);
				originatorOfDate.put(node, node);
			}
		}
		verify(nodes.isEmpty() || !visitNext.isEmpty());

		while (!visitNext.isEmpty()) {
			final ObjectId successor = visitNext.remove();
			verify(originatorOfDate.containsKey(successor));
			final Instant successorDate = modifiedPushedDates.get(successor);
			for (ObjectId predecessor : graph.predecessors(successor)) {
				final Instant predecessorDate = modifiedPushedDates.get(predecessor);
				/**
				 * Ensures the value associated to this predecessor is the “smallest” one among
				 * all descendants of this predecessor seen so far and the original value of
				 * this predecessor.
				 */
				final boolean change = comparator.compare(successorDate, predecessorDate) < 0;
				if (change) {
					modifiedPushedDates.put(predecessor, successorDate);
					originatorOfDate.put(predecessor, originatorOfDate.get(successor));
				}

				final int before = remainingVisits.remove(predecessor, 1);
				if (before == 1) {
					visitNext.add(predecessor);
					if (!originatorOfDate.containsKey(predecessor)) {
						originatorOfDate.put(predecessor, predecessor);
					}
				}
			}
		}
		verify(originatorOfDate.keySet().equals(nodes));
		return ImmutableMap.copyOf(originatorOfDate);
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
		final ImmutableMap.Builder<ObjectId, Instant> initialBuilder = ImmutableMap.builder();
		initialBuilder.putAll(pushDates);
		final Set<ObjectId> nodes = graph.nodes();
		final ImmutableSet<ObjectId> unobservedPushedDates = Sets.difference(nodes, pushDates.keySet()).immutableCopy();
		for (ObjectId unobserved : unobservedPushedDates) {
			initialBuilder.put(unobserved, Instant.MAX);
		}
		final ImmutableMap<ObjectId, Instant> initial = initialBuilder.build();
		verify(initial.keySet().equals(nodes));

		final Comparator<Instant> comparator = Comparator.naturalOrder();

		final ImmutableMap<ObjectId, ObjectId> patch = getLoweringPatchForNonIncreasing(graph, initial, comparator);

		final ImmutableMap<ObjectId, Instant> modifiedPushedDates = nodes.stream().collect(ImmutableMap.toImmutableMap(
				Function.identity(), n -> pushDates.containsKey(n) ? pushDates.get(patch.get(n)) : Instant.MIN));

		{
			final ImmutableMap<ObjectId, ObjectId> patchedKnownsMap = ImmutableMap
					.copyOf(Maps.filterKeys(patch, o -> pushDates.containsKey(o) && !patch.get(o).equals(o)));
			final ImmutableSet<Entry<ObjectId, ObjectId>> entrySet = patchedKnownsMap.entrySet();
			final ImmutableGraph.Builder<ObjectId> graphBuilder = GraphBuilder.directed().immutable();
			for (Entry<ObjectId, ObjectId> patchEntry : entrySet) {
				graphBuilder.putEdge(patchEntry.getKey(), patchEntry.getValue());
			}
			patchedKnowns = graphBuilder.build();
		}

		final ImmutableMap<ObjectId, ObjectId> unknownsPatch = getLoweringPatchForNonIncreasing(Graphs.transpose(graph),
				modifiedPushedDates, comparator.reversed());
		verify(unknownsPatch.keySet().stream().filter((o) -> !unknownsPatch.get(o).equals(o))
				.allMatch((o) -> !pushDates.containsKey(o)));
		finalPushDates = nodes.stream().collect(
				ImmutableMap.toImmutableMap(Function.identity(), n -> modifiedPushedDates.get(unknownsPatch.get(n))));
	}

	/**
	 * @return the object ids that have been patched (changed compared to the
	 *         reported values) due to a suspected bug in GitHub.
	 */
	public ImmutableGraph<ObjectId> getPatchedPushCommits() {
		return patchedKnowns;
	}
}
