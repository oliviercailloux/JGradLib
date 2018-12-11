package io.github.oliviercailloux.git.git_hub.model.v3;

import static java.util.Objects.requireNonNull;

import java.time.Instant;
import java.util.Optional;

import javax.json.JsonObject;

import com.google.common.base.MoreObjects;

import io.github.oliviercailloux.git.git_hub.services.GitHubJsonParser;
import io.github.oliviercailloux.git.git_hub.utils.JsonUtils;
import io.github.oliviercailloux.git.git_hub.utils.Utils;

/**
 * <p>
 * https://developer.github.com/v3/activity/events/
 * </p>
 * <p>
 * Not to be mixed with issue event.
 * </p>
 *
 * @author Olivier Cailloux
 *
 */
public class Event {

	public static Event from(JsonObject json) {
		return new Event(json);
	}

	private final JsonObject json;

	private Event(JsonObject json) {
		this.json = requireNonNull(json);
	}

	public Instant getCreatedAt() {
		return GitHubJsonParser.getCreatedAtV3(json);
	}

	public int getId() {
		return json.getInt("id");
	}

	public JsonObject getJson() {
		return json;
	}

	public Optional<PushPayload> getPushPayload() {
		return Utils.getIf(getType().equals(EventType.PUSH_EVENT),
				() -> PushPayload.from(json.getJsonObject("payload")));
	}

	public EventType getType() {
		return EventType.from(json.getString("type"));
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this).addValue(JsonUtils.asPrettyString(json)).toString();
	}

}
