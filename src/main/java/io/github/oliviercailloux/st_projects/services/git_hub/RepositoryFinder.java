package io.github.oliviercailloux.st_projects.services.git_hub;

import static java.util.Objects.requireNonNull;

import java.time.LocalDate;
import java.time.Month;
import java.time.MonthDay;
import java.time.ZoneOffset;
import java.util.LinkedList;
import java.util.List;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonValue;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;

import io.github.oliviercailloux.st_projects.model.GitHubProject;
import io.github.oliviercailloux.st_projects.model.Project;

public class RepositoryFinder {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(RepositoryFinder.class);

	private static final String SEARCH_URI = "https://api.github.com/search/repositories";

	private final Client client;

	private LocalDate floorSearchDate;

	public RepositoryFinder() {
		client = ClientBuilder.newClient();
		floorSearchDate = getFloorSept1();
	}

	public List<GitHubProject> find(Project project) {
		final String projectName = project.getName();
		final LinkedList<GitHubProject> ghProjects = Lists.newLinkedList();
		final Builder request = client.target(SEARCH_URI)
				.queryParam("q", projectName + " in:name created:>" + floorSearchDate.toString())
				.request(Fetch.GIT_HUB_MEDIA_TYPE);
		final Response response = request.get();
		final JsonObject json = response.readEntity(JsonObject.class);
		LOGGER.info("Found nb: {}.", json.getInt("total_count"));
		final JsonArray repos = json.getJsonArray("items");
//			final Iterable<String> reposCreat = Iterables.transform(repos, (r) -> {
//				final JsonObject repo = r.asJsonObject();
//				return repo.getString("created_at");
//			});
		for (JsonValue repoVal : repos) {
			final JsonObject repo = repoVal.asJsonObject();
			final GitHubProject ghp = new GitHubProject(project, repo);
			ghProjects.add(ghp);
		}
		return ghProjects;
	}

	public LocalDate getFloorSearchDate() {
		return floorSearchDate;
	}

	public void setFloorSearchDate(LocalDate floorSearchDate) {
		this.floorSearchDate = requireNonNull(floorSearchDate);
	}

	/**
	 * @return the latest date that is the first of september and before now. That’s
	 *         either this year, first of september, or last year, first of
	 *         september in case the first one is ahead of us.
	 */
	private LocalDate getFloorSept1() {
		final LocalDate now = LocalDate.now(ZoneOffset.UTC);
		final int curYear = now.getYear();
		final MonthDay sept1 = MonthDay.of(Month.SEPTEMBER, 1);
		final LocalDate latestSept1 = sept1.atYear(curYear);
		final LocalDate previousSept1 = sept1.atYear(curYear - 1);
		final LocalDate floorComp;
		if (latestSept1.isBefore(now)) {
			floorComp = latestSept1;
		} else {
			assert previousSept1.isBefore(now);
			floorComp = previousSept1;
		}
		return floorComp;
	}
}
