package io.github.oliviercailloux.st_projects.model;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.json.JsonObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.jcabi.github.Issue;
import com.jcabi.github.Repo;

import io.github.oliviercailloux.st_projects.utils.JsonUtils;
import io.github.oliviercailloux.st_projects.utils.Utils;
import jersey.repackaged.com.google.common.collect.Iterables;

public class ProjectOnGitHub {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(ProjectOnGitHub.class);

	private int id;

	private List<GitHubIssue> issues;

	/**
	 * not <code>null</code> iff all issues initialized.
	 */
	private ImmutableMap<String, GitHubIssue> issuesByName;

	private JsonObject json;

	private Project project;

	private Repo repo;

	public ProjectOnGitHub(Project project, Repo repo) {
		this.project = requireNonNull(project);
		this.repo = requireNonNull(repo);
		this.json = null;
		issues = null;
		issuesByName = null;
	}

	public URL getApiURL() {
		return Utils.newURL(json.getString("url"));
	}

	public LocalDateTime getCreatedAt() {
		final ZonedDateTime parsed = ZonedDateTime.parse(json.getString("created_at"));
		assert parsed.getZone().equals(ZoneOffset.UTC);
		return parsed.toLocalDateTime();
	}

	public URL getHtmlURL() {
		return Utils.newURL(json.getString("html_url"));
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

	public GitHubUser getOwner() {
		final JsonObject ownerJson = json.getJsonObject("owner");
		final GitHubUser owner = new GitHubUser(repo.github().users().get(ownerJson.getString("login")));
		return owner;
	}

	public Project getProject() {
		return project;
	}

	public Repo getRepo() {
		return repo;
	}

	public URL getSshURL() {
		return Utils.newURL("ssh://" + json.getString("ssh_url"));
	}

	public String getSshURLString() {
		return json.getString("ssh_url");
	}

	public void init() throws IOException {
		if (json == null) {
			json = repo.json();
		}
		if (issues == null) {
			final Iterable<Issue> issuesIt = repo.issues().iterate(ImmutableMap.of("state", "all"));
			issues = ImmutableList.copyOf(Iterables.transform(issuesIt, (i) -> new GitHubIssue(i)));
		}
		checkState(getName().equals(project.getGitHubName()));
	}

	public void initAllIssues() throws IOException {
		for (GitHubIssue issue : issues) {
			issue.init();
			LOGGER.info("Inited {}.", issue);
		}
	}

	public void initAllIssuesAndEvents() throws IOException {
		initAllIssues();
		for (GitHubIssue issue : issues) {
			issue.initAllEvents();
		}
		final Map<String, GitHubIssue> builder = Maps.newLinkedHashMap();
		for (GitHubIssue issue : issues) {
			final String title = issue.getTitle();
			final Optional<GitHubEvent> newValidEventOpt = issue.getFirstEventDone();
			if (builder.containsKey(title)) {
				final GitHubIssue currentIssue = builder.get(title);
				LOGGER.info("Found again {}, already in {}, new {}.", title, currentIssue, issue);
				final Optional<GitHubEvent> currentValidEventOpt = currentIssue.getFirstEventDone();
				if (!currentValidEventOpt.isPresent() && newValidEventOpt.isPresent()) {
					LOGGER.info("Replacing current {} by new valid {}.", currentValidEventOpt, newValidEventOpt);
					builder.put(title, issue);
				} else {
					LOGGER.info("Keeping current {} although found other: {}.", currentValidEventOpt, newValidEventOpt);
				}
			} else {
				builder.put(title, issue);
			}
		}
		issuesByName = ImmutableMap.copyOf(builder);
	}

	@Override
	public String toString() {
		final ToStringHelper helper = MoreObjects.toStringHelper(this);
		helper.addValue(id).addValue(project.getName());
		helper.addValue(repo.toString());
		helper.add("Json", JsonUtils.asPrettyString(json));
		return helper.toString();
	}
}
