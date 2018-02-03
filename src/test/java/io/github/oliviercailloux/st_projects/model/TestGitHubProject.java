package io.github.oliviercailloux.st_projects.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Iterables;
import com.jcabi.github.Coordinates;
import com.jcabi.github.Github;
import com.jcabi.github.RtGithub;

import io.github.oliviercailloux.st_projects.services.git_hub.GitHubFetcher;
import io.github.oliviercailloux.st_projects.services.git_hub.RepositoryFinder;
import io.github.oliviercailloux.st_projects.utils.JsonUtils;
import io.github.oliviercailloux.st_projects.utils.Utils;

public class TestGitHubProject {

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(TestGitHubProject.class);

	@Test
	public void testGithubIssues() throws Exception {
		final RepositoryFinder finder = new RepositoryFinder();
		final RtGithub gitHub = new RtGithub(Utils.getToken());
		finder.setGitHub(gitHub);
		final List<Coordinates> found = finder.find(new Project("Dauphine-Open-Data"));
		assertTrue(found.size() >= 1);
		final List<Coordinates> foundWithPom = finder.withPom();
		final Coordinates matching = Iterables.getOnlyElement(foundWithPom);
		final ProjectOnGitHub project = GitHubFetcher.using(gitHub).getProject(matching);
		assertTrue(project.getIssuesNamed("Course").toString(), project.getIssuesNamed("Course").size() == 1);
		assertFalse(project.getIssuesNamed("Triple").size() == 1);
	}

	@Test
	public void testProject() throws Exception {
		Utils.logLimits();

		final Github gitHub = new RtGithub(Utils.getToken());
		final GitHubFetcher factory = GitHubFetcher.using(gitHub);
		final Coordinates.Simple coords = new Coordinates.Simple("oliviercailloux", "testrel");
		final ProjectOnGitHub project = factory.getProject(coords);
		assertEquals(Utils.newURL("https://api.github.com/repos/oliviercailloux/testrel"), project.getApiURL());
		assertEquals(Utils.newURL("https://github.com/oliviercailloux/testrel"), project.getHtmlURL());
		assertEquals(LocalDateTime.of(2016, 04, 15, 10, 33, 27).toInstant(ZoneOffset.UTC), project.getCreatedAt());
		assertEquals(4, project.getIssues().size());
		assertTrue(project.getIssuesNamed("test1").size() == 1);
		assertFalse(project.getIssuesNamed("non-existant").size() == 1);
		assertEquals("testrel", project.getName());

		final User userC = GitHubFetcher.using(gitHub).getUser("oliviercailloux");
		LOGGER.debug(JsonUtils.asPrettyString(userC.getJson()));
		LOGGER.debug(JsonUtils.asPrettyString(project.getOwner().getJson()));
		assertEquals(userC, project.getOwner());
	}

}
