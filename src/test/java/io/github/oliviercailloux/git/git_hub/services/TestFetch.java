package io.github.oliviercailloux.git.git_hub.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import javax.json.JsonObject;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.core.Response;

import org.eclipse.jgit.lib.ObjectId;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.oliviercailloux.git.git_hub.model.GitHubToken;
import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinates;
import io.github.oliviercailloux.git.git_hub.model.graph_ql.RepositoryWithFiles;
import io.github.oliviercailloux.git.git_hub.model.graph_ql.RepositoryWithIssuesWithHistory;
import io.github.oliviercailloux.git.git_hub.model.v3.SearchResult;

public class TestFetch {

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(TestFetch.class);

	@Test
	public void testFetchAbsentGitHubProject() throws Exception {
		try (GitHubFetcherQL fetcher = GitHubFetcherQL.using(GitHubToken.getRealInstance())) {
			final RepositoryCoordinates coord = RepositoryCoordinates.from("this-user-does-not-exist-dfqfaglmkj45858",
					"repo");
			final Optional<RepositoryWithIssuesWithHistory> opt = fetcher.getRepository(coord);
			assertTrue(!opt.isPresent());
		}
	}

	@Test
	public void testFetchFiles() throws Exception {
		final RepositoryCoordinates coord = RepositoryCoordinates.from("oliviercailloux", "projets");
		try (GitHubFetcherQL fetcher = GitHubFetcherQL.using(GitHubToken.getRealInstance())) {
			/**
			 * TODO oddly enough, it sees only one file among the two files living in this
			 * sub-folder.
			 */
//			final Optional<RepositoryWithFiles> found = fetcher.getRepositoryWithFiles(coord, Paths.get("Licences/"));
			final Optional<RepositoryWithFiles> found = fetcher.getRepositoryWithFiles(coord,
					Paths.get("Autres énoncés/"));
			final RepositoryWithFiles repo = found.get();
			assertEquals(7, repo.getContentFromFileNames().size());
		}
	}

	/**
	 * @Test Disabled because requires to find a recent file and not too much
	 *       touched.
	 */
	public void testFindFileCreationExists() throws Exception {
		final RepositoryCoordinates coord = RepositoryCoordinates.from("tonyseg", "Rapport");
		try (GitHubFetcherV3 rawFetcher = GitHubFetcherV3.using(GitHubToken.getRealInstance())) {
			final Optional<ObjectId> found = rawFetcher.getCreationSha(coord,
					Paths.get("Presentation_12_octobre_2018.pdf"));
			final ObjectId sha = found.get();
			LOGGER.info(sha.toString());
			final Instant receivedTime = rawFetcher.getReceivedTime(coord, sha).get();
			assertEquals(Instant.parse("2018-02-17T22:45:05Z"), receivedTime);
		}
	}

	@Test
	public void testFindFileCreationExists2() throws Exception {
		final RepositoryCoordinates coord = RepositoryCoordinates.from("ynestacamille", "Java-L3-Eck-Ex");
		try (GitHubFetcherV3 rawFetcher = GitHubFetcherV3.using(GitHubToken.getRealInstance())) {
			final List<SearchResult> paths = rawFetcher.searchForCode(coord, "class", "java");
			for (SearchResult res : paths) {
				final Path path = res.getPath();
				LOGGER.info(path.toString());
				final Optional<ObjectId> shaOpt = rawFetcher.getCreationSha(coord, Paths.get("EE31SnackEyes.java"));
				final ObjectId sha = shaOpt.get();
				assertEquals("405de5b6edcc2ec49f35c59960bf877bef03eda7", sha.getName());
				// https://api.github.com/repos/ynestacamille/Java-L3-Eck-Ex/commits/405de5b6edcc2ec49f35c59960bf877bef03eda7
				// no push event lists that commit, though it appears as being "before" the
				// listed commit in the first PushEvent
				// (https://api.github.com/repos/ynestacamille/Java-L3-Eck-Ex/events). Possibly,
				// because it’s the very first commit (included in the CreateEvent that created
				// the branch master).
				final Optional<Instant> receivedTimeOpt = rawFetcher.getReceivedTime(coord, sha);
//				assertTrue(receivedTimeOpt.isPresent());
				assertFalse(receivedTimeOpt.isPresent());
			}
		}
	}

