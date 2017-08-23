package io.github.oliviercailloux.st_projects.model;

import static java.util.Objects.requireNonNull;

import java.net.URL;
import java.util.List;

import com.google.common.base.MoreObjects;

public class Issue {
	/**
	 * Not <code>null</code>.
	 */
	private List<Contributor> contributors;

	/**
	 * Not <code>null</code>.
	 */
	private Project project;

	/**
	 * Not <code>null</code>.
	 */
	private URL url;

	public Issue(Project project, URL url, List<Contributor> contributors) {
		this.project = requireNonNull(project);
		this.url = requireNonNull(url);
		this.contributors = requireNonNull(contributors);
	}

	public List<Contributor> getContributors() {
		return contributors;
	}

	public Project getProject() {
		return project;
	}

	public URL getUrl() {
		return url;
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this).addValue(project).addValue(url).addValue(contributors).toString();
	}
}
