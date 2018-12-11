package io.github.oliviercailloux.git.git_hub.services;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonValue;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Link;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.eclipse.jgit.lib.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.MoreCollectors;

import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinates;
import io.github.oliviercailloux.git.git_hub.model.v3.CommitGitHubDescription;
import io.github.oliviercailloux.git.git_hub.model.v3.Event;
import io.github.oliviercailloux.git.git_hub.model.v3.EventType;
import io.github.oliviercailloux.git.git_hub.model.v3.SearchResult;
import io.github.oliviercailloux.git.git_hub.model.v3.SearchResults;
import io.github.oliviercailloux.git.git_hub.utils.JsonUtils;
import io.github.oliviercailloux.git.git_hub.utils.Utils;

public class GitHubFetcherV3 implements AutoCloseable {
	public static final Set<String> FORBIDDEN_IN_SEARCH = ImmutableSet.of(".", ",", ":", ";", "/", "\\", "`", "'", "\"",
			"=", "*", "!", "?", "#", "$", "&", "+", "^", "|", "~", "<", ">", "(", ")", "{", "}", "[", "]");

	public static final MediaType GIT_HUB_MEDIA_TYPE = new MediaType("application", "vnd.github.v3+json");

	public static final MediaType GIT_HUB_RAW_MEDIA_TYPE = new MediaType("application", "vnd.github.v3.raw");

	/**
	 * https://developer.github.com/v3/repos/commits/
	 */
	private static final String COMMITS_URI = "https://api.github.com/repos/{owner}/{repo}/commits";

	/**
	 * https://developer.github.com/v3/repos/commits/
	 */
	private static final String COMMITS_BY_PATH_URI = "https://api.github.com/repos/{owner}/{repo}/commits?path={path}";

	/**
	 * https://developer.github.com/v3/activity/events/#list-repository-events
	 */
	private static final String EVENTS_URI = "https://api.github.com/repos/{owner}/{repo}/events";

	private static final String FILE_IN_COMMIT_URI = "https://api.github.com/repos/{owner}/{repo}/contents/{path}?ref={sha}";

