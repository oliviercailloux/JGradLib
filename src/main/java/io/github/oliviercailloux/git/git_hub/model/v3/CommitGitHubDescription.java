package io.github.oliviercailloux.git.git_hub.model.v3;

import static java.util.Objects.requireNonNull;

import java.net.URI;
import java.time.Instant;

import javax.json.JsonObject;

import org.eclipse.jgit.lib.ObjectId;

import io.github.oliviercailloux.git.git_hub.services.GitHubJsonParser;

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

	public URI getApiURI() {
		return URI.create(json.getString("url"));
	}

	public Instant getCommitterCommitDate() {
		return GitHubJsonParser.asInstant(getJsonCommitter().getString("date"));
	}

	public String getCommitterName() {
		return getJsonCommitter().getString("name");
	}

	private JsonObject getJsonCommitter() {
		return getJsonCommit().getJsonObject("committer");
	}

	private JsonObject getJsonCommit() {
		return json.getJsonObject("commit");
	}

	public URI getHtmlURI() {
		return URI.create(json.getString("html_url"));
	}

	public JsonObject getJson() {
		return json;
	}

	public ObjectId getSha() {
		return ObjectId.fromString(json.getString("sha"));
	}

}