package io.github.oliviercailloux.st_projects.services.git_hub;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

import io.github.oliviercailloux.git.git_hub.model.graph_ql.RepositoryWithIssuesWithHistory;
import io.github.oliviercailloux.git.git_hub.services.GitHubFetcherQL;
import io.github.oliviercailloux.st_projects.model.Project;
import io.github.oliviercailloux.st_projects.utils.Utils;

public class TestFinder {

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(TestFinder.class);

	@Test
	public void testFindMyRepo() throws IOException {
		final Project myProject = Project.from("XMCDA-2.2.1-JAXB");
		final List<RepositoryWithIssuesWithHistory> repositories;
		try (GitHubFetcherQL fetcher = GitHubFetcherQL.using(Utils.getToken())) {
			repositories = fetcher.find(myProject.getGitHubName(), Instant.parse("2015-12-01T00:00:00Z"));
		}
		assertFalse(repositories.isEmpty());
		final Instant expectedCreation = LocalDateTime.of(2016, Month.JULY, 29, 17, 34, 19).toInstant(ZoneOffset.UTC);
		final RepositoryWithIssuesWithHistory firstFound = repositories.get(0);
		LOGGER.debug("Created at: {}." + firstFound.getBare().getCreatedAt());
		assertTrue(repositories.stream().anyMatch((p) -> p.getBare().getCreatedAt().equals(expectedCreation)));
	}

	@Test
	public void testFindTooMany() throws IOException {
		final Project myProject = Project.from("Biblio");
		try (GitHubFetcherQL fetcher = GitHubFetcherQL.using(Utils.getToken())) {
			assertThrows(UnsupportedOperationException.class,
					() -> fetcher.find(myProject.getGitHubName(), Instant.parse("2017-09-01T00:00:00Z")));
		}
	}

	@Test
	public void testHasPom() throws IOException {
		final Project myProject = Project.from("XMCDA-2.2.1-JAXB");
		final List<RepositoryWithIssuesWithHistory> found;
		try (GitHubFetcherQL fetcher = GitHubFetcherQL.using(Utils.getToken())) {
			found = fetcher.find(myProject.getGitHubName(), Instant.parse("2015-12-01T00:00:00Z"));
		}
		assertTrue(found.size() >= 1);

		final ImmutableList<RepositoryWithIssuesWithHistory> foundWithPom = found.stream()
				.filter((r) -> r.getFiles().contains("pom.xml")).collect(ImmutableList.toImmutableList());
		LOGGER.debug("Found: {}.", found);
		LOGGER.debug("With POM: {}.", foundWithPom);
		assertFalse(foundWithPom.isEmpty());
	}

	@Test
	public void testNoFindTooLate() throws IOException {
		final Project myProject = Project.from("java-course");
		try (GitHubFetcherQL finder = GitHubFetcherQL.using(Utils.getToken())) {
			final List<RepositoryWithIssuesWithHistory> found = finder.find(myProject.getGitHubName(),
					Instant.parse("2049-10-04T00:00:00Z"));
			assertTrue(found.isEmpty());
		}
	}

}
