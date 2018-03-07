package io.github.oliviercailloux.git_hub.low;

import static java.util.Objects.requireNonNull;

import java.net.URL;

import javax.json.JsonObject;

import org.eclipse.jgit.lib.ObjectId;

import io.github.oliviercailloux.st_projects.utils.Utils;

/**
 *
 * https://developer.github.com/v3/repos/commits/
 *
 * @author Olivier Cailloux
 *
 */
public class CommitGitHubDescription {

	public static CommitGitHubDescription from(JsonObject json) {
		return new CommitGitHubDescription(json);
	}

	private final JsonObject json;

	private CommitGitHubDescription(JsonObject json) {
		this.json = requireNonNull(json);
	}

	public URL getApiURL() {
		return Utils.newURL(json.getString("url"));
	}

	public String getCommitterName() {
		return json.getJsonObject("commit").getJsonObject("committer").getString("name");
	}

	public URL getHtmlURL() {
		return Utils.newURL(json.getString("html_url"));
	}

	public JsonObject getJson() {
		return json;
	}

	public ObjectId getSha() {
		return ObjectId.fromString(json.getString("sha"));
	}

}
