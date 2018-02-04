package io.github.oliviercailloux.st_projects.services.git_hub;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import javax.json.JsonArray;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jcabi.github.Coordinates;
import com.jcabi.github.Github;
import com.jcabi.github.Issue;
import com.jcabi.github.Issues;
import com.jcabi.github.Repo;
import com.jcabi.github.RtGithub;

import io.github.oliviercailloux.st_projects.model.Project;
import io.github.oliviercailloux.st_projects.services.read.IllegalFormat;
import io.github.oliviercailloux.st_projects.utils.Utils;

public class TestFetch {

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(TestFetch.class);

	@Test
	public void testFetchAssignees() throws Exception {
		final Github github = new RtGithub(Utils.getToken());
		final Repo repo = github.repos().get(new Coordinates.Simple("waffleio", "waffle.io"));
		final Issues issues = repo.issues();
		final Issue issue = issues.get(3259);
		final JsonArray jsonArray = issue.json().getJsonArray("assignees");
		LOGGER.info("Issue assignees: {}.", jsonArray);
		assertEquals(2, jsonArray.size());
		/** https://github.com/waffleio/waffle.io/issues/3259 */
	}

	@Test
	public void testRawFetchLastModification() throws Exception {
		try (RawGitHubFetcher fetch = new RawGitHubFetcher()) {
			final Instant lastModification = fetch.getLastModification("testrel", "Test.html").get();
			LOGGER.debug("Last: {}.", lastModification);
			assertEquals(Instant.parse("2016-05-02T14:11:38Z"), lastModification);
		}
	}

	@Test
	public void testRawFetchProjects() throws IllegalFormat, IOException {
		try (RawGitHubFetcher fetch = new RawGitHubFetcher()) {
			// fetch.fetchReadme();
			final List<Project> projects = fetch.fetchProjects();
			assertTrue(projects.toString(), projects.size() >= 3);
		}
	}

	@Test(expected = AssertionError.class)
	public void testUnauthorized() throws Exception {
		final Github github = new RtGithub("invalid", "invalid-password");
		final Repo repo = github.repos().get(new Coordinates.Simple("waffleio", "waffle.io"));
		repo.json();
	}

}
