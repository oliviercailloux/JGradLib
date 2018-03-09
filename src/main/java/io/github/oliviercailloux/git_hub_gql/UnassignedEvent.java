package io.github.oliviercailloux.git_hub_gql;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.function.Predicate;

import javax.json.JsonObject;

import com.google.common.collect.ImmutableSet;

public class UnassignedEvent extends IssueEventQL {

	protected UnassignedEvent(JsonObject json) {
		super(json);
	}

	@Override
	public IssueSnapshotQL applyTo(IssueSnapshotQL snap) {
		final UserQL user = getUser();
		checkArgument(snap.getAssignees().contains(user));
		return IssueSnapshotQL.of(getCreatedAt(), snap.getName(), snap.isOpen(), snap.getAssignees().stream()
				.filter(Predicate.isEqual(user).negate()).collect(ImmutableSet.toImmutableSet()));
	}

	public UserQL getUser() {
		return UserQL.from(getJson().getJsonObject("user"));
	}

}
