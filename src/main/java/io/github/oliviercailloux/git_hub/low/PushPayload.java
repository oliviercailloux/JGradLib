package io.github.oliviercailloux.git_hub.low;

import static java.util.Objects.requireNonNull;

import java.util.List;
import java.util.stream.Collectors;

import javax.json.JsonObject;
import javax.json.JsonValue;

/**
 * <p>
 * https://developer.github.com/v3/activity/events/types/#pushevent
 * </p>
 *
 * @author Olivier Cailloux
 *
 */
public class PushPayload {

	public static PushPayload from(JsonObject json) {
		return new PushPayload(json);
	}

	private final JsonObject json;

	private PushPayload(JsonObject json) {
		this.json = requireNonNull(json);
	}

	public List<PayloadCommitDescription> getCommits() {
		return json.getJsonArray("commits").stream().map(JsonValue::asJsonObject).map(PayloadCommitDescription::from)
				.collect(Collectors.toList());
	}

	public int getId() {
		return json.getInt("id");
	}
}
