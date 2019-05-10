package io.github.oliviercailloux.git.git_hub.model.v3;

import javax.json.JsonObject;

public class CreateBranchEvent extends Event {

	CreateBranchEvent(JsonObject json) {
		super(json);
		assert getPayload().getString("ref_type").equals("branch");
	}

	public String getCreatedBranchName() {
		return getPayload().getString("ref");
	}

}
