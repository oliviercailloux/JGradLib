package io.github.oliviercailloux.st_projects.model;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;
import com.google.common.base.Predicates;
import com.google.common.collect.Comparators;
import com.google.common.collect.Iterators;
import com.google.common.collect.PeekingIterator;

import io.github.oliviercailloux.git_hub_gql.IssueBareQL;
import io.github.oliviercailloux.git_hub_gql.IssueSnapshotQL;
import io.github.oliviercailloux.git_hub_gql.UserQL;

/**
 * An intemporal issue, that also contains a description of its current state.
 *
 * @author Olivier Cailloux
 *
 */
public class IssueWithHistoryQL implements Comparable<IssueWithHistoryQL> {
	private static Comparator<IssueWithHistoryQL> compareDoneTimeThenNumber = null;

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(IssueWithHistoryQL.class);

	/**
	 * @param issue
	 *            describes the current state of this issue.
	 * @param snaps
	 *            at least one.
	 */
	public static IssueWithHistoryQL from(IssueBareQL issue, List<IssueSnapshotQL> snaps) {
		return new IssueWithHistoryQL(issue, snaps);
	}

	private static Comparator<IssueWithHistoryQL> getComparator() {
		final Function<? super IssueWithHistoryQL, Optional<Instant>> issueToDoneTime = (i) -> i.getFirstSnapshotDone()
				.map(IssueSnapshotQL::getBirthTime);
		final Comparator<Optional<Instant>> compareOptInst = Comparators
				.emptiesLast(Comparator.<Instant>naturalOrder());
		final Comparator<IssueWithHistoryQL> compareDoneTime = Comparator.comparing(issueToDoneTime, compareOptInst);
		final Comparator<IssueWithHistoryQL> comparator = compareDoneTime
				.thenComparing(Comparator.comparing((i) -> i.getBare().getNumber()));
		return comparator;
	}

	private final IssueBareQL simple;

	private final List<IssueSnapshotQL> snaps;

	private IssueWithHistoryQL(IssueBareQL issue, List<IssueSnapshotQL> snaps) {
		simple = requireNonNull(issue);
		checkArgument(snaps.size() >= 1);
		this.snaps = snaps;
	}

	@Override
	public int compareTo(IssueWithHistoryQL i2) {
		if (compareDoneTimeThenNumber == null) {
			compareDoneTimeThenNumber = getComparator();
		}
		return compareDoneTimeThenNumber.compare(this, i2);
	}

	public IssueBareQL getBare() {
		return simple;
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
	public Optional<IssueSnapshotQL> getFirstSnapshotDone() {
		final Instant fourMinutesInThePast = Instant.now().minus(Duration.ofMinutes(4));

		final PeekingIterator<IssueSnapshotQL> snapsIt = Iterators.peekingIterator(snaps.iterator());
		while (snapsIt.hasNext()) {
			final IssueSnapshotQL snap = snapsIt.next();
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
				final IssueSnapshotQL nextSnap = snapsIt.peek();
				final Instant nextSnapBirth = nextSnap.getBirthTime();
				final Instant thisSnapPlus3m = thisSnapBirth.plus(Duration.ofMinutes(3));
				if (nextSnapBirth.isBefore(thisSnapPlus3m)) {
					/** This event is followed by an event within three minutes. */
					continue;
				}
			}

			final Set<UserQL> assignees = snap.getAssignees();
			if (assignees.size() >= 1 && assignees.size() <= 2) {
				return Optional.of(snap);
			}
		}
		return Optional.empty();
	}

	/**
	 * Returns all the names this issue has had in its life, ordered from earliest
	 * name to latest, thus with its original name first.
	 */
	public List<String> getNames() {
		return snaps.stream().map(IssueSnapshotQL::getName).collect(Collectors.toList());
	}

	public String getOriginalName() {
		return snaps.iterator().next().getName();
	}

	public List<IssueSnapshotQL> getSnapshots() {
		return snaps;
	}

	public boolean hasBeenRenamed() {
		return !snaps.stream().map(IssueSnapshotQL::getName).allMatch(Predicates.equalTo(getOriginalName()));
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
		helper.addValue(simple.getHtmlURL());
		helper.add("Snapshot nb", snaps.size());
		return helper.toString();
	}
}
