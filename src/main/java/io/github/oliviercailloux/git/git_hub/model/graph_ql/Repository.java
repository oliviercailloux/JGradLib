package io.github.oliviercailloux.git.git_hub.model.graph_ql;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import java.io.StringReader;
import java.net.URI;
import java.net.URL;
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
import io.github.oliviercailloux.st_projects.utils.Utils;

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

	public URI getSshURL() {
		return Utils.newURI("ssh:" + getSshURLString());
	}

	@JsonbTransient
	public String getSshURLString() {
		return json.getString("sshUrl");
	}

	public URL getURL() {
		return Utils.newURL(json.getString("url") + "/");
	}

	public JsonObject toJsonSummary() {
		final String data;
		try (Jsonb jsonb = JsonbBuilder.create()) {
			data = jsonb.toJson(this);
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
		try (JsonReader jr = Json.createReader(new StringReader(data))) {
			return jr.readObject();
		}
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this).addValue(getCoordinates().toString()).toString();
	}

	private String getOwnerLogin() {
		return getOwner().getLogin();
	}

}
