package io.github.oliviercailloux.git_hub.low;

import static java.util.Objects.requireNonNull;

import java.net.URL;
import java.time.Instant;

import javax.json.JsonObject;

import io.github.oliviercailloux.st_projects.services.git_hub.GitHubJsonParser;
import io.github.oliviercailloux.st_projects.utils.Utils;

public class Repository {

	public static Repository from(JsonObject json) {
		return new Repository(json);
	}

	private final JsonObject json;

	private Repository(JsonObject json) {
		this.json = requireNonNull(json);
	}

	public URL getApiURL() {
		return Utils.newURL(json.getString("url"));
	}

	public Instant getCreatedAt() {
		return GitHubJsonParser.getCreatedAt(json);
	}

	public URL getHtmlURL() {
		return Utils.newURL(json.getString("html_url") + "/");
	}

	public int getId() {
		return json.getInt("id");
	}

	public String getName() {
		return json.getString("name");
	}

	public URL getSshURL() {
		return Utils.newURL("ssh://" + json.getString("ssh_url"));
	}

	public String getSshURLString() {
		return json.getString("ssh_url");
	}

}
