package io.github.oliviercailloux.st_projects.model;

import java.io.IOException;
import java.net.URL;

import javax.json.JsonObject;

import com.google.common.base.MoreObjects;
import com.jcabi.github.User;

import io.github.oliviercailloux.st_projects.utils.Utils;
import jersey.repackaged.com.google.common.base.Objects;

public class GitHubUser {
	private JsonObject json;

	private User user;

	public GitHubUser(User user) {
		this.user = user;
		json = null;
	}

	@Override
	public boolean equals(Object o2) {
		if (!(o2 instanceof GitHubUser)) {
			return false;
		}
		final GitHubUser c2 = (GitHubUser) o2;
		return Objects.equal(user, c2.user);
	}

	public URL getApiURL() {
		return Utils.newURL(json.getString("url"));
	}

	public URL getHtmlURL() {
		return Utils.newURL(json.getString("html_url"));
	}

	public String getLogin() {
		return json.getString("login");
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(user);
	}

	public void init() throws IOException {
		json = user.json();
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this).addValue(user).toString();
	}
}
