package io.github.oliviercailloux.st_projects.services.git_hub;

import static java.util.Objects.requireNonNull;

import java.time.Instant;
import java.time.ZonedDateTime;

import javax.json.JsonObject;

public class GitHubJsonParser {

	public static Instant getCreatedAt(JsonObject json) {
		requireNonNull(json);
		final ZonedDateTime parsed = ZonedDateTime.parse(json.getString("created_at"));
		return parsed.toInstant();
	}

}
