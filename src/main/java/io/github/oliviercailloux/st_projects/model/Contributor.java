package io.github.oliviercailloux.st_projects.model;

import static java.util.Objects.requireNonNull;

import java.net.URL;

import com.google.common.base.MoreObjects;

public class Contributor {
	/**
	 * Not <code>null</code>, not empty.
	 */
	private String name;

	/**
	 * Not <code>null</code>.
	 */
	private URL url;

	public Contributor(String name, URL url) {
		this.name = requireNonNull(name);
		this.url = requireNonNull(url);
	}

	public String getName() {
		return name;
	}

	public URL getUrl() {
		return url;
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this).addValue(name).addValue(url).toString();
	}
}
