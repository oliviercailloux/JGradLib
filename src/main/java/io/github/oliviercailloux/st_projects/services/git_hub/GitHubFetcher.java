package io.github.oliviercailloux.st_projects.services.git_hub;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.json.JsonObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.jcabi.github.Coordinates;
import com.jcabi.github.Event;
import com.jcabi.github.Github;
import com.jcabi.github.Repo;

import io.github.oliviercailloux.st_projects.model.Issue;
import io.github.oliviercailloux.st_projects.model.IssueCoordinates;
import io.github.oliviercailloux.st_projects.model.IssueSnapshot;
import io.github.oliviercailloux.st_projects.model.ProjectOnGitHub;
import io.github.oliviercailloux.st_projects.model.RawGitHubIssue;
import io.github.oliviercailloux.st_projects.model.User;
import io.github.oliviercailloux.st_projects.utils.JsonUtils;
import io.github.oliviercailloux.st_projects.utils.Utils;

public class GitHubFetcher {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GitHubFetcher.class);

	public static GitHubFetcher using(Github github) {
		return new GitHubFetcher(github);
	}

	private Github github;

	private final Map<String, User> users = new LinkedHashMap<>();

	private GitHubFetcher(Github github) {
		this.github = requireNonNull(github);
	}

	public User getCachedUser(String login) {
		requireNonNull(login);
		checkState(users.containsKey(login));
		return users.get(login);
	}

	public Issue getIssue(IssueCoordinates coordinates) throws IOException {
		final com.jcabi.github.Issue issue = getJCabiIssue(coordinates);
		final JsonObject json = issue.json();
		final RawGitHubIssue simpleIssue = RawGitHubIssue.from(json);
		return getIssue(simpleIssue);
	}

	public Issue getIssue(RawGitHubIssue simple) throws IOException {
		final JsonObject issueJson = simple.getJson();
		LOGGER.debug("Taking care of issue: {}.", JsonUtils.asPrettyString(issueJson));
		final IssueCoordinates coord = simple.getCoordinates();
		final com.jcabi.github.Issue issue = getJCabiIssue(coord);
		final Iterable<Event> events = issue.events();
		/**
		 * TODO this sends n requests whereas 1 (or so) is enough, see
		 * https://api.github.com/repos/benzait27/Dauphine-Open-Data/issues/16/events.
		 */
		final ImmutableList<JsonObject> eventsJson = Utils.map(events, Event::json);

		final Set<User> assignees = Sets.newLinkedHashSet();
		boolean open = true;
		String name;
		{
			final Optional<JsonObject> firstRename = eventsJson.stream()
					.filter((ej) -> ej.getString("event").equals(Event.RENAMED)).findFirst();
			if (firstRename.isPresent()) {
				name = firstRename.get().getJsonObject("rename").getString("from");
			} else {
				final String endName = issueJson.getString("title");
				name = endName;
			}
		}

		final List<IssueSnapshot> snaps = new ArrayList<>();
		snaps.add(IssueSnapshot.of(issueJson, name, open, assignees));

		for (JsonObject eventJson : eventsJson) {
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
				assert fromName.equals(name) : String.format(
						"Taking care of issue: %s. From name '%s' ≠ recorded name '%s'.",
						JsonUtils.asPrettyString(issueJson), fromName, name);
				assert !name.equals(newName);
				name = newName;
				break;
			case Event.CLOSED: {
				assert open;
				open = false;
				break;
			}
			case Event.REOPENED: {
				assert !open;
				open = true;
				break;
			}
			default:
			}
			final IssueSnapshot snap = IssueSnapshot.of(eventJson, name, open, assignees);
			snaps.add(snap);
		}
		return Issue.from(issueJson, snaps);
	}

	/**
	 * Note that the returned project has all issues present in github except for
	 * issues that are pull requests. Those are ignored by this method.
	 *
	 */
	public ProjectOnGitHub getProject(Coordinates coordinates) throws IOException {
		final Repo repo = github.repos().get(coordinates);
		final JsonObject json = repo.json();
		final JsonObject ownerJson = json.getJsonObject("owner");
		final String login = putUserJson(ownerJson);
		final User owner = getCachedUser(login);
		return ProjectOnGitHub.from(json, owner, getIssues(repo));
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

	private List<Issue> getIssues(Repo repo) throws IOException {
		final Iterable<com.jcabi.github.Issue> issuesIt = repo.issues().iterate(ImmutableMap.of("state", "all"));
		final ImmutableList<RawGitHubIssue> simpleIssues = Utils.map(issuesIt, (i) -> RawGitHubIssue.from(i.json()));
		final Iterable<RawGitHubIssue> simpleRightIssues = Iterables.filter(simpleIssues, (s) -> !s.isPullRequest());
		final ImmutableList<Issue> issues = Utils.map(simpleRightIssues, this::getIssue);
		return issues;
	}

	private com.jcabi.github.Issue getJCabiIssue(final IssueCoordinates coordinates) {
		final com.jcabi.github.Issue issue = getRepo(coordinates.getOwner(), coordinates.getRepo()).issues()
				.get(coordinates.getIssueNumber());
		return issue;
	}

	private Repo getRepo(String owner, String repo) {
		return github.repos().get(new Coordinates.Simple(owner, repo));
	}

	private User getUser(JsonObject json) throws IOException {
		return getUser(json.getString("login"));
	}
}
