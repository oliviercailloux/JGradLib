package io.github.oliviercailloux.st_projects.services.git_hub;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.oliviercailloux.st_projects.model.GitHubIssue;
import io.github.oliviercailloux.st_projects.model.GitHubProject;
import io.github.oliviercailloux.st_projects.model.ModelMocker;
import io.github.oliviercailloux.st_projects.model.Project;
import io.github.oliviercailloux.st_projects.services.read.IllegalFormat;

public class TestFetch {

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(TestFetch.class);

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
	public void testFetchProjects() throws IllegalFormat {
		try (Fetch fetch = new Fetch()) {
//		fetch.fetchReadme();
			final List<Project> projects = fetch.fetchProjects();
			assertTrue(projects.toString(), projects.size() >= 3);
		}
	}

}
