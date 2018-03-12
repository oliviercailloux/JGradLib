package io.github.oliviercailloux.git_hub.graph_ql;

import static com.google.common.base.Preconditions.checkArgument;

import javax.json.JsonObject;

public class RenamedTitleEvent extends IssueEvent {

	protected RenamedTitleEvent(JsonObject json) {
		super(json);
		checkArgument(json.containsKey("previousTitle"));
		checkArgument(json.containsKey("currentTitle"));
		checkArgument(!getPreviousTitle().equals(getCurrentTitle()));
	}

	@Override
	public IssueSnapshotQL applyTo(IssueSnapshotQL snap) {
		checkArgument(snap.getName().equals(getPreviousTitle()));
		return IssueSnapshotQL.of(getCreatedAt(), getCurrentTitle(), snap.isOpen(), snap.getAssignees());
	}

	public String getCurrentTitle() {
		return getJson().getString("currentTitle");
	}

	public String getPreviousTitle() {
		return getJson().getString("previousTitle");
	}

}
