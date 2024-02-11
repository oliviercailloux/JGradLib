package io.github.oliviercailloux.git.github.model.graphql;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import io.github.oliviercailloux.git.github.model.GitHubUsername;
import io.github.oliviercailloux.git.github.model.IssueCoordinates;
import io.github.oliviercailloux.git.github.services.GitHubJsonParser;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import jakarta.json.JsonValue.ValueType;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IssueBare {
  @SuppressWarnings("unused")
  private static final Logger LOGGER = LoggerFactory.getLogger(IssueBare.class);

  public static IssueBare from(JsonObject json) {
    return new IssueBare(json);
  }

  private final JsonObject json;

  private IssueBare(JsonObject json) {
    this.json = requireNonNull(json);
    checkArgument(json.containsKey("repository"));
    checkArgument(json.containsKey("createdAt"));
    checkArgument(json.containsKey("url"));
    checkArgument(json.containsKey("number"));
  }

  public IssueCoordinates getCoordinates() {
    return IssueCoordinates.from(json.getJsonObject("repository").getString("owner"),
        json.getJsonObject("repository").getString("name"), getNumber());
  }

  public Instant getCreatedAt() {
    return GitHubJsonParser.getCreatedAtQL(json);
  }

  public List<IssueEvent> getEvents() {
    final JsonObject timeline = json.getJsonObject("timeline");
    final JsonArray events = timeline.getJsonArray("nodes");
    checkState(timeline.getInt("totalCount") == events.size(),
        String.format("Tot: %s, size: %s.", timeline.getInt("totalCount"), events.size()));
    return events.stream().map(JsonValue::asJsonObject).map(IssueEvent::from)
        .flatMap(Streams::stream).collect(Collectors.toList());
  }

  public URI getHtmlURI() {
    return URI.create(json.getString("url"));
  }

  public JsonObject getJson() {
    return json;
  }

  public int getNumber() {
    return json.getInt("number");
  }

  public URI getRepositoryURI() {
    return URI.create(json.getJsonObject("repository").getString("homepageUrl"));
  }

  public String getTitle() {
    return json.getString("title");
  }

  /**
   * This works for PRs, maybe not for issues.
   */
  public Optional<String> getMilestone() {
    return Optional.ofNullable(json.get("milestone"))
        .filter(v -> v.getValueType() != ValueType.NULL).map(v -> (JsonObject) v)
        .map(m -> m.getString("title"));
  }

  /**
   * This works for PRs and for issues (though my queries are not uniform here).
   */
  public ImmutableList<GitHubUsername> getAssignees() {
    final Optional<JsonObject> assigneesOpt = Optional.ofNullable(json.getJsonObject("assignees"));
    if (assigneesOpt.isEmpty()) {
      return ImmutableList.of();
    }
    final JsonObject assignees = assigneesOpt.orElseThrow();
    checkState(GitHubJsonParser.isConnectionComplete(assignees));
    return GitHubJsonParser.getContent(assignees).map(a -> a.getString("login"))
        .map(GitHubUsername::given).collect(ImmutableList.toImmutableList());
  }

  @Override
  public String toString() {
    final ToStringHelper helper = MoreObjects.toStringHelper(this);
    helper.addValue(getHtmlURI());
    return helper.toString();
  }
}
