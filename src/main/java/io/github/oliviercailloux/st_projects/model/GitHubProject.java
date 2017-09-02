package io.github.oliviercailloux.st_projects.model;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import javax.json.JsonObject;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import io.github.oliviercailloux.st_projects.services.git_hub.Fetch;
import io.github.oliviercailloux.st_projects.services.git_hub.Utils;

public class GitHubProject {
	private int id;

	private List<GitHubIssue> issues;

	private ImmutableMap<String, GitHubIssue> issuesByName;

	private JsonObject json;

	private Project project;

	public GitHubProject(Project project, JsonObject json) {
		this.project = requireNonNull(project);
		this.json = requireNonNull(json);
		issues = null;
		issuesByName = null;
	}

	public URL getApiURL() {
		return Utils.newUrl(json.getString("url"));
	}

	public LocalDateTime getCreatedAt() {
		final ZonedDateTime parsed = ZonedDateTime.parse(json.getString("created_at"));
		assert parsed.getZone().equals(ZoneOffset.UTC);
		return parsed.toLocalDateTime();
	}

	public URL getHtmlURL() {
		return Utils.newUrl(json.getString("html_url"));
	}

	public int getId() {
		return json.getInt("id");
	}

	public Optional<GitHubIssue> getIssue(String name) {
		checkState(issuesByName != null);
		return issuesByName.containsKey(name) ? Optional.of(issuesByName.get(name)) : Optional.empty();
	}

	public List<GitHubIssue> getIssues() {
		checkState(issues != null);
		return issues;
	}

	public String getName() {
		return json.getString("name");
	}

	public Contributor getOwner() {
		final JsonObject ownerJson = json.getJsonObject("owner");
		final Contributor owner = new Contributor(ownerJson);
		return owner;
	}

	public Project getProject() {
		return project;
	}

	public void initIssues() throws IOException {
		final List<GitHubIssue> fetched;
		/**
		 * This should be made cleaner, probably with the help of some GitHub library.
		 */
		try (Fetch fetcher = new Fetch()) {
			fetched = fetcher.fetchIssues(this);
		}
		issues = ImmutableList.copyOf(fetched);
		issuesByName = issues.stream().collect(ImmutableMap.toImmutableMap((i) -> i.getName(), (i) -> i));
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this).addValue(id).addValue(project).addValue(getOwner()).addValue(json)
				.toString();
	}
}
