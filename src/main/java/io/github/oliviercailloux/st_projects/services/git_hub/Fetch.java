package io.github.oliviercailloux.st_projects.services.git_hub;

import java.io.IOException;
import java.io.StringReader;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonValue;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.beust.jcommander.internal.Lists;
import com.google.common.base.Strings;

import io.github.oliviercailloux.st_projects.model.GitHubIssue;
import io.github.oliviercailloux.st_projects.model.GitHubProject;
import io.github.oliviercailloux.st_projects.model.Project;
import io.github.oliviercailloux.st_projects.services.read.IllegalFormat;
import io.github.oliviercailloux.st_projects.services.read.ProjectReader;

public class Fetch implements AutoCloseable {
	public static final MediaType GIT_HUB_MEDIA_TYPE = new MediaType("application", "vnd.github.v3+json");

	public static final MediaType GIT_HUB_RAW_MEDIA_TYPE = new MediaType("application", "vnd.github.v3.raw");

	private static final String CONTENT_URI = "https://api.github.com/repos/{owner}/{repo}/contents/{path}";

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(Fetch.class);

	private static final String README_URI = "https://api.github.com/repos/{owner}/{repo}/readme";

	private final Client client;

	private String content;

	private List<Project> projects;

	private String rateLimit;

	private ZonedDateTime rateReset;

	public Fetch() {
		rateLimit = "";
		rateReset = Instant.EPOCH.atZone(ZoneId.systemDefault());
		content = "";
		client = ClientBuilder.newClient();
		projects = Lists.newLinkedList();
	}

	@Override
	public void close() {
		client.close();
	}

	public List<GitHubIssue> fetchIssues(GitHubProject project) throws IOException {
		final WebTarget target = client.target(Utils.toURI(project.getApiURL())).path("issues").queryParam("state",
				"all");
		final Builder request = target.request(GIT_HUB_MEDIA_TYPE);
		final Response response;
		try {
			response = request.get();
		} catch (ProcessingException e) {
			throw new IOException(e);
		}
		readRates(response);
		final String jsonIssuesStr = response.readEntity(String.class);
		LOGGER.info("List: {}.", jsonIssuesStr);
		final JsonArray jsonIssues;
		try (JsonReader jr = Json.createReader(new StringReader(jsonIssuesStr))) {
			jsonIssues = jr.readArray();
		}
		LOGGER.info("List: {}.", jsonIssues);
		final List<GitHubIssue> issues = Lists.newLinkedList();
		for (JsonValue jsonIssueValue : jsonIssues) {
			final JsonObject jsonIssue = jsonIssueValue.asJsonObject();
			final GitHubIssue issue = new GitHubIssue(jsonIssue);
			issues.add(issue);
		}
		return issues;
	}

	public List<Project> fetchProjects() throws IllegalFormat {
		projects = Lists.newLinkedList();
		final Builder request = client.target(CONTENT_URI).resolveTemplate("owner", "oliviercailloux")
				.resolveTemplate("repo", "projets").resolveTemplate("path", "EE").request(GIT_HUB_MEDIA_TYPE);
		final Response response = request.get();
		readRates(response);
		final String jsonFileListStr = response.readEntity(String.class);
		LOGGER.info("List: {}.", jsonFileListStr);
		try (JsonReader jr = Json.createReader(new StringReader(jsonFileListStr))) {
			final JsonArray jsonFileList = jr.readArray();
			for (JsonValue jsonFileValue : jsonFileList) {
				final JsonObject jsonFile = jsonFileValue.asJsonObject();
				final String type = jsonFile.getString("type");
				if (!Objects.equals(type, "file")) {
					throw new IllegalFormat();
				}
				final String fileName = jsonFile.getString("name");
				final String fileApiUrl = jsonFile.getString("git_url");
				LOGGER.info("Reading: {}.", fileName);
				readRaw(client.target(fileApiUrl));
				final StringReader source = new StringReader(content);
				final Project project;
				try {
					project = new ProjectReader().asProject(source, fileName);
				} catch (IOException e) {
					throw new IllegalStateException(e);
				}
				LOGGER.info("Built: {}.", project);
				projects.add(project);
			}
		}
		return projects;
	}

	public void fetchReadme() {
		final WebTarget target = client.target(README_URI).resolveTemplate("owner", "oliviercailloux")
				.resolveTemplate("repo", "java-course");
		readRaw(target);
	}

	private void readRates(Response response) {
		rateLimit = Strings.nullToEmpty(response.getHeaderString("X-RateLimit-Remaining"));
		LOGGER.info("Rate limit: {}.", rateLimit);
		final String rateResetString = response.getHeaderString("X-RateLimit-Reset");
		rateReset = Instant.ofEpochSecond(Integer.parseInt(rateResetString)).atZone(ZoneId.systemDefault());
		LOGGER.info("Rate reset: {}.", rateReset);
	}

	private void readRaw(final WebTarget target) {
		final Builder request = target.request(GIT_HUB_RAW_MEDIA_TYPE);
		final Response response = request.get();
		readRates(response);
		content = response.readEntity(String.class);
		LOGGER.info("Content retrieved…\n{}.", content);
	}
}
