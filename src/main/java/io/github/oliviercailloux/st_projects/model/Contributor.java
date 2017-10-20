package io.github.oliviercailloux.st_projects.model;

import java.net.URL;

import javax.json.JsonObject;

import com.google.common.base.MoreObjects;

import io.github.oliviercailloux.st_projects.services.git_hub.Utils;
import jersey.repackaged.com.google.common.base.Objects;

public class Contributor {
	/**
	 * Not <code>null</code>, not empty.
	 */
	private String name;

	/**
	 * Not <code>null</code>.
	 */
	private URL url;

	public Contributor(JsonObject contr) {
		this.name = contr.getString("login");
		this.url = Utils.newUrl(contr.getString("url"));
	}

	@Override
	public boolean equals(Object o2) {
		if (!(o2 instanceof Contributor)) {
			return false;
		}
		final Contributor c2 = (Contributor) o2;
		return Objects.equal(url, c2.url);
	}

	public String getName() {
		return name;
	}

	public URL getUrl() {
		return url;
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(url);
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this).addValue(name).addValue(url).toString();
	}
}
