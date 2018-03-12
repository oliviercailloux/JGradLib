package io.github.oliviercailloux.git_hub.graph_ql;

import static com.google.common.base.Preconditions.checkArgument;

import javax.json.JsonObject;

public class ClosedEvent extends IssueEvent {

	protected ClosedEvent(JsonObject json) {
		super(json);
	}

	@Override
	public IssueSnapshot applyTo(IssueSnapshot snap) {
		checkArgument(snap.isOpen());
		return IssueSnapshot.of(getCreatedAt(), snap.getName(), false, snap.getAssignees());
	}

}
