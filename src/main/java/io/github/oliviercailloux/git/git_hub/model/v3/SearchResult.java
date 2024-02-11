package io.github.oliviercailloux.git.git_hub.model.v3;

import static java.util.Objects.requireNonNull;

import jakarta.json.JsonObject;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 *
 * <p>
 * https://developer.github.com/v3/search/#search-code
 * </p>
 *
 * @author Olivier Cailloux
 *
 */
public class SearchResult {

  public static SearchResult from(JsonObject json) {
    return new SearchResult(json);
  }

  private final JsonObject json;

  private SearchResult(JsonObject json) {
    this.json = requireNonNull(json);
  }

  public URI getApiURI() {
    return URI.create(json.getString("url"));
  }

  public URI getHtmlURI() {
    return URI.create(json.getString("html_url"));
  }

  public String getName() {
    return json.getString("name");
  }

  public Path getPath() {
    return Paths.get(json.getString("path"));
  }
}
