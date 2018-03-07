package io.github.oliviercailloux.git_hub.high;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import com.google.common.collect.ImmutableSet;

import io.github.oliviercailloux.git_hub.low.IssueEvent;
import io.github.oliviercailloux.git_hub.low.User;

/**
 * An issue as it was at some specific time of its life.
 *
 * @author Olivier Cailloux
 *
 */
public class IssueSnapshot {
	public static IssueSnapshot of(IssueEvent creationEvent, String name, boolean isOpen, Set<User> assignees) {
		return new IssueSnapshot(Optional.of(creationEvent), creationEvent.getCreatedAt(), name, isOpen, assignees);
	}

	public static IssueSnapshot original(Instant birthTime, String name, boolean isOpen, Set<User> assignees) {
		return new IssueSnapshot(Optional.empty(), birthTime, name, isOpen, assignees);
	}

	private final ImmutableSet<User> assignees;

	private final Instant birthTime;

	private final Optional<IssueEvent> event;

	private final boolean isOpen;

	private final String name;

	private IssueSnapshot(Optional<IssueEvent> creationEvent, Instant birthTime, String name, boolean isOpen,
			Set<User> assignees) {
		this.event = requireNonNull(creationEvent);
		this.birthTime = requireNonNull(birthTime);
		if (creationEvent.isPresent()) {
			checkArgument(creationEvent.get().getCreatedAt().equals(birthTime));
		}
		this.name = requireNonNull(name);
		this.isOpen = isOpen;
		this.assignees = ImmutableSet.copyOf(requireNonNull(assignees));
	}

	public ImmutableSet<User> getAssignees() {
		return assignees;
	}

	public Instant getBirthTime() {
		return birthTime;
	}

	public Optional<IssueEvent> getCreationEvent() {
		return event;
	}

	public String getName() {
		return name;
	}

	public boolean isOpen() {
		return isOpen;
	}
}
