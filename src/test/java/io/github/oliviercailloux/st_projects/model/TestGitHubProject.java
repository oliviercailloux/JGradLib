package io.github.oliviercailloux.st_projects.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Iterables;
import com.jcabi.github.Coordinates;
import com.jcabi.github.RtGithub;

import io.github.oliviercailloux.st_projects.services.git_hub.GitHubFetcher;
import io.github.oliviercailloux.st_projects.services.git_hub.RepositoryFinder;
import io.github.oliviercailloux.st_projects.utils.JsonUtils;
import io.github.oliviercailloux.st_projects.utils.Utils;

public class TestGitHubProject {

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(TestGitHubProject.class);

	@Test
	public void testGetRepo() throws Exception {
		try (GitHubFetcher fetcher = GitHubFetcher.using(Utils.getToken())) {
			fetcher.setToken(Utils.getToken());
			final RepositoryWithIssuesWithHistoryQL repo = fetcher
					.getProject(new Coordinates.Simple("MAMERY-DOUMBIA", "Dauphine-Pole-Info")).get();
			LOGGER.debug("Issues with history: {}.", repo.getIssues());
			assertEquals(13, repo.getIssues().size());
		}
	}

	@Test
	public void testGithubIssues() throws Exception {
		final RepositoryFinder finder = new RepositoryFinder();
		final RtGithub gitHub = new RtGithub(Utils.getToken());
		finder.setGitHub(gitHub);
		final List<Coordinates> found = finder.find(Project.from("Dauphine-Open-Data"));
		assertTrue(found.size() >= 1);
		final List<Coordinates> foundWithPom = finder.withPom();
		final Coordinates matching = Iterables.getOnlyElement(foundWithPom);
		final RepositoryWithIssuesWithHistoryQL project = GitHubFetcher.using(Utils.getToken()).getProject(matching)
				.get();
		assertTrue(project.getIssuesOriginallyNamed("Course").toString(),
				project.getIssuesOriginallyNamed("Course").size() == 1);
		assertFalse(project.getIssuesOriginallyNamed("Triple").size() == 1);
	}

	@Test
	public void testProject() throws Exception {
		Utils.logLimits();

		try (GitHubFetcher factory = GitHubFetcher.using(Utils.getToken())) {
			final Coordinates.Simple coords = new Coordinates.Simple("oliviercailloux", "testrel");
			final RepositoryWithIssuesWithHistoryQL project = factory.getProject(coords).get();
			assertEquals(Utils.newURL("https://github.com/oliviercailloux/testrel/"), project.getBare().getHtmlURL());
			assertEquals(LocalDateTime.of(2016, 04, 15, 10, 33, 27).toInstant(ZoneOffset.UTC),
					project.getBare().getCreatedAt());
			assertEquals(5, project.getIssues().size());
			assertTrue(project.getIssuesOriginallyNamed("test1").size() == 1);
			assertFalse(project.getIssuesOriginallyNamed("non-existant").size() == 1);
			assertEquals("testrel", project.getBare().getName());

			LOGGER.debug(JsonUtils.asPrettyString(project.getOwner().getJson()));
			assertEquals("oliviercailloux", project.getOwner().getLogin());
		}
	}

	@Test
	public void testProjectToJson() throws Exception {
		final Project p = Project.from("pn", ModelMocker.getFunctionalities("pn", 1),
				Instant.parse("2018-01-01T00:00:00Z"), Instant.parse("2018-01-01T00:00:00Z"));
		final String json;
		try (Jsonb jsonb = JsonbBuilder.create()) {
			json = jsonb.toJson(p);
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
		LOGGER.debug("Serialized json: {}.", json);
		assertEquals("{\"name\":\"pn\",\"gitHubName\":\"pn\","
				+ "\"functionalities\":[{\"name\":\"pn-f1\",\"description\":\"pn-d1\",\"difficulty\":1}],"
				+ "\"lastModification\":\"2018-01-01T00:00:00Z\",\"queried\":\"2018-01-01T00:00:00Z\"}", json);
	}

}
