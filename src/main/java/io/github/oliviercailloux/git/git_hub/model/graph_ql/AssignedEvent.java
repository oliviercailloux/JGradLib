package io.github.oliviercailloux.git.git_hub.model.graph_ql;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.stream.Stream;

import javax.json.JsonObject;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;

public class AssignedEvent extends IssueEvent {

	protected AssignedEvent(JsonObject json) {
		super(json);
	}

	@Override
	public IssueSnapshot applyTo(IssueSnapshot snap) {
		final User user = getUser();
		checkArgument(!snap.getAssignees().contains(user));
		return IssueSnapshot.of(getCreatedAt(), snap.getName(), snap.isOpen(),
				Streams.concat(snap.getAssignees().stream(), Stream.of(user)).collect(ImmutableSet.toImmutableSet()));
	}

	public User getUser() {
		return User.from(getJson().getJsonObject("user"));
	}

}