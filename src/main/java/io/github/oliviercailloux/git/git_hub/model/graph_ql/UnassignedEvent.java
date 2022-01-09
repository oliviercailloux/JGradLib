package io.github.oliviercailloux.git.git_hub.model.graph_ql;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ImmutableSet;
import java.util.function.Predicate;
import javax.json.JsonObject;

public class UnassignedEvent extends IssueEvent {

	protected UnassignedEvent(JsonObject json) {
		super(json);
	}

	@Override
	public IssueSnapshot applyTo(IssueSnapshot snap) {
		final User user = getUser();
		checkArgument(snap.getAssignees().contains(user));
		return IssueSnapshot.of(getCreatedAt(), snap.getName(), snap.isOpen(), snap.getAssignees().stream()
				.filter(Predicate.isEqual(user).negate()).collect(ImmutableSet.toImmutableSet()));
	}

	public User getUser() {
		return User.from(getJson().getJsonObject("user"));
	}

}
