package io.github.oliviercailloux.git_hub.low;

import static java.util.Objects.requireNonNull;

import java.net.URL;
import java.time.Instant;

import javax.json.JsonObject;

import org.eclipse.jgit.lib.ObjectId;

import io.github.oliviercailloux.st_projects.services.git_hub.GitHubJsonParser;
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

	public Instant getCommitterCommitDate() {
		return GitHubJsonParser.asInstant(getJsonCommitter().getString("date"));
	}

	public String getCommitterName() {
		return getJsonCommitter().getString("name");
	}

	private JsonObject getJsonCommitter() {
		return getJsonCommmit().getJsonObject("committer");
	}

	private JsonObject getJsonCommmit() {
		return json.getJsonObject("commit");
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
