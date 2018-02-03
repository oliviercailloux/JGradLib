package io.github.oliviercailloux.st_projects.model;

import static com.google.common.base.Preconditions.checkArgument;

import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import javax.json.JsonObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;
import com.google.common.base.Predicates;
import com.google.common.collect.Comparators;
import com.google.common.collect.Iterators;
import com.google.common.collect.PeekingIterator;

/**
 * An intemporal issue, that also contains a description of its current state.
 *
 * @author Olivier Cailloux
 *
 */
public class Issue implements Comparable<Issue> {
	private static Comparator<Issue> compareDoneTimeThenNumber = null;

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(Issue.class);

	/**
	 * @param json
	 *            describes the current state of this issue.
	 * @param snaps
	 *            at least one.
	 */
	public static Issue from(JsonObject json, List<IssueSnapshot> snaps) {
		return new Issue(json, snaps);
	}

	private final RawGitHubIssue simple;

	private final List<IssueSnapshot> snaps;

	private Issue(JsonObject json, List<IssueSnapshot> snaps) {
		simple = RawGitHubIssue.from(json);
		checkArgument(snaps.size() >= 1);
		this.snaps = snaps;
	}

	@Override
	public int compareTo(Issue i2) {
		if (compareDoneTimeThenNumber == null) {
			final Function<? super Issue, Optional<Instant>> issueToDoneTime = (i) -> i.getFirstSnapshotDone()
					.map(IssueSnapshot::getBirthTime);
			final Comparator<Optional<Instant>> compareOptInst = Comparators
					.emptiesLast(Comparator.<Instant>naturalOrder());
			final Comparator<Issue> compareDoneTime = Comparator.comparing(issueToDoneTime, compareOptInst);
			compareDoneTimeThenNumber = compareDoneTime.thenComparing(Comparator.comparing(Issue::getNumber));
		}
		return compareDoneTimeThenNumber.compare(this, i2);
	}

	public URL getApiURL() {
		return simple.getApiURL();
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
			LOGGER.debug("Looking at {}, state: {}, assignees: {}.", snap, snap.isOpen(), snap.getAssignees());

			if (snap.isOpen()) {
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
		return simple.getHtmlURL();
	}

	public int getNumber() {
		return simple.getNumber();
	}

	public String getOriginalName() {
		return snaps.iterator().next().getName();
	}

	public List<IssueSnapshot> getSnapshots() {
		return snaps;
	}

	public boolean hasBeenRenamed() {
		return !snaps.stream().map(IssueSnapshot::getName).allMatch(Predicates.equalTo(getOriginalName()));
	}

	@Override
	public String toString() {
		final ToStringHelper helper = MoreObjects.toStringHelper(this);
		if (hasBeenRenamed()) {
			helper.add("Original name", getOriginalName());
			helper.add("Final name", snaps.get(snaps.size() - 1).getName());
		} else {
			helper.add("Name", getOriginalName());
		}
		helper.addValue(getHtmlURL());
		helper.add("Snapshot nb", snaps.size());
		return helper.toString();
	}
}