package io.github.oliviercailloux.st_projects.model;

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
public class IssueSnapshot {
	public static IssueSnapshot of(Instant birthTime, String name, IssueState state, Set<User> assignees) {
		return new IssueSnapshot(birthTime, name, state, assignees);
	}

	private final ImmutableSet<User> assignees;

	private final Instant birthTime;

	private final String name;

	private final IssueState state;

	private IssueSnapshot(Instant birthTime, String name, IssueState state, Set<User> assignees) {
		this.birthTime = requireNonNull(birthTime);
		this.name = requireNonNull(name);
		this.state = requireNonNull(state);
		this.assignees = ImmutableSet.copyOf(requireNonNull(assignees));
	}

	public ImmutableSet<User> getAssignees() {
		return assignees;
	}

	public Instant getBirthTime() {
		return birthTime;
	}

	public String getName() {
		return name;
	}

	public IssueState getState() {
		return state;
	}
}
