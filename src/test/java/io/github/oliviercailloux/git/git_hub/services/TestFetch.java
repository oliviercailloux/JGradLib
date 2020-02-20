package io.github.oliviercailloux.git.git_hub.services;

import static com.google.common.base.Preconditions.checkState;
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
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Sets;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.ImmutableGraph;

import io.github.oliviercailloux.git.ComplexClient;
import io.github.oliviercailloux.git.GitLocalHistory;
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
			assertEquals(8, repo.getContentFromFileNames().size());
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
	@EnabledIfEnvironmentVariable(named = "CONTINUOUS_INTEGRATION", matches = "true")
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
	@EnabledIfEnvironmentVariable(named = "CONTINUOUS_INTEGRATION", matches = "true")
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
	@EnabledIfEnvironmentVariable(named = "CONTINUOUS_INTEGRATION", matches = "true")
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
	@EnabledIfEnvironmentVariable(named = "CONTINUOUS_INTEGRATION", matches = "true")
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
			assertEquals(Instant.parse("2020-02-17T19:37:26Z"), lastModification);
		}
	}

	@Test
	@EnabledIfEnvironmentVariable(named = "CONTINUOUS_INTEGRATION", matches = "true")
	public void testGitHubHistoryProjets() throws Exception {
		final RepositoryCoordinates coord = RepositoryCoordinates.from("oliviercailloux", "projets");
		try (GitHubFetcherQL fetcher = GitHubFetcherQL.using(GitHubToken.getRealInstance())) {
			final GitHubHistory gHH = fetcher.getGitHubHistory(coord);
			final ImmutableMap<ObjectId, Instant> pushedDates = gHH.getPushedDates();
			final ImmutableMap<ObjectId, Instant> compPushedDates = gHH.getCorrectedAndCompletedPushedDates();

			assertEquals(ImmutableSet.of(ObjectId.fromString("f96c728044e885fceaf4a3ae926f1a13dd329758")),
					gHH.getRoots());
			assertTrue(gHH.getGraph().nodes().size() >= 164);
			assertEquals(gHH.getGraph().nodes(), compPushedDates.keySet());
			assertTrue(gHH.getPatchedKnowns().nodes().isEmpty());

			assertEquals(Instant.parse("2019-05-24T15:24:11Z"),
					pushedDates.get(ObjectId.fromString("81d36d64a5f6304d38b965897b9dc2ef3513a628")));
			assertEquals(Instant.parse("2019-05-24T15:24:11Z"),
					compPushedDates.get(ObjectId.fromString("81d36d64a5f6304d38b965897b9dc2ef3513a628")));
			assertEquals(Instant.parse("2019-05-06T14:57:15Z"),
					pushedDates.get(ObjectId.fromString("dbd7a9439cc79e365dde71930634a9051c82d596")));
			assertEquals(Instant.parse("2019-05-06T14:57:15Z"),
					compPushedDates.get(ObjectId.fromString("dbd7a9439cc79e365dde71930634a9051c82d596")));
			assertEquals(null, pushedDates.get(ObjectId.fromString("967489122c5d485bda8b571b2835dafa77af787f")));
			assertEquals(Instant.parse("2019-05-03T14:25:04Z"),
					compPushedDates.get(ObjectId.fromString("967489122c5d485bda8b571b2835dafa77af787f")));
			assertEquals(Instant.parse("2019-05-03T14:25:04Z"),
					pushedDates.get(ObjectId.fromString("3cc31dd1d5a5210b269f0a01d26fd2a670bc4404")));
			assertEquals(Instant.parse("2019-05-03T14:25:04Z"),
					compPushedDates.get(ObjectId.fromString("3cc31dd1d5a5210b269f0a01d26fd2a670bc4404")));
		}
	}

	@Test
	@EnabledIfEnvironmentVariable(named = "CONTINUOUS_INTEGRATION", matches = "true")
	public void testGitHubHistoryFiltered() throws Exception {
		final RepositoryCoordinates coord = RepositoryCoordinates.from("oliviercailloux", "projets");
		try (GitHubFetcherQL fetcher = GitHubFetcherQL.using(GitHubToken.getRealInstance())) {
			final GitHubHistory gHH = fetcher.getGitHubHistory(coord);
			final ImmutableMap<ObjectId, Instant> pushedDates = gHH.getPushedDates();
			final ImmutableMap<ObjectId, Instant> compPushedDates = gHH.getCorrectedAndCompletedPushedDates();
			assertEquals(Instant.parse("2016-09-23T13:01:18Z"),
					pushedDates.get(ObjectId.fromString("c2e245e7a7ca785fe8410213db89f142dda13bcf")));
			assertEquals(Instant.parse("2016-09-23T13:02:36Z"),
					pushedDates.get(ObjectId.fromString("cc61d5dd68156cb5429da29c23729d169639a64d")));

			final GitHubHistory filtered = gHH
					.filter((o) -> compPushedDates.get(o).isBefore(Instant.parse("2016-09-23T13:01:30Z")));
			assertTrue(gHH.getGraph().nodes().size() >= 164);
			assertEquals(16, filtered.getGraph().nodes().size());
			assertEquals(16, filtered.getGraph().edges().size());
		}
	}

	@Test
	@EnabledIfEnvironmentVariable(named = "CONTINUOUS_INTEGRATION", matches = "true")
	void testGitHubHistoryCLut() throws Exception {
		final RepositoryCoordinates coordinates = RepositoryCoordinates.from("oliviercailloux", "CLut");
		try (GitHubFetcherQL fetcher = GitHubFetcherQL.using(GitHubToken.getRealInstance())) {
			LOGGER.info("Proceeding with {}.", coordinates);
			final ComplexClient client = ComplexClient.aboutAndUsing(coordinates, Path.of("/tmp/"));
			final boolean retrieved = client.tryRetrieve();
			checkState(retrieved);
			final GitLocalHistory historyFromWorkTree = client.getWholeHistory();

			final GitHubHistory gHH = fetcher.getGitHubHistory(coordinates);
			final ImmutableMap<ObjectId, Instant> pushedDatesWithDeductions = gHH.getCorrectedAndCompletedPushedDates();

			final ImmutableGraph<RevCommit> g1 = historyFromWorkTree.getGraph();
			final ImmutableGraph<ObjectId> g2 = gHH.getGraph();

			checkState(historyFromWorkTree.getRoots().equals(gHH.getRoots()));
			checkState(g1.equals(g2), "Nb: " + g1.edges().size() + ", " + g2.edges().size() + "; Diff: "
					+ Sets.symmetricDifference(g1.edges(), g2.edges()) + ".");
			checkState(gHH.getGraph().nodes().equals(pushedDatesWithDeductions.keySet()));

			checkState(gHH.getCommitDates().equals(historyFromWorkTree.getCommitDates()));

			final ImmutableGraph<Object> expectedPatch = GraphBuilder.directed().immutable()
					.putEdge(ObjectId.fromString("21af8bffc747eaee04217b9c8bb9e3e4a3a6293d"),
							ObjectId.fromString("4016d7b1b09e2a188fb99d30d1ca5b0f726a4a3d"))
					.build();
			assertEquals(expectedPatch, gHH.getPatchedKnowns());
			assertTrue(gHH.getPushedBeforeCommitted().isEmpty());
		}
	}

	@Test
	@EnabledIfEnvironmentVariable(named = "CONTINUOUS_INTEGRATION", matches = "true")
	public void testGitHubHistoryJBiblio() throws Exception {
		final RepositoryCoordinates coord = RepositoryCoordinates.from("oliviercailloux", "J-Biblio");
		try (GitHubFetcherQL fetcher = GitHubFetcherQL.using(GitHubToken.getRealInstance())) {
			final GitHubHistory gHH = fetcher.getGitHubHistory(coord);
			final ImmutableMap<ObjectId, Instant> pushedDates = gHH.getPushedDates();
			final ImmutableMap<ObjectId, Instant> compPushedDates = gHH.getCorrectedAndCompletedPushedDates();

			assertEquals(ImmutableMap.of(), pushedDates);
			assertEquals(ImmutableSet.of(Instant.MIN), ImmutableSet.copyOf(compPushedDates.values()));
			assertEquals(compPushedDates.keySet(), gHH.getGraph().nodes());
		}
	}

	@Test
	@EnabledIfEnvironmentVariable(named = "CONTINUOUS_INTEGRATION", matches = "true")
	void testGitHubHistorySampleRestClient() throws Exception {
		final RepositoryCoordinates coordinates = RepositoryCoordinates.from("oliviercailloux", "sample-rest-client");
		try (GitHubFetcherQL fetcher = GitHubFetcherQL.using(GitHubToken.getRealInstance())) {
			LOGGER.info("Proceeding with {}.", coordinates);
			final ComplexClient client = ComplexClient.aboutAndUsing(coordinates, Path.of("/tmp/"));
			final boolean retrieved = client.tryRetrieve();
			checkState(retrieved);
			final GitLocalHistory historyFromWorkTree = client.getWholeHistory();

			final GitHubHistory gHH = fetcher.getGitHubHistory(coordinates);
			final ImmutableMap<ObjectId, Instant> pushedDatesWithDeductions = gHH.getCorrectedAndCompletedPushedDates();

			final ImmutableGraph<RevCommit> g1 = historyFromWorkTree.getGraph();
			final ImmutableGraph<ObjectId> g2 = gHH.getGraph();

			checkState(historyFromWorkTree.getRoots().equals(gHH.getRoots()));
			checkState(g1.equals(g2), "Nb: " + g1.edges().size() + ", " + g2.edges().size() + "; Diff: "
					+ Sets.symmetricDifference(g1.edges(), g2.edges()) + ".");
			checkState(gHH.getGraph().nodes().equals(pushedDatesWithDeductions.keySet()));

			checkState(gHH.getCommitDates().equals(historyFromWorkTree.getCommitDates()));

			final ImmutableGraph<Object> expectedPatch = GraphBuilder.directed().immutable()
					.putEdge(ObjectId.fromString("5d15007fde7cb7b62dc14a601cc18f5174174ada"),
							ObjectId.fromString("8d3a2ae555b0c82917db2ede5a5fd3d1cbe6f903"))
					.build();
			assertEquals(expectedPatch, gHH.getPatchedKnowns());
			assertTrue(gHH.getPushedBeforeCommitted().isEmpty());
		}
	}

	public static void main(String[] args) throws Exception {
		final ImmutableList<RepositoryCoordinates> allCoordinates;
		try (GitHubFetcherV3 fetcher = GitHubFetcherV3.using(GitHubToken.getRealInstance())) {
			allCoordinates = fetcher.getUserRepositories("oliviercailloux");
		}
//		allCoordinates = ImmutableList.of(RepositoryCoordinates.from("oliviercailloux", "Collaborative-exams"));
//		allCoordinates = ImmutableList.of(RepositoryCoordinates.from("oliviercailloux", "Collaborative-exams-2016"));

		try (GitHubFetcherQL fetcher = GitHubFetcherQL.using(GitHubToken.getRealInstance())) {
			for (RepositoryCoordinates coordinates : allCoordinates) {
				final ComplexClient client = ComplexClient.aboutAndUsing(coordinates, Path.of("/tmp/"));
				final boolean retrieved = client.tryRetrieve();
				checkState(retrieved);
				final GitLocalHistory historyFromWorkTree = client.getWholeHistory();

				final GitHubHistory gHH = fetcher.getGitHubHistory(coordinates);
				final ImmutableMap<ObjectId, Instant> pushedDatesWithDeductions = gHH
						.getCorrectedAndCompletedPushedDates();

				final ImmutableGraph<RevCommit> g1 = historyFromWorkTree.getGraph();
				final ImmutableGraph<ObjectId> g2 = gHH.getGraph();

				checkState(historyFromWorkTree.getRoots().equals(gHH.getRoots()));
				checkState(g1.equals(g2), "Nb: " + g1.edges().size() + ", " + g2.edges().size() + "; Diff: "
						+ Sets.symmetricDifference(g1.edges(), g2.edges()) + ".");
//				checkState(g1.equals((Graph<ObjectId>) Graphs.inducedSubgraph(g2, g1.nodes())),
//						"Nb: " + g1.edges().size() + ", " + g2.edges().size() + "; Diff: "
//								+ Sets.symmetricDifference(g1.edges(), g2.edges()) + ".");
//				if (!g1.equals(g2)) {
//					LOGGER.warn("Nb: " + g1.edges().size() + ", " + g2.edges().size() + "; Diff: "
//							+ Sets.symmetricDifference(g1.edges(), g2.edges()) + ".");
//				}
				checkState(gHH.getGraph().nodes().equals(pushedDatesWithDeductions.keySet()));

				final ImmutableSortedMap<Instant, ImmutableSet<ObjectId>> refsBySortedPushedDates = gHH
						.getRefsBySortedPushedDates();
				if (!refsBySortedPushedDates.isEmpty()) {
					final Instant lastPush = refsBySortedPushedDates.lastKey();
					LOGGER.info("Last commits: {}, {}.", lastPush, refsBySortedPushedDates.get(lastPush));
				} else {
					assertTrue(gHH.getPushedDates().isEmpty());
					LOGGER.warn("No pushed dates.");
				}

				checkState(gHH.getCommitDates().equals(historyFromWorkTree.getCommitDates()));

				if (!gHH.getPatchedKnowns().nodes().isEmpty()) {
					LOGGER.warn("Patched knowns: {}.", gHH.getPatchedKnowns());
				}
				if (!gHH.getPushedBeforeCommitted().isEmpty()) {
					LOGGER.warn("Pushed before committed: {}.", gHH.getPushedBeforeCommitted());
				}
			}
		}
	}

}
