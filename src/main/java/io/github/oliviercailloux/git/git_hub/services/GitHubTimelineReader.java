package io.github.oliviercailloux.git.git_hub.services;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import java.io.IOException;
import java.time.Instant;
import java.time.Period;
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
import com.google.common.graph.Graphs;

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
		Optional<ObjectId> firstPushedOIdKnown = null;
		if (firstPushEvent.isPresent()) {
			final PushEvent push = firstPushEvent.get();
			final PushPayload pushPayload = push.getPushPayload();
			final List<PayloadCommitDescription> pushedCommits = pushPayload.getCommits();
			firstPushedOIdKnown = Optional.of(pushedCommits.get(pushedCommits.size() - 1).getSha());
		} else {
			firstPushedOIdKnown = Optional.empty();
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

		final Set<RevCommit> commitsBeforeFirstPushBuilt;
		if (firstPushedCommitKnown.isPresent()) {
			commitsBeforeFirstPushBuilt = new LinkedHashSet<>(
					Graphs.reachableNodes(history.getGraph(), firstPushedCommitKnown.get()));
			commitsBeforeFirstPushBuilt.remove(firstPushedCommitKnown.get());
		} else {
			commitsBeforeFirstPushBuilt = history.getGraph().nodes();
		}
		commitsBeforeFirstPush = ImmutableSet.copyOf(commitsBeforeFirstPushBuilt);

		for (RevCommit unknownCommit : commitsBeforeFirstPushBuilt) {
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
				receivedAt.put(sha, reception);
			}
		}

		return receivedAt;
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
		 * Then followed by a push event (which I have seen to happen seven minutes
		 * later), which references as "before" the first commit in branch master. I
		 * suppose that the creation of branch master corresponds to the first user
		 * push, and the first push event corresponds to the second one. In summary, the
		 * important stuff here is: create event, with its ref_type and ref; member
		 * event, with the name of the person added (in "payload": "action" = "added"
		 * and "member"/"login"); and push event, with before and commits (in payload).
		 * The create event of the master branch may hide several commits, not just the
		 * one that can be retrieved using the next "before".
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
		 * event. Given our checks so far, this is equivalent to finding the first
		 * create branch event.
		 */
		final Optional<Event> found = events.stream().filter((e) -> e.getType() == EventType.CREATE_BRANCH_EVENT)
				.findFirst();
		/**
		 * There must be no push before the very first branch event.
		 */
		checkState(
				events.stream().map(Event::getType).takeWhile(Predicate.isEqual(EventType.CREATE_BRANCH_EVENT).negate())
						.noneMatch(Predicate.isEqual(EventType.PUSH_EVENT)));

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
