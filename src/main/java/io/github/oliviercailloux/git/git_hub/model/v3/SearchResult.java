package io.github.oliviercailloux.git.git_hub.model.v3;

import static java.util.Objects.requireNonNull;

import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.json.JsonObject;

import io.github.oliviercailloux.st_projects.utils.Utils;

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

	public URL getApiURL() {
		return Utils.newURL(json.getString("url"));
	}

	public URL getHtmlURL() {
		return Utils.newURL(json.getString("html_url"));
	}

	public String getName() {
		return json.getString("name");
	}

	public Path getPath() {
		return Paths.get(json.getString("path"));
	}

}
