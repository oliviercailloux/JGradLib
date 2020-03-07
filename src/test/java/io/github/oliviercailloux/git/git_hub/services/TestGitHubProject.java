package io.github.oliviercailloux.git.git_hub.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.UnmodifiableIterator;

import io.github.oliviercailloux.git.git_hub.model.GitHubToken;
import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinates;
import io.github.oliviercailloux.git.git_hub.model.graph_ql.IssueWithHistory;
import io.github.oliviercailloux.git.git_hub.model.graph_ql.RepositoryWithIssuesWithHistory;
import io.github.oliviercailloux.json.PrintableJsonObjectFactory;
import io.github.oliviercailloux.students_project_following.Project;

public class TestGitHubProject {

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(TestGitHubProject.class);

	@Test
	public void testGetRepo() throws Exception {
		try (GitHubFetcherQL fetcher = GitHubFetcherQL.using(GitHubToken.getRealInstance())) {
			final RepositoryWithIssuesWithHistory repo = fetcher
					.getRepository(RepositoryCoordinates.from("MAMERY-DOUMBIA", "Dauphine-Pole-Info")).get();
			LOGGER.debug("Issues with history: {}.", repo.getIssues());
			assertEquals(13, repo.getIssues().size());
		}
	}

	@Test
	public void testGithubIssues() throws Exception {
		final Project myProject = Project.from("Dauphine-Open-Data");
		final List<RepositoryWithIssuesWithHistory> found;
		try (GitHubFetcherQL fetcher = GitHubFetcherQL.using(GitHubToken.getRealInstance())) {
			found = fetcher.find(myProject.getGitHubName(), Instant.EPOCH);
		}
		assertTrue(found.size() >= 1);

		final ImmutableList<RepositoryWithIssuesWithHistory> foundWithPom = found.stream()
				.filter((r) -> r.getFiles().contains("pom.xml")).collect(ImmutableList.toImmutableList());
		final RepositoryWithIssuesWithHistory repository = foundWithPom.get(0);
		assertEquals("benzait27", repository.getOwner().getLogin());
		assertTrue(repository.getIssuesOriginallyNamed("Course").size() == 1);
		assertFalse(repository.getIssuesOriginallyNamed("Triple").size() == 1);
	}

	@Test
	public void testHomonymIssues() throws Exception {
		final RepositoryWithIssuesWithHistory repo;
		try (GitHubFetcherQL fetcher = GitHubFetcherQL.using(GitHubToken.getRealInstance())) {
			repo = fetcher.getRepository(RepositoryCoordinates.from("oliviercailloux", "testrel")).get();
		}
		final ImmutableSortedSet<IssueWithHistory> issues = repo.getIssuesCorrespondingTo("AFct");
		LOGGER.debug("Issues with history corr: {}.", issues);
		assertEquals(2, issues.size());
		final UnmodifiableIterator<IssueWithHistory> it = issues.iterator();
		final IssueWithHistory first = it.next();
		final IssueWithHistory second = it.next();
		assertEquals("AFct", first.getOriginalName());
		assertTrue(first.getFirstSnapshotDone().isPresent());
		assertEquals("AFct-2", second.getOriginalName());
		assertFalse(second.getFirstSnapshotDone().isPresent());
	}

	@Test
	public void testProject() throws Exception {
		try (GitHubFetcherQL factory = GitHubFetcherQL.using(GitHubToken.getRealInstance())) {
			final RepositoryCoordinates coords = RepositoryCoordinates.from("oliviercailloux", "testrel");
			final RepositoryWithIssuesWithHistory project = factory.getRepository(coords).get();
			assertEquals(URI.create("https://github.com/oliviercailloux/testrel"), project.getBare().getURI());
			assertEquals(LocalDateTime.of(2016, 04, 15, 10, 33, 27).toInstant(ZoneOffset.UTC),
					project.getBare().getCreatedAt());
			assertEquals(10, project.getIssues().size());
			assertTrue(project.getIssuesOriginallyNamed("test1").size() == 1);
			assertFalse(project.getIssuesOriginallyNamed("non-existant").size() == 1);
			assertEquals("testrel", project.getBare().getName());

			LOGGER.debug(PrintableJsonObjectFactory.wrapObject(project.getOwner().getJson()).toString());
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
		assertEquals("{\"name\":\"pn\",\"gitHubName\":\"pn\",\"URI\":\"http://example.com\","
				+ "\"functionalities\":[{\"name\":\"pn-f1\",\"description\":\"pn-d1\",\"difficulty\":1}],"
				+ "\"lastModification\":\"2018-01-01T00:00:00Z\",\"queried\":\"2018-01-01T00:00:00Z\"}", json);
	}

}
