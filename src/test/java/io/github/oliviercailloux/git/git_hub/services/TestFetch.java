package io.github.oliviercailloux.git.git_hub.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.eclipse.jgit.lib.ObjectId;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import io.github.oliviercailloux.git.GitHubHistory;
import io.github.oliviercailloux.git.filter.GitHistory;
import io.github.oliviercailloux.git.git_hub.model.GitHubToken;
import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinates;
import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinatesWithPrefix;
import io.github.oliviercailloux.git.git_hub.model.graph_ql.RepositoryWithFiles;
import io.github.oliviercailloux.git.git_hub.model.graph_ql.RepositoryWithIssuesWithHistory;
import io.github.oliviercailloux.git.git_hub.model.v3.SearchResult;
import jakarta.json.JsonObject;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation.Builder;
import jakarta.ws.rs.core.Response;

public class TestFetch {

  @SuppressWarnings("unused")
  private static final Logger LOGGER = LoggerFactory.getLogger(TestFetch.class);

  @Test
  public void testFetchAbsentGitHubProject() throws Exception {
    try (GitHubFetcherQL fetcher = GitHubFetcherQL.using(GitHubToken.getRealInstance())) {
      final RepositoryCoordinates coord =
          RepositoryCoordinates.from("this-user-does-not-exist-dfqfaglmkj45858", "repo");
      final Optional<RepositoryWithIssuesWithHistory> opt = fetcher.getRepository(coord);
      assertTrue(!opt.isPresent());
    }
  }

  @Test
  public void testFetchFiles() throws Exception {
    final RepositoryCoordinates coord = RepositoryCoordinates.from("oliviercailloux", "projets");
    try (GitHubFetcherQL fetcher = GitHubFetcherQL.using(GitHubToken.getRealInstance())) {
      final Optional<RepositoryWithFiles> found =
          fetcher.getRepositoryWithFiles(coord, Path.of("2D Library/"));
      final RepositoryWithFiles repo = found.get();
      assertEquals(1, repo.getContentFromFileNames().size());
    }
    try (GitHubFetcherQL fetcher = GitHubFetcherQL.using(GitHubToken.getRealInstance())) {
      final Optional<RepositoryWithFiles> found =
          fetcher.getRepositoryWithFiles(coord, Path.of(""));
      final RepositoryWithFiles repo = found.get();
      assertEquals(14, repo.getContentFromFileNames().size(),
          repo.getContentFromFileNames().keySet().toString());
    }
  }

  /**
   * @Test Disabled because requires to find a recent file and not too much touched.
   */
  public void testFindFileCreationExists() throws Exception {
    final RepositoryCoordinates coord = RepositoryCoordinates.from("tonyseg", "Rapport");
    try (GitHubFetcherV3 rawFetcher = GitHubFetcherV3.using(GitHubToken.getRealInstance())) {
      final Optional<ObjectId> found =
          rawFetcher.getCreationSha(coord, Paths.get("Presentation_12_octobre_2018.pdf"));
      final ObjectId sha = found.get();
      LOGGER.info(sha.toString());
      final Instant receivedTime = rawFetcher.getReceivedTime(coord, sha).get();
      assertEquals(Instant.parse("2018-02-17T22:45:05Z"), receivedTime);
    }
  }

  @Test
  public void testFindFileCreationExists2() throws Exception {
    final RepositoryCoordinates coord =
        RepositoryCoordinates.from("ynestacamille", "Java-L3-Eck-Ex");
    try (GitHubFetcherV3 rawFetcher = GitHubFetcherV3.using(GitHubToken.getRealInstance())) {
      final List<SearchResult> paths = rawFetcher.searchForCode(coord, "class", "java");
      for (SearchResult res : paths) {
        final Path path = res.getPath();
        LOGGER.info(path.toString());
        final Optional<ObjectId> shaOpt =
            rawFetcher.getCreationSha(coord, Paths.get("EE31SnackEyes.java"));
        final ObjectId sha = shaOpt.get();
        assertEquals("405de5b6edcc2ec49f35c59960bf877bef03eda7", sha.getName());
        // https://api.github.com/repos/ynestacamille/Java-L3-Eck-Ex/commits/405de5b6edcc2ec49f35c59960bf877bef03eda7
        // no push event lists that commit, though it appears as being "before" the
        // listed commit in the first PushEvent
        // (https://api.github.com/repos/ynestacamille/Java-L3-Eck-Ex/events). Possibly,
        // because it’s the very first commit (included in the CreateEvent that created
        // the branch master).
        final Optional<Instant> receivedTimeOpt = rawFetcher.getReceivedTime(coord, sha);
        // assertTrue(receivedTimeOpt.isPresent());
        assertFalse(receivedTimeOpt.isPresent());
      }
    }
  }