	private static final String FILE_URI = "https://api.github.com/repos/{owner}/{repo}/contents/{path}";

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GitHubFetcherV3.class);

	private static final String REPOS_URI = "https://api.github.com/repos/{owner}/{repo}";

	/**
	 * https://developer.github.com/v3/search/#search-code
	 */
	private static final String SEARCH_CODE_URI = "https://api.github.com/search/code";

	public static GitHubFetcherV3 using(String token) {
		return new GitHubFetcherV3(token);
	}

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

	/**
	 * https://developer.github.com/v3/repos/#list-organization-repositories
	 */
	private static final String LIST_ORG_REPOS = "https://api.github.com/orgs/{org}/repos";

	private GitHubFetcherV3(String token) {
		rateLimit = "";
		rateReset = Instant.EPOCH;
		client = ClientBuilder.newClient();
		this.token = requireNonNull(token);
	}

	@Override
	public void close() {
		client.close();
	}

	public JsonObject fetchEventDetails(URL eventApiURL) throws IOException {
		final WebTarget target = client.target(Utils.toURI(eventApiURL));
		final Builder request = target.request(GIT_HUB_MEDIA_TYPE);
		final String jsonEventDetailsStr;
		try (Response response = request.get()) {
			readRates(response);
			readLinks(response);
			jsonEventDetailsStr = response.readEntity(String.class);
		} catch (ProcessingException e) {
			throw new IOException(e);
		}
		LOGGER.info("Received: {}.", jsonEventDetailsStr);
		final JsonObject jsonEventDetails;
		try (JsonReader jr = Json.createReader(new StringReader(jsonEventDetailsStr))) {
			jsonEventDetails = jr.readObject();
		}
		return jsonEventDetails;
	}

	public Optional<JsonObject> fetchGitHubProject(RepositoryCoordinates coord) {
		final WebTarget target = client.target(REPOS_URI).resolveTemplate("owner", coord.getOwner())
				.resolveTemplate("repo", coord.getRepositoryName());
		return getContent(target, JsonObject.class);
	}

	public List<CommitGitHubDescription> getCommitsGitHubDescriptions(RepositoryCoordinates repositoryCoordinates) {
		return getCommitsGitHubDescriptions(repositoryCoordinates, false);
	}

	public List<CommitGitHubDescription> getCommitsGitHubDescriptions(RepositoryCoordinates repositoryCoordinates,
			boolean truncate) {
		final WebTarget target = client.target(COMMITS_URI).resolveTemplate("owner", repositoryCoordinates.getOwner())
				.resolveTemplate("repo", repositoryCoordinates.getRepositoryName());
		return getContentAsList(target, CommitGitHubDescription::from, truncate);
	}

	public List<CommitGitHubDescription> getCommitsGitHubDescriptions(RepositoryCoordinates repositoryCoordinates,
			Path path) {
		return getCommitsGitHubDescriptions(repositoryCoordinates, path, false);
	}

	public List<CommitGitHubDescription> getCommitsGitHubDescriptions(RepositoryCoordinates repositoryCoordinates,
			Path path, boolean truncate) {
		final WebTarget target = client.target(COMMITS_BY_PATH_URI)
				.resolveTemplate("owner", repositoryCoordinates.getOwner())
				.resolveTemplate("repo", repositoryCoordinates.getRepositoryName())
				.resolveTemplate("path", path.toString());
		return getContentAsList(target, CommitGitHubDescription::from, truncate);
	}

	public Optional<String> getContent(RepositoryCoordinates repositoryCoordinates, Path path) {
		final WebTarget target = client.target(FILE_URI).resolveTemplate("owner", repositoryCoordinates.getOwner())
				.resolveTemplate("repo", repositoryCoordinates.getRepositoryName())
				.resolveTemplate("path", path.toString());
		return getContent(target, String.class, GIT_HUB_RAW_MEDIA_TYPE, Optional.of((v1, v2) -> {
			throw new UnsupportedOperationException();
		}));
	}

	public Optional<String> getContent(RepositoryCoordinates repositoryCoordinates, Path path, ObjectId sha) {
		final WebTarget target = client.target(FILE_IN_COMMIT_URI)
				.resolveTemplate("owner", repositoryCoordinates.getOwner())
				.resolveTemplate("repo", repositoryCoordinates.getRepositoryName())
				.resolveTemplate("path", path.toString()).resolveTemplate("sha", sha);
		return getContent(target, String.class, GIT_HUB_RAW_MEDIA_TYPE, Optional.of((v1, v2) -> {
			throw new UnsupportedOperationException();
		}));
	}

	public ImmutableList<RepositoryCoordinates> getRepositories(String org) {
		return getRepositories(org, false);
	}

	public ImmutableList<RepositoryCoordinates> getRepositories(String org, boolean truncate) {
		final WebTarget target = client.target(LIST_ORG_REPOS).resolveTemplate("org", org);
		return getContentAsList(target, RepositoryCoordinates::from, truncate);
	}

	public Optional<Instant> getLastModification(RepositoryCoordinates repositoryCoordinates, Path path) {
		final List<CommitGitHubDescription> descriptions = getCommitsGitHubDescriptions(repositoryCoordinates, path);
		return Utils.getIf(!descriptions.isEmpty(), () -> GitHubJsonParser.asInstant(
				descriptions.get(0).getJson().getJsonObject("commit").getJsonObject("author").getString("date")));
	}

	/**
	 * GitHub only sends events from the last 90 days, thus even if the sha is
	 * indeed from this repo, it might be impossible to retrieve its received time.
	 *
	 */
	public Optional<Instant> getReceivedTime(RepositoryCoordinates repositoryCoordinates, ObjectId sha) {
		final ImmutableList<Event> pushEvents = getPushEvents(repositoryCoordinates);
		LOGGER.info("Push events: {}.", pushEvents);
		final Stream<Event> matchingEvents = pushEvents.stream()
				.filter((e) -> e.getPushPayload().get().getCommits().stream().anyMatch((c) -> {
					LOGGER.info("Comparing {} to {}.", c.getSha(), sha);
					return c.getSha().equals(sha);
				}));
		final Optional<Event> matchingEvent = matchingEvents.collect(MoreCollectors.toOptional());
		LOGGER.debug("Matching: {}.",
				JsonUtils.asPrettyString(matchingEvent.<JsonValue>map(Event::getJson).orElse(JsonValue.NULL)));
		return matchingEvent.map(Event::getCreatedAt);
	}

	public ImmutableList<Event> getStartEvent(RepositoryCoordinates repositoryCoordinates) {
		final WebTarget target = client.target(EVENTS_URI).resolveTemplate("owner", repositoryCoordinates.getOwner())
				.resolveTemplate("repo", repositoryCoordinates.getRepositoryName());
		final List<Event> events = getContentAsList(target, Event::from, false);
		final ImmutableList<Event> startEvents = events.stream()
				.filter((e) -> e.getType().equals(EventType.CREATE_EVENT)).collect(ImmutableList.toImmutableList());
		LOGGER.info("All: {}.", startEvents.stream().<String>map((e) -> JsonUtils.asPrettyString(e.getJson()))
				.collect(Collectors.joining(", ")));
		return startEvents;
	}

	public void logRepositoryEvents(RepositoryCoordinates repositoryCoordinates) {
		final WebTarget target = client.target(EVENTS_URI).resolveTemplate("owner", repositoryCoordinates.getOwner())
				.resolveTemplate("repo", repositoryCoordinates.getRepositoryName());
		final Optional<JsonArray> eventsArray = getContent(target, JsonArray.class);
		LOGGER.info("Events: {}", JsonUtils.asPrettyString(eventsArray.get()));
	}

	public List<SearchResult> searchForCode(RepositoryCoordinates repositoryCoordinates, String code,
			String extension) {
		checkArgument(!code.isEmpty());
		checkArgument(!extension.isEmpty());
		final String searchKeywords = "repo:" + repositoryCoordinates.getOwner() + "/"
				+ repositoryCoordinates.getRepositoryName() + " " + "extension:" + extension + " " + code;
		return searchFor(searchKeywords);
	}

	/**
	 * <p>
	 * You can't use the following wildcard characters as part of your search query:
	 * {@link #FORBIDDEN_IN_SEARCH}. This method refuses such entries. (GitHub doc
	 * says that search would
	 * <a href="https://help.github.com/articles/searching-code/">ignore</a> these
	 * symbols, but that is incorrect: searching for JSON.adoc matches JSON.adoc and
	 * JSON-B.adoc while searching for JSONadoc matches none of those.)
	 * </p>
	 * <p>
	 * * Doesn’t work reliably. Searching for a partial filename usually fails
	 * ("Vote" with extension java does not match "Voter.java"), though not always
	 * ("JSON" with extension adoc matches JSON-B.adoc; "class" with extension java
	 * matches MyClass.java).
	 * </p>
	 * TODO check encoding of filename
	 *
	 */
	public List<SearchResult> searchForFile(RepositoryCoordinates repositoryCoordinates, String filename,
			String extension) {
		checkArgument(!filename.isEmpty());
		checkArgument(!extension.isEmpty());
		final String searchKeywords = "repo:" + repositoryCoordinates.getOwner() + "/"
				+ repositoryCoordinates.getRepositoryName() + " " + "extension:" + extension + " " + "filename:"
				+ filename;
		final Predicate<String> includedForbidden = (forbidden) -> filename.contains(forbidden);
		checkArgument(FORBIDDEN_IN_SEARCH.stream().noneMatch(includedForbidden),
				FORBIDDEN_IN_SEARCH.stream().filter(includedForbidden).collect(Collectors.toList()));
		return searchFor(searchKeywords);
	}

	public void setToken(String token) {
		this.token = requireNonNull(token);
	}

	private <T extends JsonValue> Optional<T> getContent(WebTarget target, Class<T> c) {
		return getContent(target, c, GIT_HUB_MEDIA_TYPE, Optional.of((v1, v2) -> {
			throw new UnsupportedOperationException();
		}));
	}

	private Optional<JsonArray> getContentArray(WebTarget target, boolean truncate) {
		final Optional<BinaryOperator<JsonArray>> accumulator;
		if (truncate) {
			accumulator = Optional.empty();
		} else {
			accumulator = Optional
					.of((v1, v2) -> JsonUtils.addAllTo(JsonUtils.addAllTo(Json.createArrayBuilder(), v1), v2).build());
		}
		return getContent(target, JsonArray.class, GIT_HUB_MEDIA_TYPE, accumulator);
	}

	private <T> Optional<T> getContent(WebTarget target, Class<T> c, MediaType type,
			Optional<BinaryOperator<T>> accumulator) {
		requireNonNull(target);
		if (JsonValue.class.isAssignableFrom(c)) {
			checkArgument(type.equals(GIT_HUB_MEDIA_TYPE));
		}
		LOGGER.debug(target.toString());
		WebTarget currentTarget = target;
		final List<T> contents = new ArrayList<>();
		while (currentTarget != null) {
			final Builder request = currentTarget.request(type);
			if (token.length() >= 1) {
				request.header(HttpHeaders.AUTHORIZATION, String.format("token %s", token));
			}

			final WebTarget nextTarget;
			try (Response response = request.get()) {
				readRates(response);
				final ImmutableMap<String, URI> links = readLinks(response);

				if (accumulator.isPresent() && links.containsKey("next")) {
					final URI next = links.get("next");
					LOGGER.info("Next: {}.", next);
					nextTarget = client.target(next);
				} else {
					nextTarget = null;
				}

				final T content = response.readEntity(c);

				final String contentStr;
				if (content instanceof JsonValue) {
					final JsonValue contentAsJson = (JsonValue) content;
					contentStr = JsonUtils.asPrettyString(contentAsJson);
				} else {
					contentStr = content.toString();
				}
				LOGGER.debug("Got: {}.", contentStr);

				if (response.getStatus() == HttpServletResponse.SC_NOT_FOUND) {
					assert nextTarget == null;
					assert currentTarget.equals(target);
					return Optional.empty();
				} else if (response.getStatus() != HttpServletResponse.SC_OK) {
					throw new WebApplicationException(response);
				}
				contents.add(content);
			}
			currentTarget = nextTarget;
		}
		if (contents.size() == 1) {
			return Optional.of(contents.get(0));
		}
		LOGGER.info("Size: {}.", contents.size());
		assert accumulator.isPresent();
		return contents.stream().reduce(accumulator.get());
	}

	private <T> ImmutableList<T> getContentAsList(WebTarget target, Function<JsonObject, ? extends T> jsonDeser,
			boolean truncate) {
		final Optional<JsonArray> respOpt = getContentArray(target, truncate);
		final Function<JsonArray, Stream<JsonObject>> arrayToObjects = (a) -> a.stream().map(JsonValue::asJsonObject);
		final Stream<JsonObject> objects = respOpt.map(arrayToObjects).orElse(Stream.empty());
		return objects.map(jsonDeser).collect(ImmutableList.toImmutableList());
	}

	private ImmutableMap<String, URI> readLinks(Response response) {
		final ImmutableMap.Builder<String, URI> builder = ImmutableMap.builder();
		final ImmutableList<String> types = ImmutableList.of("next", "last", "prev", "fist");
		for (String type : types) {
			final Link link = response.getLink(type);
			if (link != null) {
				builder.put(type, link.getUri());
			}
		}

		return builder.build();
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

	private List<SearchResult> searchFor(final String searchKeywords) {
		final WebTarget target = client.target(SEARCH_CODE_URI).queryParam("q", searchKeywords);
		final Optional<SearchResults> respOpt = getContent(target, JsonObject.class).map(SearchResults::from);
		if (respOpt.isPresent()) {
			checkState(!respOpt.get().isIncomplete());
			return respOpt.get().getItems();
		}
		return ImmutableList.of();
	}

	public Optional<ObjectId> getCreationSha(RepositoryCoordinates repositoryCoordinates, Path path) {
		final List<CommitGitHubDescription> descriptions = getCommitsGitHubDescriptions(repositoryCoordinates, path);
		return Utils.getIf(!descriptions.isEmpty(), () -> descriptions.get(descriptions.size() - 1).getSha());
	}

	public ImmutableList<Event> getPushEvents(RepositoryCoordinates repositoryCoordinates) {
		final ImmutableList<Event> events = getEvents(repositoryCoordinates);
		final ImmutableList<Event> pushEvents = events.stream().filter((e) -> e.getPushPayload().isPresent())
				.collect(ImmutableList.toImmutableList());
		LOGGER.debug("All: {}.", pushEvents.stream().<String>map((e) -> JsonUtils.asPrettyString(e.getJson()))
				.collect(Collectors.joining(", ")));
		return pushEvents;
	}

	public ImmutableList<Event> getEvents(RepositoryCoordinates repositoryCoordinates) {
		final WebTarget target = client.target(EVENTS_URI).resolveTemplate("owner", repositoryCoordinates.getOwner())
				.resolveTemplate("repo", repositoryCoordinates.getRepositoryName());
		final ImmutableList<Event> events = getContentAsList(target, Event::from, false);
		LOGGER.debug("Events: {}.", events);
		return events;
	}
}
