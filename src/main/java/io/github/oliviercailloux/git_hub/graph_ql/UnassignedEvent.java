package io.github.oliviercailloux.git_hub.graph_ql;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.function.Predicate;

import javax.json.JsonObject;

import com.google.common.collect.ImmutableSet;

public class UnassignedEvent extends IssueEvent {

	protected UnassignedEvent(JsonObject json) {
		super(json);
	}

	@Override
	public IssueSnapshotQL applyTo(IssueSnapshotQL snap) {
		final User user = getUser();
		checkArgument(snap.getAssignees().contains(user));
		return IssueSnapshotQL.of(getCreatedAt(), snap.getName(), snap.isOpen(), snap.getAssignees().stream()
				.filter(Predicate.isEqual(user).negate()).collect(ImmutableSet.toImmutableSet()));
	}

	public User getUser() {
		return User.from(getJson().getJsonObject("user"));
	}

}
