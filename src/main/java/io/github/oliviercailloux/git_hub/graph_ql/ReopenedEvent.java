package io.github.oliviercailloux.git_hub.graph_ql;

import static com.google.common.base.Preconditions.checkArgument;

import javax.json.JsonObject;

public class ReopenedEvent extends IssueEvent {

	protected ReopenedEvent(JsonObject json) {
		super(json);
	}

	@Override
	public IssueSnapshotQL applyTo(IssueSnapshotQL snap) {
		checkArgument(!snap.isOpen());
		return IssueSnapshotQL.of(getCreatedAt(), snap.getName(), true, snap.getAssignees());
	}

}
