package io.github.oliviercailloux.st_projects.services.git_hub;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.json.Json;
import javax.json.JsonBuilderFactory;
import javax.json.JsonObject;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.jcabi.github.Coordinates;
import com.jcabi.github.Event;
import com.jcabi.github.Github;
import com.jcabi.github.Repo;
import com.jcabi.github.RtGithub;

import io.github.oliviercailloux.git_hub.high.IssueSnapshot;
import io.github.oliviercailloux.git_hub.low.IssueBare;
import io.github.oliviercailloux.git_hub.low.IssueCoordinates;
import io.github.oliviercailloux.git_hub.low.IssueEvent;
import io.github.oliviercailloux.git_hub.low.User;
import io.github.oliviercailloux.st_projects.model.IssueWithHistory;
import io.github.oliviercailloux.st_projects.model.IssueWithHistoryQL;
import io.github.oliviercailloux.st_projects.model.RepositoryWithIssuesWithHistoryQL;
import io.github.oliviercailloux.st_projects.utils.JsonUtils;
import io.github.oliviercailloux.st_projects.utils.Utils;

public class GitHubFetcher implements AutoCloseable {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GitHubFetcher.class);

	public static GitHubFetcher using(String token) {
		return new GitHubFetcher(token);
	}

	public final String GRAPHQL_ENDPOINT = "https://api.github.com/graphql";

	private final Client client;

	private Github github;

	/**
	 * Not <code>null</code>.
	 */
	private String rateLimit;

	private Instant rateReset;

	private String token;

	private final Map<String, User> users = new LinkedHashMap<>();

	private GitHubFetcher(String token) {
		this.token = requireNonNull(token);
		this.github = new RtGithub(token);
		rateLimit = "";
		rateReset = Instant.EPOCH;
		client = ClientBuilder.newClient();
	}

	@Override
	public void close() {
		client.close();
	}

	public User getCachedUser(String login) {
		requireNonNull(login);
		checkState(users.containsKey(login));
		return users.get(login);
	}

	public RepositoryWithIssuesWithHistoryQL getExistingProject(Coordinates coordinates) throws IOException {
		final WebTarget target = client.target(GRAPHQL_ENDPOINT);
		final Builder request = target.request();
		if (token.length() >= 1) {
			request.header(HttpHeaders.AUTHORIZATION, String.format("token %s", token));
		}
		final String queryGQL;
		try (BufferedReader r = new BufferedReader(
				new InputStreamReader(getClass().getResourceAsStream("q.txt"), StandardCharsets.UTF_8))) {
			queryGQL = r.lines().collect(Collectors.joining("\n"));
		}
		final JsonBuilderFactory factory = Json.createBuilderFactory(null);
		final JsonObject varsJson = factory.createObjectBuilder().add("repositoryName", coordinates.repo())
				.add("repositoryOwner", coordinates.user()).build();
		final JsonObject queryJson = factory.createObjectBuilder().add("query", queryGQL).add("variables", varsJson)
				.build();
		final JsonObject ret;
		try (Response response = request.post(Entity.json(queryJson))) {
			readRates(response);
			if (response.getStatus() != HttpServletResponse.SC_OK) {
				throw new WebApplicationException(response);
			}
			ret = response.readEntity(JsonObject.class);
		}
		if (ret.containsKey("errors")) {
			throw new WebApplicationException(ret.toString());
		}
		final JsonObject data = ret.getJsonObject("data");
		LOGGER.debug(JsonUtils.asPrettyString(data));
		final JsonObject repositoryJson = data.getJsonObject("repository");
		final RepositoryWithIssuesWithHistoryQL repo = RepositoryWithIssuesWithHistoryQL.from(repositoryJson);
		return repo;
	}

	public IssueWithHistory getIssue(IssueBare simple) throws IOException {
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
		snaps.add(IssueSnapshot.original(simple.getCreatedAt(), name, open, assignees));

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
			final IssueEvent event = IssueEvent.from(eventJson);
			final IssueSnapshot snap = IssueSnapshot.of(event, name, open, assignees);
			snaps.add(snap);
		}
		return IssueWithHistory.from(IssueBare.from(issueJson), snaps);
	}

	public IssueWithHistory getIssue(IssueCoordinates coordinates) throws IOException {
		final com.jcabi.github.Issue issue = getJCabiIssue(coordinates);
		final JsonObject json = issue.json();
		final IssueBare simpleIssue = IssueBare.from(json);
		return getIssue(simpleIssue);
	}

	/**
	 * The returned project has all issues present in github except for issues that
	 * are pull requests. Those are ignored by this method.
	 *
	 */
	public Optional<RepositoryWithIssuesWithHistoryQL> getProject(Coordinates coordinates) throws IOException {
		final Optional<JsonObject> optPrj;
		try (RawGitHubFetcher rawFetcher = new RawGitHubFetcher()) {
			rawFetcher.setToken(Utils.getToken());
			optPrj = rawFetcher.fetchGitHubProject(coordinates);
		}
		if (!optPrj.isPresent()) {
			return Optional.empty();
		}
		return Optional.of(getExistingProject(coordinates));
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

	public void setToken(String token) {
		this.token = requireNonNull(token);
	}

	private com.jcabi.github.Issue getJCabiIssue(final IssueCoordinates coordinates) {
		final com.jcabi.github.Issue issue = getRepo(coordinates.getRepositoryCoordinates()).issues()
				.get(coordinates.getIssueNumber());
		return issue;
	}

	private Repo getRepo(Coordinates coordinates) {
		return github.repos().get(coordinates);
	}

	private User getUser(JsonObject json) throws IOException {
		return getUser(json.getString("login"));
	}

	private void readRates(Response response) {
		rateLimit = Strings.nullToEmpty(response.getHeaderString("X-RateLimit-Remaining"));
		LOGGER.debug("Rate limit: {}.", rateLimit);
		final String rateResetString = Strings.nullToEmpty(response.getHeaderString("X-RateLimit-Reset"));
		if (!rateResetString.isEmpty()) {
			rateReset = Instant.ofEpochSecond(Integer.parseInt(rateResetString));
			LOGGER.debug("Rate reset: {}.", rateReset);
		} else {
			LOGGER.debug("No rate reset info.");
		}
	}

}
