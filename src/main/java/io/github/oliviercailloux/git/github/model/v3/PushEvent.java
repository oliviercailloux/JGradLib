package io.github.oliviercailloux.git.github.model.v3;

import jakarta.json.JsonObject;

public class PushEvent extends Event {

  PushEvent(JsonObject json) {
    super(json);
  }

  public PushPayload getPushPayload() {
    return PushPayload.from(getPayload());
  }
}
