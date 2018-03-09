package io.github.oliviercailloux.git_hub_gql;

import static java.util.Objects.requireNonNull;

import java.time.Instant;
import java.util.Set;

import com.google.common.collect.ImmutableSet;

/**
 * An issue as it was at some specific time of its life.
 *
 * @author Olivier Cailloux
 *
 */
public class IssueSnapshotQL {
	public static IssueSnapshotQL of(Instant birthTime, String name, boolean isOpen, Set<UserQL> assignees) {
		return new IssueSnapshotQL(birthTime, name, isOpen, assignees);
	}

	private final ImmutableSet<UserQL> assignees;

	private final Instant birthTime;

	private final boolean isOpen;

	private final String name;

	private IssueSnapshotQL(Instant birthTime, String name, boolean isOpen, Set<UserQL> assignees) {
		this.birthTime = requireNonNull(birthTime);
		this.name = requireNonNull(name);
		this.isOpen = isOpen;
		this.assignees = ImmutableSet.copyOf(requireNonNull(assignees));
	}

	public ImmutableSet<UserQL> getAssignees() {
		return assignees;
	}

	public Instant getBirthTime() {
		return birthTime;
	}

	public String getName() {
		return name;
	}

	public boolean isOpen() {
		return isOpen;
	}
}
