package io.github.oliviercailloux.st_projects.services.git_hub;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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

import io.github.oliviercailloux.git_hub.RepositoryCoordinates;
import io.github.oliviercailloux.git_hub.low.CommitGitHubDescription;
import io.github.oliviercailloux.git_hub.low.Event;
import io.github.oliviercailloux.git_hub.low.SearchResult;
import io.github.oliviercailloux.git_hub.low.SearchResults;
import io.github.oliviercailloux.st_projects.model.Project;
import io.github.oliviercailloux.st_projects.services.read.IllegalFormat;
import io.github.oliviercailloux.st_projects.services.read.ProjectReader;
import io.github.oliviercailloux.st_projects.utils.JsonUtils;
import io.github.oliviercailloux.st_projects.utils.Utils;

public class RawGitHubFetcher implements AutoCloseable {
	public static final Set<String> FORBIDDEN_IN_SEARCH = ImmutableSet.of(".", ",", ":", ";", "/", "\\", "`", "'", "\"",
			"=", "*", "!", "?", "#", "$", "&", "+", "^", "|", "~", "<", ">", "(", ")", "{", "}", "[", "]");

	public static final MediaType GIT_HUB_MEDIA_TYPE = new MediaType("application", "vnd.github.v3+json");

	public static final MediaType GIT_HUB_RAW_MEDIA_TYPE = new MediaType("application", "vnd.github.v3.raw");

	/**
	 * https://developer.github.com/v3/repos/commits/
	 */
	private static final String COMMITS_BY_PATH_URI = "https://api.github.com/repos/{owner}/{repo}/commits?path={path}";

	private static final String CONTENT_URI = "https://api.github.com/repos/{owner}/{repo}/contents/{path}";

	/**
	 * https://developer.github.com/v3/activity/events/#list-repository-events
	 */
	private static final String EVENTS_URI = "https://api.github.com/repos/{owner}/{repo}/events";

	private static final String FILE_IN_COMMIT_URI = "https://api.github.com/repos/{owner}/{repo}/contents/{path}?ref={sha}";

	private static final String FILE_URI = "https://api.github.com/repos/{owner}/{repo}/contents/{path}";

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(RawGitHubFetcher.class);

	private static final String REPOS_URI = "https://api.github.com/repos/{owner}/{repo}";

	/**
	 * https://developer.github.com/v3/search/#search-code
	 */
	private static final String SEARCH_CODE_URI = "https://api.github.com/search/code";

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

