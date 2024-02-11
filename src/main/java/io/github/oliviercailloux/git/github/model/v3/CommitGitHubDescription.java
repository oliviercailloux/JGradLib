package io.github.oliviercailloux.git.github.model.v3;

import static java.util.Objects.requireNonNull;

import io.github.oliviercailloux.git.github.services.GitHubJsonParser;
import jakarta.json.JsonObject;
import java.net.URI;
import java.time.Instant;
import org.eclipse.jgit.lib.ObjectId;

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

  public URI getApiUri() {
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

  public URI getHtmlUri() {
    return URI.create(json.getString("html_url"));
  }

  public JsonObject getJson() {
    return json;
  }

  public ObjectId getSha() {
    return ObjectId.fromString(json.getString("sha"));
  }
}
