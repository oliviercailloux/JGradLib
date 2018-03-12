package io.github.oliviercailloux.git_hub_gql;

import static com.google.common.base.Preconditions.checkArgument;
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
		checkArgument(json.containsKey("createdAt"));
		checkArgument(json.containsKey("url"));
		checkArgument(json.containsKey("id"));
		checkArgument(json.containsKey("name"));
		checkArgument(json.containsKey("owner"));
		checkArgument(json.containsKey("sshUrl"));
	}

	public Instant getCreatedAt() {
		return GitHubJsonParser.getCreatedAtBetterSpelling(json);
	}

	public URL getHtmlURL() {
		return Utils.newURL(json.getString("url") + "/");
	}

	public int getId() {
		return json.getInt("id");
	}

	public String getName() {
		return json.getString("name");
	}

	public User getOwner() {
		return User.from(json.getJsonObject("owner"));
	}

	public URL getSshURL() {
		return Utils.newURL("ssh://" + json.getString("sshUrl"));
	}

	public String getSshURLString() {
		return json.getString("sshUrl");
	}

}
