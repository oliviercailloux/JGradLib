package io.github.oliviercailloux.git.github.model.graphql;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects;
import jakarta.json.JsonObject;
import java.net.URI;
import java.util.Objects;

public class User {
  public static User from(JsonObject json) {
    return new User(json);
  }

  /**
   * Not {@code null}.
   */
  private final JsonObject json;

  private User(JsonObject json) {
    this.json = requireNonNull(json);
    checkArgument(json.containsKey("login"));
    checkArgument(json.containsKey("url"));
  }

  @Override
  public boolean equals(Object o2) {
    if (!(o2 instanceof User)) {
      return false;
    }
    final User c2 = (User) o2;
    return Objects.equals(getLogin(), c2.getLogin());
  }

  public URI getHtmlURI() {
    return URI.create(json.getString("url"));
  }

  public JsonObject getJson() {
    return json;
  }

  public String getLogin() {
    return json.getString("login");
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(getLogin());
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).addValue(getLogin()).toString();
  }
}
