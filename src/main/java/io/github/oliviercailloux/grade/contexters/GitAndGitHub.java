package io.github.oliviercailloux.grade.contexters;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import java.io.IOException;
import java.time.Instant;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Comparators;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.PeekingIterator;

import io.github.oliviercailloux.git.Client;
import io.github.oliviercailloux.git.git_hub.model.GitHubToken;
import io.github.oliviercailloux.git.git_hub.model.v3.Event;
import io.github.oliviercailloux.git.git_hub.model.v3.EventType;
import io.github.oliviercailloux.git.git_hub.model.v3.PayloadCommitDescription;
import io.github.oliviercailloux.git.git_hub.model.v3.PushPayload;
import io.github.oliviercailloux.git.git_hub.services.GitHubFetcherV3;

public class GitAndGitHub {
	public GitAndGitHub() {
		receivedAt = null;
		fetcherSupplier = () -> {
			try {
				return GitHubFetcherV3.using(GitHubToken.getRealInstance());
			} catch (IOException e) {
				throw new IllegalStateException(e);
			}
		};
	}

	public Map<ObjectId, Instant> check(Client client) {
		/**
		 * Approach should be changed. A Create Event with ref "null", ref_type
		 * "repository" happens when the repository gets created, that’s the first
		 * event. It is immediately followed (same second) by a member event which adds
		 * the owner of the repository. Then followed (can be much later) by a create
		 * event, ref "master", ref_type "branch", which happens when the branch master
		 * is created. Then followed by a push event (which I have seen to happen seven
		 * minutes later), which references as "before" the first commit in branch
		 * master. I suppose that the creation of branch master corresponds to the first
		 * user push, and the first push event corresponds to the second one. In
		 * summary, the important stuff here is: create event, with its ref_type and
		 * ref; member event, with the name of the person added (in "payload": "action"
		 * = "added" and "member"/"login"); and push event, with before and commits (in
		 * payload). The create event of the master branch may hide several commits, not
		 * just the one that can be retrieved using the next "before".
		 */
		checkArgument(client.hasContentCached());
		/** Check does not throw. */
		client.getAllHistoryCached();

		final List<Event> events = getEvents(client);

		LOGGER.debug("Checking given {}.", events);
		final Stream<Instant> eventsCreation = events.stream().map(Event::getCreatedAt);
		checkArgument(Comparators.isInOrder(eventsCreation::iterator, Comparator.<Instant>naturalOrder().reversed()));

		receivedAt = new LinkedHashMap<>();

		final PeekingIterator<Event> peekingIterator = Iterators.peekingIterator(events.iterator());
		while (peekingIterator.hasNext()) {
			final Event event = peekingIterator.next();
			if (!event.getPushPayload().isPresent()) {
				continue;
			}
			final Instant createdAt = event.getCreatedAt();
			final PushPayload pushPayload = event.getPushPayload().get();
			pushPayload.getCommits().stream().map(PayloadCommitDescription::getSha).forEachOrdered((s) -> {
				checkOrPut(receivedAt, s, createdAt);
			});
			{
				final ObjectId beforeId = pushPayload.getBefore().get();
				final Optional<Event> nextEvent = peekingIterator.hasNext() ? Optional.of(peekingIterator.peek())
						: Optional.empty();
				final Optional<EventType> nextEventType = nextEvent.map(Event::getType);
				if (nextEventType.isPresent() && (nextEventType.get().equals(EventType.PUSH_EVENT)
						|| nextEventType.get().equals(EventType.CREATE_EVENT))) {
					final Optional<Instant> nextEventCreatedAt = nextEvent.map(Event::getCreatedAt);
					receivedAt.put(beforeId, nextEventCreatedAt.get());
				}
			}
		}
		LOGGER.info("Received at: {}.", receivedAt);
		commits = client.getAllHistoryCached().getGraph().nodes();
		final ImmutableSet<RevCommit> commitsUnknown = commits.stream()
				.filter((c) -> !receivedAt.containsKey(c.getId())).collect(ImmutableSet.toImmutableSet());
		LOGGER.info("Unknown (after first pass): {}.", commitsUnknown);

		/**
		 * Let’s check that all top are seen, all bottom ones are unseen. This test
		 * should be changed, we should follow the graph relation instead of the
		 * iteration order. Following the parent relation, starting from the seen ones,
		 * we should reach only seen ones. Equivalently: no unseen has a seen child.
		 */

		final Iterator<RevCommit> iterator = commits.iterator();
		while (iterator.hasNext()) {
			final RevCommit commit = iterator.next();
			if (!receivedAt.containsKey(commit.getId())) {
				break;
			}
		}

		while (iterator.hasNext()) {
			final RevCommit commit = iterator.next();
			checkState(!receivedAt.containsKey(commit.getId()));
		}

		/**
		 * The following will work only if the repo is young enough, otherwise GitHub
		 * risks to omit some events.
		 */
		final Iterator<Event> eventsRevIt = Lists.reverse(events).iterator();
		final Event last = eventsRevIt.next();
		checkState(last.getType().equals(EventType.CREATE_EVENT));
		final Event beforeLast = eventsRevIt.next();
		checkState(beforeLast.getType().equals(EventType.MEMBER_EVENT));
		final Event beforePenultimate = eventsRevIt.next();
		checkState(beforePenultimate.getType().equals(EventType.CREATE_EVENT));
		while (eventsRevIt.hasNext()) {
			final Event otherEvent = eventsRevIt.next();
			checkState(!otherEvent.getType().equals(EventType.CREATE_EVENT));
		}

		final Instant lastCreate = beforePenultimate.getCreatedAt();
		commitsUnknown.stream().forEachOrdered((c) -> receivedAt.put(c.getId(), lastCreate));
		LOGGER.info("Unknown (after second pass): {}.", unknown());
		checkState(unknown().isEmpty());
		return receivedAt;
	}

	public ImmutableSet<RevCommit> unknown() {
		return commits.stream().filter((c) -> !receivedAt.containsKey(c.getId()))
				.collect(ImmutableSet.toImmutableSet());
	}

	private <K, V> void checkOrPut(Map<K, V> map, K key, V value) {
		if (map.containsKey(key)) {
			checkState(map.get(key).equals(value), "Map contains key " + key + ", current value " + map.get(key)
					+ " differs from new one " + value + ".");
		} else {
			map.put(key, value);
		}
		/** Same thing, but not simpler. */
//		map.compute(key, (k, v) -> {
//			if (v != null) {
//				checkState(v.equals(value));
//			}
//			return value;
//		});
	}

	private Map<ObjectId, Instant> receivedAt;

	public Optional<Instant> getReceivedAt(ObjectId id) {
		return Optional.ofNullable(receivedAt.get(id));
	}

	private List<Event> getEvents(Client client) {
		final ImmutableList<Event> events;
		try (GitHubFetcherV3 fetcher = fetcherSupplier.get()) {
			events = fetcher.getEvents(client.getCoordinates());
		}
		checkState(events.size() >= 1);
		final Stream<Instant> eventsCreation = events.stream().map(Event::getCreatedAt);
		checkState(Comparators.isInOrder(eventsCreation::iterator, Comparator.<Instant>naturalOrder().reversed()));
		return events;
	}

	void setGitHubFetcherSupplier(Supplier<GitHubFetcherV3> supplier) {
		fetcherSupplier = supplier;
	}

	private Supplier<GitHubFetcherV3> fetcherSupplier;
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GitAndGitHub.class);
	private Set<RevCommit> commits;
}
