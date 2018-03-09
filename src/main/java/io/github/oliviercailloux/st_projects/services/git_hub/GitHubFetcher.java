package io.github.oliviercailloux.st_projects.services.git_hub;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
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
import com.jcabi.github.Coordinates;

import io.github.oliviercailloux.git_hub.low.User;
import io.github.oliviercailloux.st_projects.model.RepositoryWithIssuesWithHistoryQL;
import io.github.oliviercailloux.st_projects.utils.JsonUtils;

public class GitHubFetcher implements AutoCloseable {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GitHubFetcher.class);

	public static GitHubFetcher using(String token) {
		return new GitHubFetcher(token);
	}

	public final String GRAPHQL_ENDPOINT = "https://api.github.com/graphql";

	private final Client client;

	/**
	 * Not <code>null</code>.
	 */
	private String rateLimit;

	private Instant rateReset;

	private String token;

	private final Map<String, User> users = new LinkedHashMap<>();

	private GitHubFetcher(String token) {
		this.token = requireNonNull(token);
		rateLimit = "";
		rateReset = null;
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

	public Optional<RepositoryWithIssuesWithHistoryQL> getProject(Coordinates coordinates) throws IOException {
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
			LOGGER.debug("Error: {}.", ret.toString());
			return Optional.empty();
		}
		final JsonObject data = ret.getJsonObject("data");
		LOGGER.debug(JsonUtils.asPrettyString(data));
		final JsonObject repositoryJson = data.getJsonObject("repository");
		final RepositoryWithIssuesWithHistoryQL repo = RepositoryWithIssuesWithHistoryQL.from(repositoryJson);
		return Optional.of(repo);
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

	private void readRates(Response response) {
		rateLimit = Strings.nullToEmpty(response.getHeaderString("X-RateLimit-Remaining"));
		LOGGER.debug("Rate limit: {}.", rateLimit);
		final String rateResetString = Strings.nullToEmpty(response.getHeaderString("X-RateLimit-Reset"));
		if (!rateResetString.isEmpty()) {
			rateReset = Instant.ofEpochSecond(Integer.parseInt(rateResetString));
			LOGGER.debug("Rate reset: {}.", rateReset);
		} else {
			rateReset = null;
			LOGGER.debug("No rate reset info.");
		}
	}

}
