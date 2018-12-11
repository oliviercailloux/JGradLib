package io.github.oliviercailloux.st_projects.ex2;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import java.time.Instant;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Comparators;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.PeekingIterator;

import io.github.oliviercailloux.git.git_hub.model.v3.Event;
import io.github.oliviercailloux.git.git_hub.model.v3.EventType;
import io.github.oliviercailloux.git.git_hub.model.v3.PayloadCommitDescription;
import io.github.oliviercailloux.git.git_hub.model.v3.PushPayload;
import io.github.oliviercailloux.st_projects.services.git.Client;

public class GitAndGitHub {
	public GitAndGitHub() {
		receivedAt = null;
	}

	public Map<ObjectId, Instant> check(Client client, List<Event> events) {
		checkArgument(client.hasCachedContent());
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
				final Optional<Instant> nextEventCreatedAt = nextEvent.map(Event::getCreatedAt);
				receivedAt.put(beforeId, nextEventCreatedAt.get());
			}
		}
		LOGGER.info("Received at: {}.", receivedAt);
		commits = client.getHistory().getGraph().nodes();
		/** Let’s check that all top are seen, all bottom ones are unseen. */
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

		final ImmutableSet<RevCommit> commitsUnknown = commits.stream()
				.filter((c) -> !receivedAt.containsKey(c.getId())).collect(ImmutableSet.toImmutableSet());
		LOGGER.info("Unknown (after first pass): {}.", commitsUnknown);

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
			checkState(map.get(key).equals(value));
		} else {
			map.put(key, value);
		}
	}

	private Map<ObjectId, Instant> receivedAt;

	public Optional<Instant> getReceivedAt(ObjectId id) {
		return Optional.ofNullable(receivedAt.get(id));
	}

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GitAndGitHub.class);
	private Set<RevCommit> commits;
}
