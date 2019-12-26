package io.github.oliviercailloux.git.git_hub.services;

import static com.google.common.base.Preconditions.checkState;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.json.JsonObject;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.core.Response;

import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Sets;
import com.google.common.collect.Streams;
import com.google.common.graph.EndpointPair;
import com.google.common.graph.ImmutableGraph;
import com.google.common.graph.Traverser;

import io.github.oliviercailloux.git.ComplexClient;
import io.github.oliviercailloux.git.GitGenericHistory;
import io.github.oliviercailloux.git.GitHistory;
import io.github.oliviercailloux.git.GitUtils;
import io.github.oliviercailloux.git.git_hub.model.GitHubHistory;
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

	@Test
	public void testFetchPushedDates() throws Exception {
		final RepositoryCoordinates coord = RepositoryCoordinates.from("oliviercailloux", "projets");
		try (GitHubFetcherQL fetcher = GitHubFetcherQL.using(GitHubToken.getRealInstance())) {
			final GitHubHistory gHH = fetcher.getGitHubHistory(coord);
			final ImmutableMap<ObjectId, Instant> pushedDates = gHH.getPushedDatesWithDeductions();
			final GitGenericHistory<ObjectId> history = gHH.getHistory();

			assertEquals(ImmutableSet.of(ObjectId.fromString("f96c728044e885fceaf4a3ae926f1a13dd329758")),
					history.getRoots());
			assertEquals(155, history.getGraph().nodes().size());
			assertEquals(history.getGraph().nodes(), pushedDates.keySet());

			assertEquals(Instant.parse("2019-05-24T15:24:11Z"),
					pushedDates.get(ObjectId.fromString("81d36d64a5f6304d38b965897b9dc2ef3513a628")));
			assertEquals(Instant.parse("2019-05-06T14:57:15Z"),
					pushedDates.get(ObjectId.fromString("dbd7a9439cc79e365dde71930634a9051c82d596")));
			assertEquals(Instant.parse("2019-05-06T14:57:15Z"),
					pushedDates.get(ObjectId.fromString("967489122c5d485bda8b571b2835dafa77af787f")));
			assertEquals(Instant.parse("2019-05-03T14:25:04Z"),
					pushedDates.get(ObjectId.fromString("3cc31dd1d5a5210b269f0a01d26fd2a670bc4404")));

			final Set<EndpointPair<ObjectId>> edges = history.getGraph().edges();
			for (EndpointPair<ObjectId> edge : edges) {
				final ObjectId child = edge.source();
				final ObjectId parent = edge.target();
				assertTrue(pushedDates.get(parent).compareTo(pushedDates.get(child)) <= 0);
			}
		}
	}

	/**
	 * Far too long to be used systematically!
	 */
	@Test
	public void testFetchPushedDatesAll() throws Exception {
		final ImmutableList<RepositoryCoordinates> allCoordinates;
//		try (GitHubFetcherV3 fetcher = GitHubFetcherV3.using(GitHubToken.getRealInstance())) {
//			allCoordinates = fetcher.getUserRepositories("oliviercailloux");
//		}
//		allCoordinates = ImmutableList.of(RepositoryCoordinates.from("oliviercailloux", "CLut"));
//		allCoordinates = ImmutableList.of(RepositoryCoordinates.from("oliviercailloux", "Collaborative-exams"));
		allCoordinates = ImmutableList.of(RepositoryCoordinates.from("oliviercailloux", "Collaborative-exams-2016"));
		/** TODO jing has pbl diff; I suppose possible because rewrite of history. */
//		allCoordinates = ImmutableList.of(RepositoryCoordinates.from("oliviercailloux", "jing-trang"));
		/**
		 * TODO change refPrefix to /refs/ to get also tags.
		 */

		try (GitHubFetcherQL fetcher = GitHubFetcherQL.using(GitHubToken.getRealInstance())) {
			for (RepositoryCoordinates coordinates : allCoordinates) {
				LOGGER.info("Proceeding with {}.", coordinates);
				final ComplexClient client = ComplexClient.aboutAndUsing(coordinates, Path.of("/tmp/"));
				final boolean retrieved = client.tryRetrieve();
				checkState(retrieved);
				final GitHistory historyFromWorkTree = client.getWholeHistory();

				LOGGER.info("Fetching.");
				final GitHubHistory gHH = fetcher.getGitHubHistory(coordinates);
				LOGGER.info("Fetched.");
				final ImmutableMap<ObjectId, Instant> pushedDates = gHH.getPushedDates();
				final ImmutableMap<ObjectId, Instant> pushedDatesWithDeductions = gHH.getPushedDatesWithDeductions();
				final ImmutableSetMultimap<Instant, ObjectId> commitsByPushDate = pushedDatesWithDeductions.asMultimap()
						.inverse();
				final ImmutableSortedMap<Instant, Collection<ObjectId>> commitsBySortedPushDate = ImmutableSortedMap
						.copyOf(commitsByPushDate.asMap());

				final GitGenericHistory<ObjectId> historyFromGitHub = gHH.getHistory();
				final ImmutableGraph<RevCommit> g1 = historyFromWorkTree.getGraph();
				final ImmutableGraph<ObjectId> g2 = historyFromGitHub.getGraph();

				final ObjectId oid9d = ObjectId.fromString("9d9391f6c4c68f69c81b90348f416d6a64d3f1d5");
				final ObjectId oide4 = ObjectId.fromString("e43d36f02b5eb65f7d5b4b4c674a003956447c1c");
				final Iterable<ObjectId> thoseChildren = Traverser.forGraph(g2::predecessors).breadthFirst(oid9d);
				for (ObjectId child : thoseChildren) {
					LOGGER.info("Child {} pushed at {}.", child.getName(),
							pushedDates.getOrDefault(child, Instant.MAX));
				}
				LOGGER.info("Pred: {}.", g2.predecessors(oid9d));
				LOGGER.info("Succ: {}.", g2.successors(oide4));
				final Instant minPushedDateAmongThoseChildren = Streams.stream(thoseChildren)
						.map((o) -> pushedDates.getOrDefault(o, Instant.MAX)).min(Instant::compareTo).get();
				LOGGER.warn("Max: {}.", Instant.MAX);
				LOGGER.warn("Min: {}.", minPushedDateAmongThoseChildren);
				assertEquals(historyFromWorkTree.getRoots(), historyFromGitHub.getRoots());
				assertEquals(g1, g2, "Nb: " + g1.edges().size() + ", " + g2.edges().size() + "; Diff: "
						+ Sets.symmetricDifference(g1.edges(), g2.edges()) + ".");
				assertEquals(historyFromGitHub.getGraph().nodes(), pushedDatesWithDeductions.keySet());

				final Instant lastPush = commitsBySortedPushDate.lastKey();
				LOGGER.info("Last commits: {}, {}.", lastPush, commitsByPushDate.get(lastPush));

				final ImmutableSet<ObjectId> pushedCommits = pushedDatesWithDeductions.keySet();
				try (RevWalk walk = new RevWalk(
						new FileRepository(client.getProjectDirectory().resolve(".git").toFile()))) {
					for (ObjectId pushedCommit : pushedCommits) {
						final RevCommit pushedRevCommit;
						pushedRevCommit = walk.parseCommit(pushedCommit);
						final ZonedDateTime creationTime = GitUtils.getCreationTime(pushedRevCommit);
						final Instant pushedDate = pushedDatesWithDeductions.get(pushedCommit);
						if (pushedDate.isBefore(creationTime.toInstant())) {
							LOGGER.warn("Pushed before creation: {}-{}.", coordinates, pushedCommit);
						}
					}
				}

				final Comparator<ObjectId> comparingByPushedDate = Comparator
						.comparing((c) -> pushedDatesWithDeductions.get(c));

				final Set<EndpointPair<ObjectId>> edges = g2.edges();
				for (EndpointPair<ObjectId> edge : edges) {
					final ObjectId child = edge.source();
					final ObjectId parent = edge.target();
//					assertTrue(comparingByPushedDate.compare(parent, child) <= 0,
//							String.format("Parent %s pushed at %s, after child %s pushed at %s.", parent.getName(),
//									pushedDates.get(parent), child.getName(), pushedDates.get(child)));
					if (comparingByPushedDate.compare(parent, child) > 0) {
						LOGGER.warn(String.format("Parent %s pushed at %s, after child %s pushed at %s.",
								parent.getName(), pushedDatesWithDeductions.get(parent), child.getName(),
								pushedDatesWithDeductions.get(child)));
					}
				}
			}
		}
	}

}
