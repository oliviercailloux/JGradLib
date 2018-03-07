package io.github.oliviercailloux.st_projects.services.git_hub;

import static java.util.Objects.requireNonNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
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

import io.github.oliviercailloux.st_projects.utils.JsonUtils;
import io.github.oliviercailloux.st_projects.utils.Utils;

public class RawGitHubGraphQLFetcher implements AutoCloseable {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(RawGitHubGraphQLFetcher.class);

	public static void main(String[] args) throws Exception {
		final RawGitHubGraphQLFetcher fetcher = new RawGitHubGraphQLFetcher();
		fetcher.setToken(Utils.getToken());
		fetcher.log();
	}

	public final String ENDPOINT = "https://api.github.com/graphql";

	private final Client client;

	/**
	 * Not <code>null</code>.
	 */
	private String rateLimit;

	private Instant rateReset;

	/**
	 * Not <code>null</code>.
	 */
	private String token;

	public RawGitHubGraphQLFetcher() {
		rateLimit = "";
		rateReset = Instant.EPOCH;
		client = ClientBuilder.newClient();
		token = "";
	}

	@Override
	public void close() {
		client.close();
	}

	public void log() throws IOException {
		final WebTarget target = client.target(ENDPOINT);
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
		final JsonObject varsJson = factory.createObjectBuilder().add("repositoryName", "Dauphine-Pole-Info")
				.add("repositoryOwner", "MAMERY-DOUMBIA").build();
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
		LOGGER.info(JsonUtils.asPrettyString(data));
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
			LOGGER.debug("No rate reset info.");
		}
	}
}
