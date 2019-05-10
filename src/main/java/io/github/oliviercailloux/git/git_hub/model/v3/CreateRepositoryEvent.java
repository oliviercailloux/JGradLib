package io.github.oliviercailloux.git.git_hub.model.v3;

import javax.json.JsonObject;

public class CreateRepositoryEvent extends Event {

	CreateRepositoryEvent(JsonObject json) {
		super(json);
		assert getPayload().getString("ref_type").equals("repository");
		assert getPayload().isNull("ref");
	}

}
