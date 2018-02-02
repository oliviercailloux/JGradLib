package io.github.oliviercailloux.st_projects.model;

import static com.google.common.base.Preconditions.checkState;

import java.io.IOException;
import java.net.URL;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.Set;

import javax.json.JsonObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableSet;
import com.jcabi.github.Event;
import com.jcabi.github.Github;

import io.github.oliviercailloux.st_projects.utils.Utils;

public class GitHubEvent {

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GitHubEvent.class);

	private Optional<User> assignee;

	private Set<User> assignees;

	private Event event;

	private Github github;

	/**
	 * Not <code>null</code>.
	 */
	private JsonObject json;

	/**
	 * State of the related issue at the time of the event.
	 */
	private Optional<IssueState> state;

	private final GitHubFactory userFactory;

	public GitHubEvent(Event event) {
		this.json = null;
		this.event = event;
		assignee = null;
		github = event.repo().github();
		assignees = null;
		state = Optional.empty();
		userFactory = GitHubFactory.using(github);
	}

	public URL getApiURL() {
		return Utils.newURL(json.getString("url"));
	}

	/**
	 * @return the returned users are not initialized.
	 */
	public Optional<User> getAssignee() {
		return assignee;
	}

	public Optional<Set<User>> getAssignees() {
		return Optional.ofNullable(assignees);
	}

	public Instant getCreatedAt() {
		checkState(json != null);
		final ZonedDateTime parsed = ZonedDateTime.parse(json.getString("created_at"));
		return parsed.toInstant();
	}

	public int getId() {
		return json.getInt("id");
	}

	public JsonObject getJson() {
		checkState(json != null);
		return json;
	}

	public URL getRepoURL() {
		return Utils.newURL(json.getString("repository_url"));
	}

	public Optional<IssueState> getState() {
		return state;
	}

	public String getType() {
		return json.getString("event");
	}

	public void init() throws IOException {
		if (json == null) {
			json = event.json();
		}
		if (assignee == null) {
			final JsonObject ass = json.getJsonObject("assignee");
			if (ass == null) {
				assignee = Optional.empty();
			} else {
				final String login = ass.getString("login");
				assignee = Optional.of(userFactory.getUser(login));
			}
		}
	}

	public void setAssignees(Set<User> assignees) {
		this.assignees = ImmutableSet.copyOf(assignees);
	}

	public void setState(IssueState state) {
		this.state = Optional.of(state);
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this).addValue(event).toString();
	}

}