	@Test
	public void testFindFileCreationExistsOld() throws Exception {
		final RepositoryCoordinates coord = RepositoryCoordinates.from("oliviercailloux", "java-course");
		try (GitHubFetcherV3 rawFetcher = GitHubFetcherV3.using(GitHubToken.getRealInstance())) {
			final Optional<ObjectId> found = rawFetcher.getCreationSha(coord, Paths.get("JSON.adoc"));
			final ObjectId sha = found.get();
			LOGGER.debug(sha.getName());
			assertTrue(!rawFetcher.getReceivedTime(coord, sha).isPresent());
		}
	}

	@Test
	public void testFindFileCreationNotExists() throws Exception {
		final RepositoryCoordinates coord = RepositoryCoordinates.from("oliviercailloux", "testrel");
		try (GitHubFetcherV3 rawFetcher = GitHubFetcherV3.using(GitHubToken.getRealInstance())) {
			final Optional<ObjectId> found = rawFetcher.getCreationSha(coord, Paths.get("dfsddqgqd.ttt"));
			assertFalse(found.isPresent());
		}
	}

	@Test
	public void testFindFilePaths() throws Exception {
		final RepositoryCoordinates coord = RepositoryCoordinates.from("oliviercailloux", "java-course");
		try (GitHubFetcherV3 rawFetcher = GitHubFetcherV3.using(GitHubToken.getRealInstance())) {
			final List<SearchResult> found = rawFetcher.searchForFile(coord, "JSON", "adoc");
			LOGGER.debug(found.toString());
			assertEquals(2, found.size());
		}
	}

	@Test
	public void testFindFilePathsByCode() throws Exception {
		final RepositoryCoordinates coord = RepositoryCoordinates.from("oliviercailloux", "java-course");
		// final RepositoryCoordinates coord = RepositoryCoordinates.from("Raphaaal",
		// "Java-L3-Eck-Ex");
		try (GitHubFetcherV3 rawFetcher = GitHubFetcherV3.using(GitHubToken.getRealInstance())) {
			final List<SearchResult> found = rawFetcher.searchForCode(coord, "vote", "java");
			LOGGER.debug(found.toString());
			assertEquals(2, found.size());
		}
	}

	@Test
	public void testIssuesHistory() throws Exception {
		final RepositoryCoordinates coord = RepositoryCoordinates.from("MAMERY-DOUMBIA", "Dauphine-Pole-Info");
		try (GitHubFetcherQL fetcher = GitHubFetcherQL.using(GitHubToken.getRealInstance())) {
			final RepositoryWithIssuesWithHistory project = fetcher.getRepository(coord).get();
			assertEquals(1, project.getIssuesOriginallyNamed("PHP").size());
		}
	}

	@Test
	void testBasicJaxRs() throws Exception {
		final Builder request = ClientBuilder.newClient().target(GitHubFetcherQL.GRAPHQL_ENDPOINT).request();
		try (Response response = request.post(Entity.json("{}"))) {
			LOGGER.info("Response: {}.", response);
			assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response.getStatus());
		}
	}

	@Test
	public void testRawFetchAbsentGitHubProject() throws Exception {
		try (GitHubFetcherV3 rawFetcher = GitHubFetcherV3.using(GitHubToken.getRealInstance())) {
			final RepositoryCoordinates coord = RepositoryCoordinates.from("this-user-does-not-exist-dfqfaglmkj45858",
					"repo");
			// final User user = fetcher.getUser(username);
			final Optional<JsonObject> opt = rawFetcher.fetchGitHubProject(coord);
			assertTrue(!opt.isPresent());
		}
	}

	@Test
	public void testRawFetchGitHubProject() throws Exception {
		try (GitHubFetcherV3 rawFetcher = GitHubFetcherV3.using(GitHubToken.getRealInstance())) {
			final RepositoryCoordinates coord = RepositoryCoordinates.from("oliviercailloux", "java-course");
			// final User user = fetcher.getUser(username);
			final Optional<JsonObject> opt = rawFetcher.fetchGitHubProject(coord);
			assertTrue(opt.isPresent());
		}
	}

	@Test
	public void testRawFetchLastModification() throws Exception {
		final RepositoryCoordinates coord = RepositoryCoordinates.from("oliviercailloux", "testrel");
		try (GitHubFetcherV3 rawFetcher = GitHubFetcherV3.using(GitHubToken.getRealInstance())) {
			final Instant lastModification = rawFetcher.getLastModification(coord, Paths.get("Test.html")).get();
			LOGGER.debug("Last: {}.", lastModification);
			assertEquals(Instant.parse("2016-05-02T14:11:38Z"), lastModification);
		}
	}

}
