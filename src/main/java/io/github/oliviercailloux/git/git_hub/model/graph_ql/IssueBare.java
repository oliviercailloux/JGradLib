package io.github.oliviercailloux.git.git_hub.model.graph_ql;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

import java.net.URL;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonValue;

import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;
import com.google.common.collect.Streams;

import io.github.oliviercailloux.git.git_hub.model.IssueCoordinates;
import io.github.oliviercailloux.git.git_hub.services.GitHubJsonParser;
import io.github.oliviercailloux.st_projects.utils.Utils;

public class IssueBare {
	public static IssueBare from(JsonObject json) {
		return new IssueBare(json);
	}

	private final JsonObject json;

	private IssueBare(JsonObject json) {
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
		return GitHubJsonParser.getCreatedAtQL(json);
	}

	public List<IssueEvent> getEvents() {
		final JsonObject timeline = json.getJsonObject("timeline");
		final JsonArray events = timeline.getJsonArray("nodes");
		checkState(timeline.getInt("totalCount") == events.size());
		return events.stream().map(JsonValue::asJsonObject).map(IssueEvent::from).flatMap(Streams::stream)
				.collect(Collectors.toList());
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

	public String getTitle() {
		return json.getString("title");
	}

	@Override
	public String toString() {
		final ToStringHelper helper = MoreObjects.toStringHelper(this);
		helper.addValue(getHtmlURL());
		return helper.toString();
	}
}
