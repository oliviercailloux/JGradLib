package io.github.oliviercailloux.st_projects.model;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

import java.net.URL;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

import javax.json.JsonObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.MoreObjects;
import com.google.common.base.MoreObjects.ToStringHelper;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.ImmutableSetMultimap.Builder;
import com.google.common.collect.ImmutableSortedSet;

import io.github.oliviercailloux.st_projects.services.git_hub.GitHubJsonParser;
import io.github.oliviercailloux.st_projects.utils.Utils;

public class ProjectOnGitHub {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(ProjectOnGitHub.class);

	public static ProjectOnGitHub from(JsonObject json, User owner, List<Issue> issues) {
		return new ProjectOnGitHub(json, owner, issues);
	}

	private ImmutableSetMultimap<String, Issue> allIssuesByName;

	final private List<Issue> issues;

	final private JsonObject json;

	final private User owner;

	private ProjectOnGitHub(JsonObject json, User owner, List<Issue> issues) {
		this.owner = owner;
		this.json = requireNonNull(json);
		this.issues = requireNonNull(issues);
		final Builder<String, Issue> builder = ImmutableSetMultimap.<String, Issue>builder()
				.orderValuesBy(Comparator.<Issue>naturalOrder());
		for (Issue issue : issues) {
			builder.put(issue.getOriginalName(), issue);
		}
		allIssuesByName = builder.build();
	}

	public URL getApiURL() {
		return Utils.newURL(json.getString("url"));
	}

	public Instant getCreatedAt() {
		return GitHubJsonParser.getCreatedAt(json);
	}

	public URL getHtmlURL() {
		return Utils.newURL(json.getString("html_url"));
	}

	public int getId() {
		return json.getInt("id");
	}

	public List<Issue> getIssues() {
		checkState(issues != null);
		return issues;
	}

	/**
	 * @return all the issues that have that name (may be empty), ordered by their
	 *         date of “first done” (earliest first), with any issues that do not
	 *         have “first done” coming last.
	 */
	public ImmutableSortedSet<Issue> getIssuesNamed(String name) {
		requireNonNull(name);
		final ImmutableSet<Issue> homonyms = allIssuesByName.get(name);
		/**
		 * Guaranteed by the way we built the ImmutableSetMultimap (except after
		 * de-serialization).
		 */
		assert homonyms instanceof ImmutableSortedSet;
		return (ImmutableSortedSet<Issue>) homonyms;
	}

	public String getName() {
		return json.getString("name");
	}

	public User getOwner() {
		return owner;
	}

	public URL getSshURL() {
		return Utils.newURL("ssh://" + json.getString("ssh_url"));
	}

	public String getSshURLString() {
		return json.getString("ssh_url");
	}

	@Override
	public String toString() {
		final ToStringHelper helper = MoreObjects.toStringHelper(this);
		helper.addValue(getName());
		return helper.toString();
	}
}