  @Test
  public void testFindFileCreationExistsOld() throws Exception {
    final RepositoryCoordinates coord =
        RepositoryCoordinates.from("oliviercailloux", "java-course");
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
    final RepositoryCoordinates coord =
        RepositoryCoordinates.from("oliviercailloux", "java-course");
    try (GitHubFetcherV3 rawFetcher = GitHubFetcherV3.using(GitHubToken.getRealInstance())) {
      final List<SearchResult> found = rawFetcher.searchForFile(coord, "JSON", "adoc");
      LOGGER.debug(found.toString());
      assertEquals(2, found.size());
    }
  }

  @Test
  public void testFindFilePathsByCode() throws Exception {
    final RepositoryCoordinates coord =
        RepositoryCoordinates.from("oliviercailloux", "java-course");
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
    final RepositoryCoordinates coord =
        RepositoryCoordinates.from("MAMERY-DOUMBIA", "Dauphine-Pole-Info");
    try (GitHubFetcherQL fetcher = GitHubFetcherQL.using(GitHubToken.getRealInstance())) {
      final RepositoryWithIssuesWithHistory project = fetcher.getRepository(coord).get();
      assertEquals(1, project.getIssuesOriginallyNamed("PHP").size());
    }
  }

  @Test
  void testBasicJaxRs() throws Exception {
    final Builder request =
        ClientBuilder.newClient().target(GitHubFetcherQL.GRAPHQL_ENDPOINT).request();
    try (Response response = request.post(Entity.json("{}"))) {
      LOGGER.info("Response: {}.", response);
      assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response.getStatus());
    }
  }

  @Test
  public void testRawFetchAbsentGitHubProject() throws Exception {
    try (GitHubFetcherV3 rawFetcher = GitHubFetcherV3.using(GitHubToken.getRealInstance())) {
      final RepositoryCoordinates coord =
          RepositoryCoordinates.from("this-user-does-not-exist-dfqfaglmkj45858", "repo");
      // final User user = fetcher.getUser(username);
      final Optional<JsonObject> opt = rawFetcher.fetchGitHubProject(coord);
      assertTrue(!opt.isPresent());
    }
  }

  @Test
  public void testRawFetchGitHubProject() throws Exception {
    try (GitHubFetcherV3 rawFetcher = GitHubFetcherV3.using(GitHubToken.getRealInstance())) {
      final RepositoryCoordinates coord =
          RepositoryCoordinates.from("oliviercailloux", "java-course");
      // final User user = fetcher.getUser(username);
      final Optional<JsonObject> opt = rawFetcher.fetchGitHubProject(coord);
      assertTrue(opt.isPresent());
    }
  }

  @Test
  public void testRawFetchLastModification() throws Exception {
    final RepositoryCoordinates coord = RepositoryCoordinates.from("oliviercailloux", "testrel");
    try (GitHubFetcherV3 rawFetcher = GitHubFetcherV3.using(GitHubToken.getRealInstance())) {
      final Instant lastModification =
          rawFetcher.getLastModification(coord, Paths.get("Test.html")).get();
      LOGGER.debug("Last: {}.", lastModification);
      assertEquals(Instant.parse("2020-02-17T19:37:26Z"), lastModification);
    }
  }

  @Test
  public void testGitHubHistoryProjets() throws Exception {
    final RepositoryCoordinates coord = RepositoryCoordinates.from("oliviercailloux", "projets");
    try (GitHubFetcherQL fetcher = GitHubFetcherQL.using(GitHubToken.getRealInstance())) {
      final GitHubHistory gHH = fetcher.getReversedGitHubHistory(coord);
      final ImmutableMap<ObjectId, Instant> pushDates = gHH.getPushDates();
      final GitHistory consistentHistory = gHH.getConsistentPushHistory();
      final ImmutableMap<ObjectId, Instant> compPushedDates = consistentHistory.getTimestamps();

      assertEquals(ImmutableSet.of(ObjectId.fromString("f96c728044e885fceaf4a3ae926f1a13dd329758")),
          consistentHistory.getRoots());
      assertTrue(gHH.getGraph().nodes().size() >= 164);
      assertEquals(gHH.getGraph().nodes(), compPushedDates.keySet());
      assertTrue(gHH.getPatchedPushCommits().nodes().isEmpty());

      assertEquals(Instant.parse("2019-05-24T15:24:11Z"),
          pushDates.get(ObjectId.fromString("81d36d64a5f6304d38b965897b9dc2ef3513a628")));
      assertEquals(Instant.parse("2019-05-24T15:24:11Z"),
          compPushedDates.get(ObjectId.fromString("81d36d64a5f6304d38b965897b9dc2ef3513a628")));
      assertEquals(Instant.parse("2019-05-06T14:57:15Z"),
          pushDates.get(ObjectId.fromString("dbd7a9439cc79e365dde71930634a9051c82d596")));
      assertEquals(Instant.parse("2019-05-06T14:57:15Z"),
          compPushedDates.get(ObjectId.fromString("dbd7a9439cc79e365dde71930634a9051c82d596")));
      assertEquals(null,
          pushDates.get(ObjectId.fromString("967489122c5d485bda8b571b2835dafa77af787f")));
      assertEquals(Instant.parse("2019-05-03T14:25:04Z"),
          compPushedDates.get(ObjectId.fromString("967489122c5d485bda8b571b2835dafa77af787f")));
      assertEquals(Instant.parse("2019-05-03T14:25:04Z"),
          pushDates.get(ObjectId.fromString("3cc31dd1d5a5210b269f0a01d26fd2a670bc4404")));
      assertEquals(Instant.parse("2019-05-03T14:25:04Z"),
          compPushedDates.get(ObjectId.fromString("3cc31dd1d5a5210b269f0a01d26fd2a670bc4404")));
    }
  }

  @Test
  void testGitHubHistoryFiltered() throws Exception {
    final RepositoryCoordinates coord = RepositoryCoordinates.from("oliviercailloux", "projets");
    try (GitHubFetcherQL fetcher = GitHubFetcherQL.using(GitHubToken.getRealInstance())) {
      final GitHubHistory gHH = fetcher.getReversedGitHubHistory(coord);
      final ImmutableMap<ObjectId, Instant> pushDates = gHH.getPushDates();
      final GitHistory consistentHistory = gHH.getConsistentPushHistory();
      final ImmutableMap<ObjectId, Instant> compPushedDates = consistentHistory.getTimestamps();

      final ObjectId c50 = ObjectId.fromString("50241f2a198f0eec686b19d235cd50c90614ac03");
      assertEquals(Instant.parse("2016-09-23T13:00:33Z"), pushDates.get(c50));
      final ObjectId cc2 = ObjectId.fromString("c2e245e7a7ca785fe8410213db89f142dda13bcf");
      assertEquals(Instant.parse("2016-09-23T13:01:18Z"), pushDates.get(cc2));
      final ObjectId cc6 = ObjectId.fromString("cc61d5dd68156cb5429da29c23729d169639a64d");
      assertEquals(Instant.parse("2016-09-23T13:02:36Z"), pushDates.get(cc6));

      final GitHistory filtered = consistentHistory
          .filter(o -> compPushedDates.get(o).isBefore(Instant.parse("2016-09-23T13:01:30Z")));
      assertTrue(gHH.getGraph().nodes().size() >= 164);
      assertEquals(16, filtered.getGraph().nodes().size());
      assertTrue(filtered.getGraph().nodes().contains(c50));
      assertTrue(filtered.getGraph().nodes().contains(cc2));
      assertFalse(filtered.getGraph().nodes().contains(cc6));
      assertEquals(16, filtered.getGraph().edges().size());
      assertEquals(ImmutableSet.of(c50), filtered.getGraph().predecessors(cc2));
      assertEquals(ImmutableSet.of(cc2), filtered.getGraph().successors(c50));
      assertEquals(ImmutableSet.of(), filtered.getGraph().successors(cc2));

      final GitHistory filteredAgain = filtered.filter(Predicates.alwaysTrue());
      assertEquals(filtered, filteredAgain);

      final GitHistory notReallyFiltered = consistentHistory.filter(Predicates.alwaysTrue());
      assertEquals(consistentHistory, notReallyFiltered);
    }
  }

  @Test
  public void testGitHubHistoryJBiblio() throws Exception {
    final RepositoryCoordinates coord = RepositoryCoordinates.from("oliviercailloux", "J-Biblio");
    try (GitHubFetcherQL fetcher = GitHubFetcherQL.using(GitHubToken.getRealInstance())) {
      final GitHubHistory gHH = fetcher.getReversedGitHubHistory(coord);
      final ImmutableMap<ObjectId, Instant> pushDates = gHH.getPushDates();
      final GitHistory consistentHistory = gHH.getConsistentPushHistory();
      final ImmutableMap<ObjectId, Instant> compPushedDates = consistentHistory.getTimestamps();

      assertEquals(ImmutableMap.of(), pushDates);
      assertEquals(ImmutableSet.of(Instant.MIN), ImmutableSet.copyOf(compPushedDates.values()));
      assertEquals(compPushedDates.keySet(), gHH.getGraph().nodes());
    }
  }

  @Test
  void testSearchForPrefixedRepositories() throws Exception {
    try (GitHubFetcherV3 rawFetcher = GitHubFetcherV3.using(GitHubToken.getRealInstance())) {
      final ImmutableList<RepositoryCoordinatesWithPrefix> depGit =
          rawFetcher.getRepositoriesWithPrefix("oliviercailloux", "jmcda");
      LOGGER.debug("Found: {}.", depGit);
      assertTrue(10 < depGit.size() && depGit.size() < 13, "" + depGit.size());
      final ImmutableList<RepositoryCoordinatesWithPrefix> noMatch =
          rawFetcher.getRepositoriesWithPrefix("oliviercailloux-org", "Invalid-prefix");
      assertEquals(ImmutableList.of(), noMatch);
    }
  }
}
