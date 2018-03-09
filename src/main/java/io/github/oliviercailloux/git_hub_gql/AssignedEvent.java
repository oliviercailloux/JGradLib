package io.github.oliviercailloux.git_hub_gql;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.stream.Stream;

import javax.json.JsonObject;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;

public class AssignedEvent extends IssueEventQL {

	protected AssignedEvent(JsonObject json) {
		super(json);
	}

	@Override
	public IssueSnapshotQL applyTo(IssueSnapshotQL snap) {
		final UserQL user = getUser();
		checkArgument(!snap.getAssignees().contains(user));
		return IssueSnapshotQL.of(getCreatedAt(), snap.getName(), snap.isOpen(),
				Streams.concat(snap.getAssignees().stream(), Stream.of(user)).collect(ImmutableSet.toImmutableSet()));
	}

	public UserQL getUser() {
		return UserQL.from(getJson().getJsonObject("user"));
	}

}