	public RawGitHubFetcher() {
		rateLimit = "";
		rateReset = Instant.EPOCH;
		client = ClientBuilder.newClient();
		token = "";
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

	public List<Project> fetchProjects() throws IllegalFormat {
		final JsonArray jsonFileList;
		List<Project> projects = new ArrayList<>();
		final RepositoryCoordinates coord = RepositoryCoordinates.from("oliviercailloux", "projets");
		{
			final WebTarget target = client.target(CONTENT_URI).resolveTemplate("owner", coord.getOwner())
					.resolveTemplate("repo", coord.getRepositoryName()).resolveTemplate("path", "EE");
			jsonFileList = getContent(target, JsonArray.class).get();
		}

		LOGGER.debug("Read: {}.", jsonFileList);
		for (JsonValue jsonFileValue : jsonFileList) {
			final JsonObject jsonFile = jsonFileValue.asJsonObject();
			final String type = jsonFile.getString("type");
			if (!Objects.equals(type, "file")) {
				throw new IllegalFormat();
			}
			final String fileName = jsonFile.getString("name");
			final String fileApiUrl = jsonFile.getString("git_url");
			LOGGER.info("Reading: {}.", fileName);
			final String content = getContent(client.target(fileApiUrl), String.class, GIT_HUB_RAW_MEDIA_TYPE).get();
			final StringReader source = new StringReader(content);
			LOGGER.debug("Fetching modification time.");
			final Instant lastModification = getLastModification(coord, Paths.get("EE", fileName)).get();
			final Instant queried = Instant.now();
			final Project project;
			try {
				project = new ProjectReader().asProject(source, fileName, lastModification, queried);
			} catch (IOException e) {
				// string reader can’t fail!
				throw new IllegalStateException(e);
			}
			LOGGER.info("Built: {}.", project);
			projects.add(project);
		}
		return projects;
	}

	public List<CommitGitHubDescription> getCommitsGitHubDescriptions(RepositoryCoordinates repositoryCoordinates,
			Path path) {
		final WebTarget target = client.target(COMMITS_BY_PATH_URI)
				.resolveTemplate("owner", repositoryCoordinates.getOwner())
				.resolveTemplate("repo", repositoryCoordinates.getRepositoryName())
				.resolveTemplate("path", path.toString());
		return getContentAsList(target, CommitGitHubDescription::from);
	}

	public Optional<String> getContents(RepositoryCoordinates repositoryCoordinates, Path path) {
		final WebTarget target = client.target(FILE_URI).resolveTemplate("owner", repositoryCoordinates.getOwner())
				.resolveTemplate("repo", repositoryCoordinates.getRepositoryName())
				.resolveTemplate("path", path.toString());
		return getContent(target, String.class, GIT_HUB_RAW_MEDIA_TYPE);
	}

	public Optional<String> getContents(RepositoryCoordinates repositoryCoordinates, Path path, ObjectId sha) {
		final WebTarget target = client.target(FILE_IN_COMMIT_URI)
				.resolveTemplate("owner", repositoryCoordinates.getOwner())
				.resolveTemplate("repo", repositoryCoordinates.getRepositoryName())
				.resolveTemplate("path", path.toString()).resolveTemplate("sha", sha);
		return getContent(target, String.class, GIT_HUB_RAW_MEDIA_TYPE);
	}

	public Optional<ObjectId> getCreationSha(RepositoryCoordinates repositoryCoordinates, Path path) {
		final List<CommitGitHubDescription> descriptions = getCommitsGitHubDescriptions(repositoryCoordinates, path);
		return Utils.getIf(!descriptions.isEmpty(), () -> descriptions.get(descriptions.size() - 1).getSha());
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
		final WebTarget target = client.target(EVENTS_URI).resolveTemplate("owner", repositoryCoordinates.getOwner())
				.resolveTemplate("repo", repositoryCoordinates.getRepositoryName());
		final List<Event> events = getContentAsList(target, Event::from);
		final Stream<Event> pushEvents = events.stream().filter((e) -> e.getPushPayload().isPresent());
		final Stream<Event> pushEventsForLog = events.stream().filter((e) -> e.getPushPayload().isPresent());
		LOGGER.debug("All (searching for {}): {}.", sha, pushEventsForLog
				.<String>map((e) -> JsonUtils.asPrettyString(e.getJson())).collect(Collectors.joining(", ")));
		final Stream<Event> matchingEvents = pushEvents
				.filter((e) -> e.getPushPayload().get().getCommits().stream().anyMatch((c) -> {
					LOGGER.debug("Comparing {} to {}.", c.getSha(), sha);
					return c.getSha().equals(sha);
				}));
		final Optional<Event> matchingEvent = matchingEvents.collect(MoreCollectors.toOptional());
		LOGGER.debug("Matching: {}.",
				JsonUtils.asPrettyString(matchingEvent.<JsonValue>map(Event::getJson).orElse(JsonValue.NULL)));
		return matchingEvent.map(Event::getCreatedAt);
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
		return getContent(target, c, GIT_HUB_MEDIA_TYPE);
	}

	private <T> Optional<T> getContent(WebTarget target, Class<T> c, MediaType type) {
		if (JsonValue.class.isAssignableFrom(c)) {
			checkArgument(type.equals(GIT_HUB_MEDIA_TYPE));
		}
		LOGGER.debug(target.toString());
		final Builder request = target.request(type);
		if (token.length() >= 1) {
			request.header(HttpHeaders.AUTHORIZATION, String.format("token %s", token));
		}

		try (Response response = request.get()) {
			readRates(response);
			final ImmutableMap<String, URI> links = readLinks(response);
			checkState(!links.containsKey("next"), "Unsupported request: is paginated.");
			final T content = response.readEntity(c);
			final String contentStr;
			if (JsonValue.class.isAssignableFrom(c)) {
				final JsonValue contentAsJson = JsonValue.class.cast(content);
				contentStr = JsonUtils.asPrettyString(contentAsJson);
			} else {
				contentStr = content.toString();
			}
			LOGGER.debug("Got: {}.", contentStr);
			if (response.getStatus() == HttpServletResponse.SC_NOT_FOUND) {
				return Optional.empty();
			} else if (response.getStatus() != HttpServletResponse.SC_OK) {
				throw new WebApplicationException(response);
			}
			return Optional.of(content);
		}
	}

	private <T> List<T> getContentAsList(WebTarget target, Function<JsonObject, ? extends T> jsonDeser) {
		final Optional<JsonArray> respOpt = getContent(target, JsonArray.class);
		final Function<JsonArray, Stream<JsonObject>> arrayToObjects = (a) -> a.stream().map(JsonValue::asJsonObject);
		final Stream<JsonObject> objects = respOpt.map(arrayToObjects).orElse(Stream.empty());
		return objects.map(jsonDeser).collect(Collectors.toList());
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
}
