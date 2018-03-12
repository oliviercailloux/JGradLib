package io.github.oliviercailloux.st_projects.services.git_hub;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import javax.json.JsonArray;
import javax.json.JsonObject;

import org.eclipse.jgit.lib.ObjectId;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jcabi.github.Coordinates;
import com.jcabi.github.Github;
import com.jcabi.github.Issue;
import com.jcabi.github.Issues;
import com.jcabi.github.Repo;
import com.jcabi.github.RtGithub;

import io.github.oliviercailloux.git_hub.low.SearchResult;
import io.github.oliviercailloux.st_projects.model.Project;
import io.github.oliviercailloux.st_projects.model.RepositoryWithIssuesWithHistory;
import io.github.oliviercailloux.st_projects.services.read.IllegalFormat;
import io.github.oliviercailloux.st_projects.utils.Utils;

public class TestFetch {

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(TestFetch.class);

	@Test
	public void testFetchAbsentGitHubProject() throws Exception {
		try (GitHubFetcher fetcher = GitHubFetcher.using(Utils.getToken())) {
			final Coordinates.Simple coord = new Coordinates.Simple("this-user-does-not-exist-dfqfaglmkj45858", "repo");
			final Optional<RepositoryWithIssuesWithHistory> opt = fetcher.getProject(coord);
			assertTrue(!opt.isPresent());
		}
	}

	@Test
	public void testFetchAssignees() throws Exception {
		final Github github = new RtGithub(Utils.getToken());
		final Repo repo = github.repos().get(new Coordinates.Simple("waffleio", "waffle.io"));
		final Issues issues = repo.issues();
		final Issue issue = issues.get(3259);
		final JsonArray jsonArray = issue.json().getJsonArray("assignees");
		LOGGER.debug("Issue assignees: {}.", jsonArray);
		assertEquals(2, jsonArray.size());
		/** https://github.com/waffleio/waffle.io/issues/3259 */
	}

	@Test
	public void testFindFileCreationExists() throws Exception {
		final Coordinates.Simple coord = new Coordinates.Simple("Raphaaal", "Java-L3-Eck-Ex");
		try (RawGitHubFetcher rawFetcher = new RawGitHubFetcher()) {
			rawFetcher.setToken(Utils.getToken());
			final Optional<ObjectId> found = rawFetcher.getCreationSha(coord, Paths.get("EE34BreakLine.java"));
			final ObjectId sha = found.get();
			LOGGER.info(sha.toString());
			final Instant receivedTime = rawFetcher.getReceivedTime(coord, sha).get();
			assertEquals(Instant.parse("2018-02-17T22:45:05Z"), receivedTime);
		}
	}

	@Test
	public void testFindFileCreationExists2() throws Exception {
		final Coordinates.Simple coord = new Coordinates.Simple("ynestacamille", "Java-L3-Eck-Ex");
		try (RawGitHubFetcher rawFetcher = new RawGitHubFetcher()) {
			rawFetcher.setToken(Utils.getToken());
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

	@Test(expected = IllegalStateException.class)
	public void testFindFileCreationExistsOld() throws Exception {
		final Coordinates.Simple coord = new Coordinates.Simple("oliviercailloux", "java-course");
		try (RawGitHubFetcher rawFetcher = new RawGitHubFetcher()) {
			rawFetcher.setToken(Utils.getToken());
			final Optional<ObjectId> found = rawFetcher.getCreationSha(coord, Paths.get("JSON.adoc"));
			final ObjectId sha = found.get();
			LOGGER.debug(sha.getName());
			final Instant receivedTime = rawFetcher.getReceivedTime(coord, sha).get();
			assertEquals(Instant.parse("2018-02-17T22:45:05Z"), receivedTime);
		}
	}

	@Test
	public void testFindFileCreationNotExists() throws Exception {
		final Coordinates.Simple coord = new Coordinates.Simple("oliviercailloux", "testrel");
		try (RawGitHubFetcher rawFetcher = new RawGitHubFetcher()) {
			rawFetcher.setToken(Utils.getToken());
			final Optional<ObjectId> found = rawFetcher.getCreationSha(coord, Paths.get("dfsddqgqd.ttt"));
			assertFalse(found.isPresent());
		}
	}

	@Test
	public void testFindFilePaths() throws Exception {
		final Coordinates.Simple coord = new Coordinates.Simple("oliviercailloux", "java-course");
		try (RawGitHubFetcher rawFetcher = new RawGitHubFetcher()) {
			final List<SearchResult> found = rawFetcher.searchForFile(coord, "JSON", "adoc");
			LOGGER.debug(found.toString());
			assertEquals(2, found.size());
		}
	}

	@Test
	public void testFindFilePathsByCode() throws Exception {
		final Coordinates.Simple coord = new Coordinates.Simple("oliviercailloux", "java-course");
		// final Coordinates.Simple coord = new Coordinates.Simple("Raphaaal",
		// "Java-L3-Eck-Ex");
		try (RawGitHubFetcher rawFetcher = new RawGitHubFetcher()) {
			final List<SearchResult> found = rawFetcher.searchForCode(coord, "vote", "java");
			LOGGER.debug(found.toString());
			assertEquals(2, found.size());
		}
	}

	@Test
	public void testIssuesHistory() throws Exception {
		final Coordinates.Simple coord = new Coordinates.Simple("MAMERY-DOUMBIA", "Dauphine-Pole-Info");
		try (GitHubFetcher fetcher = GitHubFetcher.using(Utils.getToken())) {
			final RepositoryWithIssuesWithHistory project = fetcher.getProject(coord).get();
			assertEquals(1, project.getIssuesOriginallyNamed("PHP").size());
		}
	}

	@Test
	public void testRawFetchAbsentGitHubProject() throws Exception {
		try (RawGitHubFetcher rawFetcher = new RawGitHubFetcher()) {
			rawFetcher.setToken(Utils.getToken());
			final Coordinates.Simple coord = new Coordinates.Simple("this-user-does-not-exist-dfqfaglmkj45858", "repo");
			// final User user = fetcher.getUser(username);
			final Optional<JsonObject> opt = rawFetcher.fetchGitHubProject(coord);
			assertTrue(!opt.isPresent());
		}
	}

	@Test
	public void testRawFetchGitHubProject() throws Exception {
		try (RawGitHubFetcher rawFetcher = new RawGitHubFetcher()) {
			rawFetcher.setToken(Utils.getToken());
			final Coordinates.Simple coord = new Coordinates.Simple("oliviercailloux", "java-course");
			// final User user = fetcher.getUser(username);
			final Optional<JsonObject> opt = rawFetcher.fetchGitHubProject(coord);
			assertTrue(opt.isPresent());
		}
	}

	@Test
	public void testRawFetchLastModification() throws Exception {
		final Coordinates.Simple coord = new Coordinates.Simple("oliviercailloux", "testrel");
		try (RawGitHubFetcher rawFetcher = new RawGitHubFetcher()) {
			rawFetcher.setToken(Utils.getToken());
			final Instant lastModification = rawFetcher.getLastModification(coord, Paths.get("Test.html")).get();
			LOGGER.debug("Last: {}.", lastModification);
			assertEquals(Instant.parse("2016-05-02T14:11:38Z"), lastModification);
		}
	}

	@Test
	public void testRawFetchProjects() throws IllegalFormat, IOException {
		try (RawGitHubFetcher rawFetcher = new RawGitHubFetcher()) {
			rawFetcher.setToken(Utils.getToken());
			final List<Project> projects = rawFetcher.fetchProjects();
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
