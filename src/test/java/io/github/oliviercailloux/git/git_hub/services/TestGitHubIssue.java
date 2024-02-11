package io.github.oliviercailloux.git.git_hub.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import io.github.oliviercailloux.git.git_hub.model.GitHubToken;
import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinates;
import io.github.oliviercailloux.git.git_hub.model.graph_ql.IssueSnapshot;
import io.github.oliviercailloux.git.git_hub.model.graph_ql.IssueWithHistory;
import io.github.oliviercailloux.git.git_hub.model.graph_ql.RepositoryWithIssuesWithHistory;
import io.github.oliviercailloux.git.git_hub.model.graph_ql.User;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestGitHubIssue {

  @SuppressWarnings("unused")
  private static final Logger LOGGER = LoggerFactory.getLogger(TestGitHubIssue.class);

  @Test
  public void testAssignees() throws Exception {
    final RepositoryCoordinates coord = RepositoryCoordinates.from("badga", "Collaborative-exams");
    try (GitHubFetcherQL factory = GitHubFetcherQL.using(GitHubToken.getRealInstance())) {
      final RepositoryWithIssuesWithHistory repo = factory.getRepository(coord).get();
      final IssueWithHistory issue = repo.getIssuesNamed("Servlets").iterator().next();
      assertEquals("Servlets", issue.getOriginalName());
      final Optional<IssueSnapshot> optDone = issue.getFirstSnapshotDone();
      assertTrue(optDone.isPresent());
      final ImmutableSet<User> assignees = optDone.get().getAssignees();
      final Stream<String> loginsStream = assignees.stream().map((u) -> u.getLogin());
      final Set<String> logins = loginsStream.collect(Collectors.toSet());
      final ImmutableSet<String> expected = ImmutableSet.of("jeffazzam");
      assertEquals(expected, logins);
    }
  }

  @Test
  public void testDupl() throws Exception {
    try (GitHubFetcherQL factory = GitHubFetcherQL.using(GitHubToken.getRealInstance())) {
      final RepositoryCoordinates coords =
          RepositoryCoordinates.from("benzait27", "Dauphine-Open-Data");
      final RepositoryWithIssuesWithHistory ghProject = factory.getRepository(coords).get();
      final IssueWithHistory issue = ghProject.getIssuesOriginallyNamed("Course").iterator().next();
      assertEquals(1, issue.getBare().getNumber());
    }
  }

  @Test
  public void testHist() throws Exception {
    final RepositoryCoordinates coord = RepositoryCoordinates.from("oliviercailloux", "testrel");
    try (GitHubFetcherQL factory = GitHubFetcherQL.using(GitHubToken.getRealInstance())) {
      final RepositoryWithIssuesWithHistory repo = factory.getRepository(coord).get();
      final IssueWithHistory issue = repo.getIssuesNamed("test1").iterator().next();
      LOGGER.info("Issue: {}.", issue);
      assertEquals(URI.create("https://github.com/oliviercailloux/testrel/issues/2"),
          issue.getBare().getHtmlURI());
      assertEquals("test1", issue.getOriginalName());
      assertEquals(2, issue.getBare().getNumber());

      final IssueSnapshot snapshot = issue.getSnapshots().get(1);

      assertEquals(LocalDateTime.of(2017, 10, 19, 14, 50, 22).toInstant(ZoneOffset.UTC),
          snapshot.getBirthTime());

      final IssueSnapshot done = issue.getFirstSnapshotDone().get();
      final Set<User> actual = done.getAssignees();
      assertEquals("bnegreve", Iterables.getOnlyElement(actual).getLogin());
    }
  }

  @Test
  public void testOpen() throws Exception {
    final RepositoryCoordinates coord = RepositoryCoordinates.from("oliviercailloux", "testrel");
    try (GitHubFetcherQL factory = GitHubFetcherQL.using(GitHubToken.getRealInstance())) {
      final RepositoryWithIssuesWithHistory repo = factory.getRepository(coord).get();
      final IssueWithHistory issue = repo.getIssuesNamed("test open").iterator().next();
      LOGGER.info("Issue: {}.", issue);
      assertEquals(URI.create("https://github.com/oliviercailloux/testrel/issues/3"),
          issue.getBare().getHtmlURI());
      assertEquals("test open", issue.getOriginalName());
      assertEquals(3, issue.getBare().getNumber());
      final List<IssueSnapshot> snapshots = issue.getSnapshots();
      assertTrue(snapshots.get(0).isOpen());
      assertTrue(!snapshots.get(1).isOpen());
      assertTrue(snapshots.get(2).isOpen());
    }
  }
}
