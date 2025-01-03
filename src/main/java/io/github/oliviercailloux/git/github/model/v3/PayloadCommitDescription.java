package io.github.oliviercailloux.git.github.model.v3;

import static java.util.Objects.requireNonNull;

import jakarta.json.JsonObject;
import java.net.URI;
import org.eclipse.jgit.lib.ObjectId;

/**
 *
 * https://developer.github.com/v3/activity/events/types/#pushevent
 *
 * @author Olivier Cailloux
 *
 */
public class PayloadCommitDescription {

  public static PayloadCommitDescription from(JsonObject json) {
    return new PayloadCommitDescription(json);
  }

  private final JsonObject json;

  private PayloadCommitDescription(JsonObject json) {
    this.json = requireNonNull(json);
  }

  public URI getApiUri() {
    return URI.create(json.getString("url"));
  }

  public JsonObject getJson() {
    return json;
  }

  public ObjectId getSha() {
    return ObjectId.fromString(json.getString("sha"));
  }
}
