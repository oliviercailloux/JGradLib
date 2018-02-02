package io.github.oliviercailloux.st_projects.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Iterables;
import com.jcabi.github.Coordinates;
import com.jcabi.github.Github;
import com.jcabi.github.Repo;
import com.jcabi.github.RtGithub;

import io.github.oliviercailloux.st_projects.services.git_hub.RepositoryFinder;
import io.github.oliviercailloux.st_projects.utils.JsonUtils;
import io.github.oliviercailloux.st_projects.utils.Utils;

public class TestGitHubProject {

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(TestGitHubProject.class);

	@Test
	public void testGithubIssues() throws Exception {
		final RepositoryFinder finder = new RepositoryFinder();
		finder.setGitHub(new RtGithub(Utils.getToken()));
		final List<ProjectOnGitHub> found = finder.find(new Project("Dauphine-Open-Data"));
		assertTrue(found.size() >= 1);
		final List<ProjectOnGitHub> foundWithPom = finder.withPom();
		final ProjectOnGitHub matching = Iterables.getOnlyElement(foundWithPom);
		matching.init();
		matching.initAllIssuesAndEvents();
		assertTrue(matching.getIssue("Course").isPresent());
		assertFalse(matching.getIssue("Triple").isPresent());
//		assertTrue(matching.getIssue("Online").isPresent());
	}

	@Test
	public void testProject() throws Exception {
		Utils.logLimits();

		final Project p1 = ModelMocker.newProject("testrel", 2);

		final Github gitHub = new RtGithub(Utils.getToken());
		final Repo repo = gitHub.repos().get(new Coordinates.Simple("oliviercailloux", "testrel"));
		final ProjectOnGitHub project = new ProjectOnGitHub(p1, repo);
		project.init();
		project.initAllIssuesAndEvents();
		assertEquals(Utils.newURL("https://api.github.com/repos/oliviercailloux/testrel"), project.getApiURL());
		assertEquals(Utils.newURL("https://github.com/oliviercailloux/testrel"), project.getHtmlURL());
		assertEquals(LocalDateTime.of(2016, 04, 15, 10, 33, 27), project.getCreatedAt());
		assertEquals(4, project.getIssues().size());
		assertTrue(project.getIssue("test1").isPresent());
		assertFalse(project.getIssue("non-existant").isPresent());
		assertEquals("testrel", project.getName());

		final User userC = GitHubFactory.using(gitHub).getUser("oliviercailloux");
		LOGGER.debug(JsonUtils.asPrettyString(userC.getJson()));
		LOGGER.debug(JsonUtils.asPrettyString(project.getOwner().getJson()));
		assertEquals(userC, project.getOwner());
	}

}
