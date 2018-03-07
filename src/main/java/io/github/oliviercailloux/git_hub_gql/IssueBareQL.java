package io.github.oliviercailloux.git_hub_gql;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import java.net.URL;
import java.time.Instant;

import javax.json.JsonObject;

import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;

import io.github.oliviercailloux.git_hub.low.IssueCoordinates;
import io.github.oliviercailloux.st_projects.services.git_hub.GitHubJsonParser;
import io.github.oliviercailloux.st_projects.utils.Utils;

public class IssueBareQL {
	public static IssueBareQL from(JsonObject json) {
		return new IssueBareQL(json);
	}

	private final JsonObject json;

	private IssueBareQL(JsonObject json) {
		this.json = requireNonNull(json);
		checkArgument(json.containsKey("repository"));
		checkArgument(json.containsKey("createdAt"));
		checkArgument(json.containsKey("url"));
		checkArgument(json.containsKey("number"));
	}

	public IssueCoordinates getCoordinates() {
		return IssueCoordinates.from(json.getJsonObject("repository").getString("owner"),
				json.getJsonObject("repository").getString("name"), getNumber());
	}

	public Instant getCreatedAt() {
		return GitHubJsonParser.getCreatedAt(json);
	}

	public URL getHtmlURL() {
		return Utils.newURL(json.getString("url"));
	}

	public JsonObject getJson() {
		return json;
	}

	public int getNumber() {
		return json.getInt("number");
	}

	public URL getRepositoryURL() {
		return Utils.newURL(json.getJsonObject("repository").getString("homepageUrl"));
	}

	@Override
	public String toString() {
		final ToStringHelper helper = MoreObjects.toStringHelper(this);
		helper.addValue(getHtmlURL());
		return helper.toString();
	}
}
