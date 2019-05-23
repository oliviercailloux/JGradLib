package io.github.oliviercailloux.git.git_hub.services;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import java.io.IOException;
import java.time.Instant;
import java.time.Period;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import javax.ws.rs.WebApplicationException;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Comparators;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Range;
import com.google.common.collect.Sets;
import com.google.common.graph.EndpointPair;
import com.google.common.graph.Graph;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.Graphs;
import com.google.common.graph.MutableGraph;

import io.github.oliviercailloux.git.Client;
import io.github.oliviercailloux.git.GitHistory;
import io.github.oliviercailloux.git.git_hub.model.GitHubToken;
import io.github.oliviercailloux.git.git_hub.model.v3.Event;
import io.github.oliviercailloux.git.git_hub.model.v3.EventType;
import io.github.oliviercailloux.git.git_hub.model.v3.PayloadCommitDescription;
import io.github.oliviercailloux.git.git_hub.model.v3.PushEvent;
import io.github.oliviercailloux.git.git_hub.model.v3.PushPayload;
import io.github.oliviercailloux.utils.Utils;

public class GitHubTimelineReader {
	public GitHubTimelineReader() {
		receivedAt = new LinkedHashMap<>();
		fetcherSupplier = () -> {
			try {
				return GitHubFetcherV3.using(GitHubToken.getRealInstance());
			} catch (IOException e) {
				throw new IllegalStateException(e);
			}
		};
		veryFirstBranchEvent = null;
		createdButNoContent = true;
		firstPushEvent = null;
		rangeFirstCommits = null;
		history = null;
		commitsBeforeFirstPush = null;
	}

