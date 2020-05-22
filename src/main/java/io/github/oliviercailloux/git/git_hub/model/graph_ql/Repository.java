package io.github.oliviercailloux.git.git_hub.model.graph_ql;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import java.io.StringReader;
import java.net.URI;
import java.time.Instant;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import javax.json.bind.annotation.JsonbPropertyOrder;
import javax.json.bind.annotation.JsonbTransient;

import com.google.common.base.MoreObjects;

import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinates;
import io.github.oliviercailloux.git.git_hub.services.GitHubJsonParser;

@JsonbPropertyOrder({ "name", "ownerLogin", "htmlURL", "sshURL", "createdAt" })
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

	public RepositoryCoordinates getCoordinates() {
		return RepositoryCoordinates.from(getOwnerLogin(), getName());
	}

	public Instant getCreatedAt() {
		return GitHubJsonParser.getCreatedAtQL(json);
	}

	@JsonbTransient
	public int getId() {
		return json.getInt("id");
	}

	@JsonbTransient
	public JsonObject getJson() {
		return json;
	}

	public String getName() {
		return json.getString("name");
	}

	@JsonbTransient
	public User getOwner() {
		return User.from(json.getJsonObject("owner"));
	}

	@JsonbTransient
	public String getSshURIString() {
		return json.getString("sshUrl");
	}

	public URI getURI() {
		return URI.create(json.getString("url"));
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this).addValue(getCoordinates().toString()).toString();
	}

	private String getOwnerLogin() {
		return getOwner().getLogin();
	}

}
