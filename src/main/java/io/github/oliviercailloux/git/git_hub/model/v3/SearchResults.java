package io.github.oliviercailloux.git.git_hub.model.v3;

import static java.util.Objects.requireNonNull;

import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import java.util.List;
import java.util.stream.Collectors;

/**
 *
 * https://developer.github.com/v3/search/#search-code
 *
 * @author Olivier Cailloux
 *
 */
public class SearchResults {

	public static SearchResults from(JsonObject json) {
		return new SearchResults(json);
	}

	private final JsonObject json;

	private SearchResults(JsonObject json) {
		this.json = requireNonNull(json);
	}

	public List<SearchResult> getItems() {
		return json.getJsonArray("items").stream().map(JsonValue::asJsonObject).map(SearchResult::from)
				.collect(Collectors.toList());
	}

	public boolean isIncomplete() {
		return json.getBoolean("incomplete_results");
	}

}
