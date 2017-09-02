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
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.ImmutableMap;
import com.jcabi.github.Coordinates;
import com.jcabi.github.Issue;
import com.jcabi.github.Issues;
import com.jcabi.github.Repo;
import com.jcabi.github.RtGithub;

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
		final Builder<GitHubIssue> builder = ImmutableList.builder();
		final RtGithub g = new RtGithub();
		final Repo repo = g.repos().get(new Coordinates.Simple(getOwner().getName(), getName()));
		final Issues issuesApi = repo.issues();
		final Iterable<Issue> iterable = issuesApi.iterate(ImmutableMap.of("state", "all"));
		for (Issue issue : iterable) {
			final GitHubIssue gitHubIssue = new GitHubIssue(issue.json());
			builder.add(gitHubIssue);
		}
		issues = builder.build();
		issuesByName = issues.stream().collect(ImmutableMap.toImmutableMap((i) -> i.getName(), (i) -> i));
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this).addValue(id).addValue(project).addValue(getOwner()).addValue(json)
				.toString();
	}
}
