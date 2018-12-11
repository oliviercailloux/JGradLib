package io.github.oliviercailloux.git.git_hub.model.v3;

import static java.util.Objects.requireNonNull;

import java.net.URL;

import javax.json.JsonObject;

import org.eclipse.jgit.lib.ObjectId;

import io.github.oliviercailloux.st_projects.utils.Utils;

/**
 *
 * https://developer.github.com/v3/activity/events/types/#pushevent
 *
 * @author Olivier Cailloux
 *
 */
public class PayloadCommitDescription {

	public static PayloadCommitDescription from(JsonObject json) {
		return new PayloadCommitDescription(json);
	}

	private final JsonObject json;

	private PayloadCommitDescription(JsonObject json) {
		this.json = requireNonNull(json);
	}

	public URL getApiURL() {
		return Utils.newURL(json.getString("url"));
	}

	public JsonObject getJson() {
		return json;
	}

	public ObjectId getSha() {
		return ObjectId.fromString(json.getString("sha"));
	}

}