	public Map<ObjectId, Range<Instant>> getReceptionRanges(Client client) throws WebApplicationException {
		checkState(firstPushEvent == null, "This object is not meant to be re-used.");
		checkArgument(client.hasContentCached());
		/** Also checks does not throw. */
		history = client.getAllHistoryCached();

		fetchEvents(client);

		LOGGER.debug("Checking given {}.", events);

		computeFirstBranchEvent();

		firstPushEvent = events.stream().filter((e) -> e.getType() == EventType.PUSH_EVENT).map(Event::asPushEvent)
				.findFirst();
		final Optional<ObjectId> firstPushedOIdKnown;
		final ImmutableList<ObjectId> suspectCommits;
		if (firstPushEvent.isPresent()) {
			final PushEvent push = firstPushEvent.get();
			final PushPayload pushPayload = push.getPushPayload();
			final List<PayloadCommitDescription> pushedCommits = pushPayload.getCommits();
			firstPushedOIdKnown = Optional.of(pushedCommits.get(0).getSha());
			/** Because of possible bug in GitHub, see below. */
			suspectCommits = pushedCommits.subList(0, pushedCommits.size() - 1).stream()
					.map(PayloadCommitDescription::getSha).collect(ImmutableList.toImmutableList());
			LOGGER.info("Suspects: {}.", suspectCommits);
		} else {
			firstPushedOIdKnown = Optional.empty();
			suspectCommits = ImmutableList.of();
		}

		final Optional<RevCommit> firstPushedCommitKnown;
		if (firstPushedOIdKnown.isPresent()) {
			try {
				firstPushedCommitKnown = Optional.of(client.getCommit(firstPushedOIdKnown.get()));
			} catch (IOException e) {
				throw new IllegalStateException(e);
			}
		} else {
			firstPushedCommitKnown = Optional.empty();
		}

		final boolean noHistory = history.getGraph().nodes().isEmpty();
		checkState(Utils.implies(createdButNoContent, noHistory));
		checkState(Utils.implies(noHistory, firstPushEvent.isEmpty()));

		if (veryFirstBranchEvent.isPresent()) {
			rangeFirstCommits = Range.singleton(veryFirstBranchEvent.get().getCreatedAt());
		} else {
			if (events.isEmpty()) {
				rangeFirstCommits = Range.open(Instant.MIN, Instant.now().minus(Period.ofMonths(1)));
			} else {
				final Event firstEvent = events.stream().findFirst().get();
				rangeFirstCommits = Range.open(Instant.MIN, firstEvent.getCreatedAt());
			}
		}

		{
			final Set<RevCommit> commitsBeforeFirstPushBuilt;
			if (firstPushedCommitKnown.isPresent()) {
				commitsBeforeFirstPushBuilt = new LinkedHashSet<>(
						Graphs.reachableNodes(history.getGraph(), firstPushedCommitKnown.get()));
				commitsBeforeFirstPushBuilt.remove(firstPushedCommitKnown.get());
			} else {
				commitsBeforeFirstPushBuilt = history.getGraph().nodes();
			}
			commitsBeforeFirstPush = ImmutableSet.copyOf(commitsBeforeFirstPushBuilt);
		}
		LOGGER.debug("Commits before first push: {}.", commitsBeforeFirstPush);

		for (RevCommit unknownCommit : commitsBeforeFirstPush) {
			receivedAt.put(unknownCommit, rangeFirstCommits);
		}

		final ImmutableSet<PushEvent> pushEvents = events.stream()
				.filter((e) -> e.getType().equals(EventType.PUSH_EVENT)).map(Event::asPushEvent)
				.collect(ImmutableSet.toImmutableSet());
		for (PushEvent pushEvent : pushEvents) {
			final Instant createdAt = pushEvent.getCreatedAt();
			final Range<Instant> reception = Range.singleton(createdAt);
			final List<PayloadCommitDescription> commits = pushEvent.getPushPayload().getCommits();
			for (PayloadCommitDescription descr : commits) {
				final ObjectId sha = descr.getSha();
				final Range<Instant> previous = receivedAt.put(sha, reception);
				checkState(previous == null, String.format("Sha: %s, previous: %s", sha, previous));
			}
		}

		/**
		 * The logic above marks all the ancestors of the first commit referenced in the
		 * first push as unknown. This is incorrect: another branch could exist,
		 * containing commits not ancestors of the first push and not referenced in any
		 * push. Note also that a possible bug in GitHub makes it possible that such an
		 * unknown commit have an ancestor that indeed has been seen (see
		 * #computeFirstBranchEvent()).
		 */
		final Set<RevCommit> allCommits = history.getGraph().nodes();
		final Set<ObjectId> idsKnown = receivedAt.keySet();
		final ImmutableSet<RevCommit> unknown = Sets.difference(allCommits, idsKnown).immutableCopy();
		for (RevCommit unknownCommit : unknown) {
			final Range<Instant> previous = receivedAt.put(unknownCommit, rangeFirstCommits);
			checkState(previous == null, String.format("Sha: %s, previous: %s", unknownCommit, previous));
		}

		for (EndpointPair<RevCommit> edge : history.getGraph().edges()) {
			final RevCommit child = edge.source();
			final RevCommit parent = edge.target();
			final Range<Instant> childTime = receivedAt.get(child);
			final Range<Instant> parentTime = receivedAt.get(parent);
			if (!suspectCommits.contains(child) && !suspectCommits.contains(parent)) {
				checkState(leq(parentTime, childTime), String.format("Parent %s after child %s: %s > %s",
						parent.getName(), child.getName(), parentTime, childTime));
			}
		}

		MutableGraph<ObjectId> oGraph = GraphBuilder.directed().build();
		for (EndpointPair<RevCommit> edge : history.getGraph().edges()) {
			oGraph.putEdge(edge.source(), edge.target());
		}
		final Graph<ObjectId> transitiveClosure = Graphs.transitiveClosure(oGraph);

		final Comparator<ObjectId> comparingReceptionTimeLower = Comparator
				.comparing((o) -> receivedAt.get(o).lowerEndpoint());
		final Comparator<ObjectId> comparingReceptionTimeUpper = Comparator
				.comparing((o) -> receivedAt.get(o).upperEndpoint());
		for (ObjectId suspectCommit : suspectCommits) {
			final Range<Instant> suspectInterval = receivedAt.get(suspectCommit);

			final ImmutableSet<ObjectId> children = transitiveClosure.predecessors(suspectCommit).stream()
					.filter((c) -> !suspectCommits.contains(c)).collect(ImmutableSet.toImmutableSet());
//			final ImmutableSet<Range<Instant>> nextIntervals = children.stream().map((o) -> receivedAt.get(o))
//					.collect(ImmutableSet.toImmutableSet());
//			final ImmutableSet<Instant> nextLowerEndpoints = nextIntervals.stream().map(Range::lowerEndpoint)
//					.collect(ImmutableSet.toImmutableSet());
//			final Instant minLower = Collections.min(nextLowerEndpoints);
			if (children.isEmpty()) {
				continue;
			}
			final ObjectId minLower = Collections.min(children, comparingReceptionTimeLower);
			final ObjectId minUpper = Collections.min(children, comparingReceptionTimeUpper);
			final Range<Instant> minRange = receivedAt.get(minLower);
			/**
			 * This should follow from the children being ordered correctly, as checked
			 * above.
			 */
			assert minRange.equals(receivedAt.get(minUpper));
			if (!leq(suspectInterval, minRange)) {
				LOGGER.warn("Rectifying suspect commit: {}, from {} to {}.", suspectCommit, suspectInterval, minRange);
				receivedAt.put(suspectCommit, minRange);
			}
		}

		final ImmutableSet<ObjectId> diff = Sets.symmetricDifference(allCommits, idsKnown).immutableCopy();

		checkState(diff.isEmpty(),
				String.format("All commits: %s; known times: %s; unknown: %s.", allCommits, receivedAt, unknown));
		return receivedAt;
	}

