package io.github.oliviercailloux.git_hub.low;

import static java.util.Objects.requireNonNull;

import java.net.URL;
import java.time.Instant;

import javax.json.JsonObject;

import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;
import com.jcabi.github.Coordinates;

import io.github.oliviercailloux.st_projects.services.git_hub.GitHubJsonParser;
import io.github.oliviercailloux.st_projects.utils.Utils;

public class IssueBare {
	public static IssueBare from(JsonObject json) {
		return new IssueBare(json);
	}

	private final JsonObject json;

	private IssueBare(JsonObject json) {
		this.json = requireNonNull(json);
	}

	public URL getApiURL() {
		return Utils.newURL(json.getString("url"));
	}

	public IssueCoordinates getCoordinates() {
		final Coordinates.Simple c = new Coordinates.Simple(getCoordinatesRepo());
		return IssueCoordinates.from(c.user(), c.repo(), getNumber());
	}

	public URL getHtmlURL() {
		return Utils.newURL(json.getString("html_url"));
	}

	public JsonObject getJson() {
		return json;
	}

	public int getNumber() {
		return json.getInt("number");
	}

	public URL getRepositoryURL() {
		return Utils.newURL(json.getString("repository_url"));
	}

	public boolean isPullRequest() {
		return json.containsKey("pull_request");
	}

	@Override
	public String toString() {
		final ToStringHelper helper = MoreObjects.toStringHelper(this);
		helper.addValue(getHtmlURL());
		return helper.toString();
	}

	private String getCoordinatesRepo() {
		return getRepositoryURL().toString().replaceAll("https://api.github.com/repos/", "");
	}

	public Instant getCreatedAt() {
		return GitHubJsonParser.getCreatedAt(json);
	}
}
