package io.github.oliviercailloux.st_projects.model;

import static java.util.Objects.requireNonNull;

import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.json.JsonObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.diffplug.common.base.Predicates;
import com.google.common.collect.Iterators;
import com.google.common.collect.PeekingIterator;

import io.github.oliviercailloux.st_projects.utils.Utils;

/**
 * An intemporal issue, that also contains a description of its current state.
 *
 * @author Olivier Cailloux
 *
 */
public class Issue {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(Issue.class);

	/**
	 * @param json
	 *            describes the current state of this issue.
	 */
	public static Issue from(JsonObject json, List<IssueSnapshot> snaps) {
		return new Issue(json, snaps);
	}

	private final JsonObject json;

	private final List<IssueSnapshot> snaps;

	private Issue(JsonObject json, List<IssueSnapshot> snaps) {
		this.snaps = snaps;
		this.json = requireNonNull(json);
	}

	public URL getApiURL() {
		return Utils.newURL(json.getString("url"));
	}

	/**
	 * @return the earliest snapshot which is closed and has one or two assignees,
	 *         and is not followed by a snapshot within 3 minutes, and is at least 4
	 *         minutes in the past (compared to the current time). This guarantees
	 *         that the selected snapshot will always be the same, once one matches,
	 *         but still permits a team to assign two persons after the issue is
	 *         closed (in which case a second snapshot with the second assignee
	 *         exists just after the first snapshot with the first assignee).
	 */
	public Optional<IssueSnapshot> getFirstSnapshotDone() {
		final Instant fourMinutesInThePast = Instant.now().minus(Duration.ofMinutes(4));

		final PeekingIterator<IssueSnapshot> snapsIt = Iterators.peekingIterator(snaps.iterator());
		while (snapsIt.hasNext()) {
			final IssueSnapshot snap = snapsIt.next();
			LOGGER.debug("Looking at {}, state: {}, assignees: {}.", snap, snap.getState(), snap.getAssignees());

			if (!snap.getState().equals(IssueState.CLOSED)) {
				continue;
			}

			final Instant thisSnapBirth = snap.getBirthTime();
			if (thisSnapBirth.isAfter(fourMinutesInThePast)) {
				/** This event is less than 4 minutes in the past. */
				continue;
			}

			if (snapsIt.hasNext()) {
				final IssueSnapshot nextSnap = snapsIt.peek();
				final Instant nextSnapBirth = nextSnap.getBirthTime();
				final Instant thisSnapPlus3m = thisSnapBirth.plus(Duration.ofMinutes(3));
				if (nextSnapBirth.isBefore(thisSnapPlus3m)) {
					/** This event is followed by an event within three minutes. */
					continue;
				}
			}

			final Set<User> assignees = snap.getAssignees();
			if (assignees.size() >= 1 && assignees.size() <= 2) {
				return Optional.of(snap);
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

	public String getOriginalName() {
		return json.getString("title");
	}

	public URL getRepoTODOURL() {
		return Utils.newURL(json.getString("repository_url"));
	}

	public boolean hasBeenRenamed() {
		return !snaps.stream().map(IssueSnapshot::getName).allMatch(Predicates.equalTo(getOriginalName()));
	}
}
