package io.github.oliviercailloux.git.git_hub.model.graph_ql;

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
	public IssueSnapshot applyTo(IssueSnapshot snap) {
		checkArgument(snap.getName().equals(getPreviousTitle()));
		return IssueSnapshot.of(getCreatedAt(), getCurrentTitle(), snap.isOpen(), snap.getAssignees());
	}

	public String getCurrentTitle() {
		return getJson().getString("currentTitle");
	}

	public String getPreviousTitle() {
		return getJson().getString("previousTitle");
	}

}
