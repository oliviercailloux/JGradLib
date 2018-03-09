package io.github.oliviercailloux.git_hub_gql;

import static java.util.Objects.requireNonNull;

import java.time.Instant;
import java.util.Optional;

import javax.json.JsonObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.oliviercailloux.st_projects.services.git_hub.GitHubJsonParser;

public abstract class IssueEventQL {

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(IssueEventQL.class);

	public static Optional<IssueEventQL> from(JsonObject json) {
		final String type = json.getString("__typename");
		switch (type) {
		case "AssignedEvent":
			return Optional.of(new AssignedEvent(json));
		case "ClosedEvent":
			return Optional.of(new ClosedEvent(json));
		case "ReopenedEvent":
			return Optional.of(new ReopenedEvent(json));
		case "RenamedTitleEvent":
			return Optional.of(new RenamedTitleEvent(json));
		case "UnassignedEvent":
			return Optional.of(new UnassignedEvent(json));
		default:
			LOGGER.debug("Unknown issue event type {}.", type);
			return Optional.empty();
		}
	}

	private final JsonObject json;

	protected IssueEventQL(JsonObject json) {
		this.json = requireNonNull(json);
	}

	public abstract IssueSnapshotQL applyTo(IssueSnapshotQL snap);

	public Instant getCreatedAt() {
		return GitHubJsonParser.getCreatedAtBetterSpelling(json);
	}

	protected JsonObject getJson() {
		return json;
	}

}