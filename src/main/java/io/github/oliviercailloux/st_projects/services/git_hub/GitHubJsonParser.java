package io.github.oliviercailloux.st_projects.services.git_hub;

import static java.util.Objects.requireNonNull;

import java.time.Instant;
import java.time.ZonedDateTime;

import javax.json.JsonObject;

public class GitHubJsonParser {

	public static Instant asInstant(String gitHubTemporal) {
		requireNonNull(gitHubTemporal);
		final ZonedDateTime parsed = ZonedDateTime.parse(gitHubTemporal);
		return parsed.toInstant();
	}

	public static Instant getCreatedAt(JsonObject json) {
		requireNonNull(json);
		final String gitHubTemporal = json.getString("created_at");
		return asInstant(gitHubTemporal);
	}

	public static Instant getCreatedAtBetterSpelling(JsonObject json) {
		requireNonNull(json);
		final String gitHubTemporal = json.getString("createdAt");
		return asInstant(gitHubTemporal);
	}

}
