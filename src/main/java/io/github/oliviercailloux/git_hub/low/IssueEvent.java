package io.github.oliviercailloux.git_hub.low;

import static java.util.Objects.requireNonNull;

import java.net.URL;
import java.time.Instant;

import javax.json.JsonObject;

import io.github.oliviercailloux.st_projects.services.git_hub.GitHubJsonParser;
import io.github.oliviercailloux.st_projects.utils.Utils;

public class IssueEvent {

	public static IssueEvent from(JsonObject json) {
		return new IssueEvent(json);
	}

	private final JsonObject json;

	private IssueEvent(JsonObject json) {
		this.json = requireNonNull(json);
	}

	public URL getApiURL() {
		return Utils.newURL(json.getString("url"));
	}

	public Instant getCreatedAt() {
		return GitHubJsonParser.getCreatedAt(json);
	}

	public URL getHtmlURL() {
		return Utils.newURL(json.getString("html_url"));
	}

	public int getId() {
		return json.getInt("id");
	}

}
