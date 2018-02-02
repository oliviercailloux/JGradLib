package io.github.oliviercailloux.st_projects.model;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.json.JsonObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Sets;
import com.jcabi.github.Event;
import com.jcabi.github.Github;

import io.github.oliviercailloux.st_projects.utils.JsonUtils;

public class GitHubFactory {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GitHubFactory.class);

	public static GitHubFactory using(Github github) {
		return new GitHubFactory(github);
	}

	private Github github;

	private final Map<String, User> users = new LinkedHashMap<>();

	private GitHubFactory(Github github) {
		this.github = requireNonNull(github);
	}

	public User getCachedUser(String login) {
		requireNonNull(login);
		checkState(users.containsKey(login));
		return users.get(login);
	}

	public Issue getIssue(com.jcabi.github.Issue issue) throws IOException {
		final Iterable<Event> events = issue.events();
		final Set<User> assignees = Sets.newLinkedHashSet();
		String name = issue.json().getString("title");
		IssueState state = IssueState.OPEN;
		final List<IssueSnapshot> snaps = new ArrayList<>();

		for (Event event : events) {
			final JsonObject eventJson = event.json();
			final String type = eventJson.getString("event");
			switch (type) {
			case Event.ASSIGNED: {
				final JsonObject ass = eventJson.getJsonObject("assignee");
				final User assigned = getUser(ass);
				LOGGER.debug("Assigned {}.", assigned);
				final boolean modified = assignees.add(assigned);
				assert modified;
				break;
			}
			case Event.UNASSIGNED: {
				final JsonObject ass = eventJson.getJsonObject("assignee");
				final User assigned = getUser(ass);
				LOGGER.debug("Unassigned {}.", assigned);
				final boolean modified = assignees.remove(assigned);
				assert modified;
				break;
			}
			case Event.RENAMED:
				final JsonObject renameJson = eventJson.getJsonObject("rename");
				LOGGER.debug("Renamed event: {}.", JsonUtils.asPrettyString(renameJson));
				final String fromName = renameJson.getString("from");
				final String newName = renameJson.getString("to");
				assert fromName.equals(name);
				assert !name.equals(newName);
				name = newName;
				break;
			case Event.CLOSED: {
				assert state == IssueState.OPEN;
				state = IssueState.CLOSED;
				break;
			}
			case Event.REOPENED: {
				assert state == IssueState.CLOSED;
				state = IssueState.OPEN;
				break;
			}
			default:
			}
			final IssueSnapshot snap = IssueSnapshot.of(getCreatedAt(eventJson), name, state, assignees);
			snaps.add(snap);
		}
		return Issue.from(issue.json(), snaps);
	}

	public User getUser(String login) throws IOException {
		if (!users.containsKey(requireNonNull(login))) {
			final com.jcabi.github.User user = github.users().get(login);
			final JsonObject json = user.json();
			users.put(login, User.from(json));
		}
		return users.get(login);
	}

	public String putUserJson(JsonObject json) {
		final String login = json.getString("login");
		checkArgument(login.length() >= 1);
		users.put(login, User.from(json));
		if (!json.containsKey("name")) {
			LOGGER.debug("Caching summarized json data for {}.", login);
		}
		return login;
	}

	private Instant getCreatedAt(JsonObject json) {
		requireNonNull(json);
		final ZonedDateTime parsed = ZonedDateTime.parse(json.getString("created_at"));
		return parsed.toInstant();
	}

	private User getUser(JsonObject json) throws IOException {
		return getUser(json.getString("login"));
	}
}
