package io.github.oliviercailloux.git.git_hub.services;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.stream.Stream;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonValue;

import io.github.oliviercailloux.json.PrintableJsonObjectFactory;

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

	static public Stream<JsonObject> getContent(JsonObject connection) {
		return getContent(connection, false);
	}

	static public Stream<JsonObject> getContent(JsonObject connection, boolean allowPartial) {
		final JsonArray nodes = connection.getJsonArray("nodes");
		checkArgument(allowPartial || isConnectionComplete(connection), PrintableJsonObjectFactory.wrap(connection));
		final Stream<JsonObject> contents = nodes.stream().map(JsonValue::asJsonObject);
		return contents;
	}

	static public boolean isConnectionComplete(JsonObject connection) {
		final JsonArray nodes = connection.getJsonArray("nodes");
		final boolean sizeComplete = connection.getInt("totalCount") == nodes.size();
		final boolean pageComplete = !connection.getJsonObject("pageInfo").getBoolean("hasNextPage");
		checkArgument(pageComplete == sizeComplete);
		return sizeComplete;
	}

}
