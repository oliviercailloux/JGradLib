package io.github.oliviercailloux.st_projects.model;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.json.JsonObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterators;
import com.google.common.collect.PeekingIterator;
import com.google.common.collect.Sets;
import com.jcabi.github.Event;
import com.jcabi.github.Issue;

import io.github.oliviercailloux.st_projects.utils.Utils;

public class GitHubIssue {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GitHubIssue.class);

	private List<GitHubEvent> events;

	private boolean initializedAllEvents;

	private Issue issue;

	/**
	 * Not <code>null</code>.
	 */
	private JsonObject json;

	public GitHubIssue(Issue issue) {
		this.issue = requireNonNull(issue);
		this.json = null;
		initializedAllEvents = false;
		events = null;
	}

	public URL getApiURL() {
		return Utils.newURL(json.getString("url"));
	}

	/**
	 * @return the earliest event whose related issue is closed at the time of the
	 *         event and has one or two assignees at the time of the event, and is
	 *         not followed by an event within 3 minutes, and is at least 4 minutes
	 *         in the past (compared to the current time). This guarantees that the
	 *         selected event will always be the same, once one matches but still
	 *         permits a team to assign two persons after the issue is closed (in
	 *         which case a second event with the second assignee is sent just after
	 *         the first event with the first assignee).
	 */
	public Optional<GitHubEvent> getFirstEventDone() {
		checkState(initializedAllEvents);

		final Instant fourMinutesInThePast = Instant.now().minus(Duration.ofMinutes(4));

		final PeekingIterator<GitHubEvent> eventsIt = Iterators.peekingIterator(events.iterator());
		while (eventsIt.hasNext()) {
			final GitHubEvent event = eventsIt.next();
			LOGGER.debug("Looking at {}, type: {}, assignees: {}.", event, event.getType(), event.getAssignees());

			if (!event.getState().get().equals(IssueState.CLOSED)) {
				continue;
			}

			final Instant thisEventInstant = event.getCreatedAt();
			if (thisEventInstant.isAfter(fourMinutesInThePast)) {
				/** This event is less than 4 minutes in the past. */
				continue;
			}

			if (eventsIt.hasNext()) {
				final GitHubEvent nextEvent = eventsIt.peek();
				final Instant nextEventInstant = nextEvent.getCreatedAt();
				final Instant thisEventPlus3m = thisEventInstant.plus(Duration.ofMinutes(3));
				if (nextEventInstant.isBefore(thisEventPlus3m)) {
					/** This event is followed by an event within three minutes. */
					continue;
				}
			}

			final Set<User> assignees = event.getAssignees().get();
			if (assignees.size() >= 1 && assignees.size() <= 2) {
				return Optional.of(event);
			}
		}
		return Optional.empty();
	}

	public URL getHtmlURL() {
		return Utils.newURL(json.getString("html_url"));
	}

	public int getNumber() {
		return json.getInt("number");
	}

	public URL getRepoURL() {
		return Utils.newURL(json.getString("repository_url"));
	}

	public String getState() {
		return json.getString("state");
	}

	public String getTitle() {
		return json.getString("title");
	}

	public boolean hasBeenClosed() {
		checkState(initializedAllEvents);
		for (GitHubEvent event : events) {
			if (event.getType().equals(Event.CLOSED)) {
				return true;
			}
		}
		return false;
	}

	public void init() throws IOException {
		if (initializedAllEvents) {
			return;
		}
		initJson();
	}

	/**
	 * Initialized all events related to this issue, and all assignees at first
	 * close.
	 *
	 * @throws IOException
	 */
	public void initAllEvents() throws IOException {
		final Iterable<Event> eventsIt = issue.events();
		final Stream<Event> eventsStream = StreamSupport.stream(eventsIt.spliterator(), false);
		final Stream<GitHubEvent> smartStream = eventsStream.map((e) -> {
			final GitHubEvent event = new GitHubEvent(e);
			try {
				event.init();
			} catch (IOException exc) {
				throw new IllegalStateException(exc);
			}
			return event;
		});

		events = smartStream.sorted(Comparator.comparing(GitHubEvent::getCreatedAt)).collect(Collectors.toList());

		initEventsAssigneesAndStates();
	}

	@Override
	public String toString() {
		final ToStringHelper helper = MoreObjects.toStringHelper(this);
		helper.addValue(issue);
		if (events != null) {
			helper.add("Events nb", events.size());
		}
		return helper.toString();
	}

	private void initEventsAssigneesAndStates() {
		checkState(events != null);
		final Set<User> assigneesSet = Sets.newLinkedHashSet();
		for (GitHubEvent event : events) {
			final String type = event.getType();
			switch (type) {
			case Event.ASSIGNED: {
				final User c = event.getAssignee().get();
				LOGGER.debug("Assigned {}.", c);
				final boolean modified = assigneesSet.add(c);
				assert modified;
				break;
			}
			case Event.UNASSIGNED: {
				final User c = event.getAssignee().get();
				LOGGER.debug("Unassigned {}.", c);
				final boolean modified = assigneesSet.remove(c);
				assert modified;
				break;
			}
			default:
			}
			LOGGER.debug("Setting to {}: {}.", event, assigneesSet);
			event.setAssignees(ImmutableSet.copyOf(assigneesSet));
		}

		IssueState current = IssueState.OPEN;
		for (GitHubEvent event : events) {
			final String type = event.getType();
			switch (type) {
			case Event.CLOSED: {
				assert current == IssueState.OPEN;
				current = IssueState.CLOSED;
				break;
			}
			case Event.REOPENED: {
				assert current == IssueState.CLOSED;
				current = IssueState.OPEN;
				break;
			}
			default:
			}
			event.setState(current);
		}

		initializedAllEvents = true;
	}

	private void initJson() throws IOException {
		if (this.json == null) {
			this.json = issue.json();
		}
	}
}
