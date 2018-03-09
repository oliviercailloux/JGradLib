package io.github.oliviercailloux.git_hub.low;

import static java.util.Objects.requireNonNull;

import java.net.URL;
import java.util.Objects;

import javax.json.JsonObject;

import com.google.common.base.MoreObjects;

import io.github.oliviercailloux.st_projects.utils.Utils;

public class User {
	public static User from(JsonObject json) {
		return new User(json);
	}

	/**
	 * Not <code>null</code>.
	 */
	private final JsonObject json;

	private User(JsonObject json) {
		this.json = requireNonNull(json);
	}

	@Override
	public boolean equals(Object o2) {
		if (!(o2 instanceof User)) {
			return false;
		}
		final User c2 = (User) o2;
		return Objects.equals(getLogin(), c2.getLogin());
	}

	public URL getApiURL() {
		return Utils.newURL(json.getString("url"));
	}

	public URL getHtmlURL() {
		return Utils.newURL(json.getString("html_url"));
	}

	public JsonObject getJson() {
		return json;
	}

	public String getLogin() {
		return json.getString("login");
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(getLogin());
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this).addValue(getLogin()).toString();
	}
}