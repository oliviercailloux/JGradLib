package io.github.oliviercailloux.git_hub_gql;

import static com.google.common.base.Preconditions.checkArgument;

import javax.json.JsonObject;

public class ClosedEvent extends IssueEventQL {

	protected ClosedEvent(JsonObject json) {
		super(json);
	}

	@Override
	public IssueSnapshotQL applyTo(IssueSnapshotQL snap) {
		checkArgument(snap.isOpen());
		return IssueSnapshotQL.of(getCreatedAt(), snap.getName(), false, snap.getAssignees());
	}

}
