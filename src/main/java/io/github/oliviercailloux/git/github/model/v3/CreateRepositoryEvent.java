package io.github.oliviercailloux.git.github.model.v3;

import jakarta.json.JsonObject;

public class CreateRepositoryEvent extends Event {

  CreateRepositoryEvent(JsonObject json) {
    super(json);
    assert getPayload().getString("ref_type").equals("repository");
    assert getPayload().isNull("ref");
  }
}
