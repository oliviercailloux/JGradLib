package io.github.oliviercailloux.st_projects.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.jcabi.github.Coordinates;

import io.github.oliviercailloux.git_hub_gql.IssueSnapshotQL;
import io.github.oliviercailloux.git_hub_gql.User;
import io.github.oliviercailloux.st_projects.services.git_hub.GitHubFetcher;
import io.github.oliviercailloux.st_projects.utils.Utils;

public class TestGitHubIssue {

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(TestGitHubIssue.class);

	@Test
	public void testAssignees() throws Exception {
		final Coordinates.Simple coord = new Coordinates.Simple("badga", "Collaborative-exams");
		try (GitHubFetcher factory = GitHubFetcher.using(Utils.getToken())) {
			final RepositoryWithIssuesWithHistory repo = factory.getProject(coord).get();
			final IssueWithHistory issue = repo.getIssuesNamed("Servlets").iterator().next();
			assertEquals("Servlets", issue.getOriginalName());
			final Optional<IssueSnapshotQL> optDone = issue.getFirstSnapshotDone();
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
		try (GitHubFetcher factory = GitHubFetcher.using(Utils.getToken())) {
			final Coordinates.Simple coords = new Coordinates.Simple("benzait27", "Dauphine-Open-Data");
			final RepositoryWithIssuesWithHistory ghProject = factory.getProject(coords).get();
			final IssueWithHistory issue = ghProject.getIssuesOriginallyNamed("Course").iterator().next();
			assertEquals(1, issue.getBare().getNumber());
		}
	}

	@Test
	public void testHist() throws Exception {
		final Coordinates.Simple coord = new Coordinates.Simple("oliviercailloux", "testrel");
		try (GitHubFetcher factory = GitHubFetcher.using(Utils.getToken())) {
			final RepositoryWithIssuesWithHistory repo = factory.getProject(coord).get();
			final IssueWithHistory issue = repo.getIssuesNamed("test1").iterator().next();
			LOGGER.info("Issue: {}.", issue);
			assertEquals(Utils.newURL("https://github.com/oliviercailloux/testrel/issues/2"),
					issue.getBare().getHtmlURL());
			assertEquals("test1", issue.getOriginalName());
			assertEquals(2, issue.getBare().getNumber());

			final IssueSnapshotQL snapshot = issue.getSnapshots().get(1);

			assertEquals(LocalDateTime.of(2017, 10, 19, 14, 50, 22).toInstant(ZoneOffset.UTC), snapshot.getBirthTime());

			final IssueSnapshotQL done = issue.getFirstSnapshotDone().get();
			final Set<User> actual = done.getAssignees();
			assertEquals("bnegreve", Iterables.getOnlyElement(actual).getLogin());
		}
	}

	@Test
	public void testOpen() throws Exception {
		final Coordinates.Simple coord = new Coordinates.Simple("oliviercailloux", "testrel");
		try (GitHubFetcher factory = GitHubFetcher.using(Utils.getToken())) {
			final RepositoryWithIssuesWithHistory repo = factory.getProject(coord).get();
			final IssueWithHistory issue = repo.getIssuesNamed("test open").iterator().next();
			LOGGER.info("Issue: {}.", issue);
			assertEquals(Utils.newURL("https://github.com/oliviercailloux/testrel/issues/3"),
					issue.getBare().getHtmlURL());
			assertEquals("test open", issue.getOriginalName());
			assertEquals(3, issue.getBare().getNumber());
			final List<IssueSnapshotQL> snapshots = issue.getSnapshots();
			assertTrue(snapshots.get(0).isOpen());
			assertTrue(!snapshots.get(1).isOpen());
			assertTrue(snapshots.get(2).isOpen());
		}
	}

}