	/**
	 * [1, 3] ≤ [2, 4]
	 */
	private boolean leq(Range<Instant> i1, Range<Instant> i2) {
		return (i1.lowerEndpoint().compareTo(i2.lowerEndpoint()) <= 0)
				&& (i1.upperEndpoint().compareTo(i2.upperEndpoint()) <= 0);
	}

	public ImmutableSet<RevCommit> getCommitsBeforeFirstPush() {
		checkState(commitsBeforeFirstPush != null);
		return commitsBeforeFirstPush;
	}

	public ImmutableMap<ObjectId, Instant> getReceivedAtLowerBounds() {
		return receivedAt.entrySet().stream()
				.collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, (e) -> e.getValue().lowerEndpoint()));
	}

	private void computeFirstBranchEvent() {
		/**
		 * The typical scenario as seen from the received list of events is as follows.
		 * A Create Event with ref "null", ref_type "repository" happens when the
		 * repository gets created, that’s the first event. It is immediately followed
		 * (same or next second) by a member event which adds the owner of the
		 * repository. Then followed (can be much later) by a create event, ref
		 * "master", ref_type "branch", which happens when the branch master is created.
		 * May be followed, in case of a repository created through GitHub Classroom
		 * starting from another repository (or, I suppose, in case of a fork), by other
		 * tag and branch events. Then followed by a push event (which I have seen to
		 * happen seven minutes later), which references as "before" the first commit in
		 * the branch designated in "payload/ref" (e.g. "refs/heads/my-branch"). I
		 * suppose that the creation of branch corresponds to the first user push, and
		 * the first push event corresponds to the second one. In summary, the important
		 * stuff here is: create event, with its ref_type and ref; member event, with
		 * the name of the person added (in "payload": "action" = "added" and
		 * "member"/"login"); and push event, with before and commits (in payload). The
		 * create event of the master branch may hide several commits, not just the one
		 * that can be retrieved using the next "before".
		 *
		 * I have seen a case where a PushEvent listed two commits, the first one of
		 * which was not actually pushed and existed long before in the repository, the
		 * second one being effectively the (only) really pushed commit. The pushed
		 * commit was a merge and the first one listed was one of its parent. Maybe it’s
		 * a GitHub bug. In this case, the received times deduced by this class are
		 * incorrect. I don’t think it’s possible to avoid this consequence.
		 */

		/** If there is a create event, must be among the first two. */
		final long nbCreateRepositoryEvents = events.stream().map(Event::getType)
				.filter(Predicate.isEqual(EventType.CREATE_REPOSITORY_EVENT)).count();
		checkState(nbCreateRepositoryEvents <= 1);
		if (nbCreateRepositoryEvents == 0) {
			veryFirstBranchEvent = Optional.empty();
			createdButNoContent = false;
			return;
		}

		/**
		 * Let’s check that the create repo is one of the first events. When GitHub
		 * Classroom creates repositories, I have seen these two events happen in the
		 * order <create repo, member> and, much more rarely, <member, create repo>. The
		 * first two events happen at the same second or with one second delay. I have
		 * seen the create branch even happen three days later: I assume it corresponds
		 * to a manual operation. I have seen the first push follow the first create
		 * branch only seven minutes later, so maybe these are two different manual
		 * operations.
		 */
		checkState(events.get(0).getType() == EventType.CREATE_REPOSITORY_EVENT
				|| (events.get(0).getType() == EventType.MEMBER_EVENT
						&& events.get(1).getType() == EventType.CREATE_REPOSITORY_EVENT));

		/**
		 * We want to find the first create branch event after the create repository
		 * event or happening simultaneously to it. Given our checks so far, this is
		 * equivalent to finding the first create branch event.
		 */
		final Optional<Event> found = events.stream().filter((e) -> e.getType() == EventType.CREATE_BRANCH_EVENT)
				.findFirst();
		/**
		 * There must be no push before the very first branch event.
		 */
		checkState(
				events.stream().map(Event::getType).takeWhile(Predicate.isEqual(EventType.CREATE_BRANCH_EVENT).negate())
						.noneMatch(Predicate.isEqual(EventType.PUSH_EVENT)));

		/**
		 * We should associate to each branch its time of creation, rather than just
		 * remember the time of creation of the very first branch (which is master, I
		 * suppose). Because push events may have a before that refers to a different
		 * branch than master.
		 */
		veryFirstBranchEvent = found;
		createdButNoContent = found.isEmpty();
	}

	private final Map<ObjectId, Range<Instant>> receivedAt;

	public Optional<Range<Instant>> getReceivedAt(ObjectId id) {
		return Optional.ofNullable(receivedAt.get(id));
	}

	private void fetchEvents(Client client) throws WebApplicationException {
		final ImmutableList<Event> eventsReversed;
		try (GitHubFetcherV3 fetcher = fetcherSupplier.get()) {
			eventsReversed = fetcher.getEvents(client.getCoordinates());
		}
		/**
		 * I have seen a case where the last event sent by GitHub was a MEMBER_EVENT,
		 * and the penultimate one was a CREATE_EVENT one second before, breaking the
		 * expected ordering (the last event sent being expected to be the oldest).
		 */
//		final Stream<Instant> eventsCreation = eventsReversed.stream().map(Event::getCreatedAt);
//		checkState(Comparators.isInOrder(eventsCreation::iterator, Comparator.<Instant>naturalOrder().reversed()));
//		events = eventsReversed.reverse();
		events = ImmutableList.sortedCopyOf(Comparator.comparing(Event::getCreatedAt), eventsReversed);
	}

	/**
	 * @param events earliest first.
	 */
	void setEvents(List<Event> events) {
		final Stream<Instant> eventsCreation = events.stream().map(Event::getCreatedAt);
		checkState(Comparators.isInOrder(eventsCreation::iterator, Comparator.<Instant>naturalOrder()));
		this.events = ImmutableList.copyOf(events);
	}

	void setGitHubFetcherSupplier(Supplier<GitHubFetcherV3> supplier) {
		fetcherSupplier = supplier;
	}

	private Supplier<GitHubFetcherV3> fetcherSupplier;
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GitHubTimelineReader.class);
	/**
	 * earliest first
	 */
	private ImmutableList<Event> events;
	/**
	 * The very first branch event in the life of this repository. May differ from
	 * the first branch event among the limited set of events available from GitHub.
	 */
	private Optional<Event> veryFirstBranchEvent;
	/**
	 * The repo might have been just created but never pushed to and the main branch
	 * might not have been created yet. <code>true</code> iff the events contain a
	 * create repository event, but no branch event and (thus) no push event.
	 */
	private boolean createdButNoContent;
	private Optional<PushEvent> firstPushEvent;
	private Range<Instant> rangeFirstCommits;
	private GitHistory history;
	private ImmutableSet<RevCommit> commitsBeforeFirstPush;
}
