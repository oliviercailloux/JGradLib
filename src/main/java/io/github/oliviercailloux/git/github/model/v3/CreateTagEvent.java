package io.github.oliviercailloux.git.github.model.v3;

import jakarta.json.JsonObject;

public class CreateTagEvent extends Event {

  CreateTagEvent(JsonObject json) {
    super(json);
    assert getPayload().getString("ref_type").equals("tag");
  }

  public String getCreatedTagName() {
    return getPayload().getString("ref");
  }
}
