package io.github.oliviercailloux.git.github.services;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import io.github.oliviercailloux.json.PrintableJsonObjectFactory;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.stream.Stream;

public class GitHubJsonParser {

  public static Instant asInstant(String gitHubTemporal) {
    requireNonNull(gitHubTemporal);
    final ZonedDateTime parsed = ZonedDateTime.parse(gitHubTemporal);
    return parsed.toInstant();
  }

  public static Instant getCreatedAtV3(JsonObject json) {
    requireNonNull(json);
    final String gitHubTemporal = json.getString("created_at");
    return asInstant(gitHubTemporal);
  }

  public static Instant getCreatedAtQL(JsonObject json) {
    requireNonNull(json);
    final String gitHubTemporal = json.getString("createdAt");
    return asInstant(gitHubTemporal);
  }

  public static Stream<JsonObject> getContent(JsonObject connection) {
    return getContent(connection, false);
  }

  public static Stream<JsonObject> getContent(JsonObject connection, boolean allowPartial) {
    final JsonArray nodes = connection.getJsonArray("nodes");
    checkArgument(allowPartial || isConnectionComplete(connection),
        PrintableJsonObjectFactory.wrapObject(connection));
    final Stream<JsonObject> contents = nodes.stream().map(JsonValue::asJsonObject);
    return contents;
  }

  public static boolean isConnectionComplete(JsonObject connection) {
    final JsonArray nodes = connection.getJsonArray("nodes");
    final boolean sizeComplete = connection.getInt("totalCount") == nodes.size();
    final boolean pageComplete = !connection.getJsonObject("pageInfo").getBoolean("hasNextPage");
    checkArgument(pageComplete == sizeComplete);
    return sizeComplete;
  }
}
