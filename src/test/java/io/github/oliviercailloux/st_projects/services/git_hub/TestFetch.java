package io.github.oliviercailloux.st_projects.services.git_hub;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
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

import io.github.oliviercailloux.st_projects.model.GitHubEvent;
import io.github.oliviercailloux.st_projects.model.GitHubIssue;
import io.github.oliviercailloux.st_projects.model.GitHubProject;
import io.github.oliviercailloux.st_projects.model.ModelMocker;
import io.github.oliviercailloux.st_projects.model.Project;
import io.github.oliviercailloux.st_projects.services.read.IllegalFormat;

public class TestFetch {

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(TestFetch.class);

	@Test
	public void testFetchAssignees() throws Exception {
		final Github github = new RtGithub();
		final Repo repo = github.repos().get(new Coordinates.Simple("waffleio", "waffle.io"));
		final Issues issues = repo.issues();
		final Issue issue = issues.get(3259);
		final JsonArray jsonArray = issue.json().getJsonArray("assignees");
		LOGGER.info("Issue assignees: {}.", jsonArray);
		assertEquals(2, jsonArray.size());
		/** https://github.com/waffleio/waffle.io/issues/3259 */
	}

	@Test
	public void testFetchEvents() throws Exception {
		final GitHubIssue is = ModelMocker.newGitHubIssue("is",
				Utils.newUrl("https://api.github.com/repos/waffleio/waffle.io/issues/3259"));
		try (Fetch fetcher = new Fetch()) {
			final List<GitHubEvent> events = fetcher.fetchEvents(is);
			for (GitHubEvent event : events) {
				event.init();
				LOGGER.info("Event cr {}, type {}, nb ass {}.", event.getCreatedAt(), event.getType(),
						event.getIssue().getAssignees().size());
			}
		}
	}

	@Test
	public void testFetchIssues() {
		final Project pjc = new Project("java-course");
		final GitHubProject ghp = ModelMocker.newGitHubProject(pjc, ModelMocker.newContributor("oli"),
				Utils.newUrl("https://api.github.com/repos/oliviercailloux/java-course"));
		final List<GitHubIssue> issues = ghp.getIssues();
		assertTrue(issues.size() >= 1);
		assertEquals(0, issues.get(0).getAssignees().size());
		LOGGER.info("Issues: {}", issues);
	}

	@Test
	public void testFetchIssuesOld() throws Exception {
		try (Fetch fetch = new Fetch()) {
			final Project pjc = new Project("java-course");
			final GitHubProject ghp = ModelMocker.newGitHubProject(pjc, ModelMocker.newContributor("oli"),
					Utils.newUrl("https://api.github.com/repos/oliviercailloux/java-course"));
			final List<GitHubIssue> issues = fetch.fetchIssues(ghp);
			assertTrue(issues.size() >= 1);
			assertEquals(0, issues.get(0).getAssignees().size());
			LOGGER.info("Issues: {}", issues);
		}
	}

	@Test
	public void testFetchProjects() throws IllegalFormat, IOException {
		try (Fetch fetch = new Fetch()) {
//		fetch.fetchReadme();
			final List<Project> projects = fetch.fetchProjects();
			assertTrue(projects.toString(), projects.size() >= 3);
		}
	}

}
