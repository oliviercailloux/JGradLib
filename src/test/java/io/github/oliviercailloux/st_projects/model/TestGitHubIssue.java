package io.github.oliviercailloux.st_projects.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Set;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSet;
import com.jcabi.github.Coordinates;
import com.jcabi.github.Github;
import com.jcabi.github.Issue;
import com.jcabi.github.Issues;
import com.jcabi.github.Repo;
import com.jcabi.github.RtGithub;

import io.github.oliviercailloux.st_projects.utils.Utils;

public class TestGitHubIssue {

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(TestGitHubIssue.class);

	@Test
	public void testDupl() throws Exception {
		final Github gitHub = new RtGithub(Utils.getToken());
		final Repo repo = gitHub.repos().get(new Coordinates.Simple("benzait27", "Dauphine-Open-Data"));
		final ProjectOnGitHub p = new ProjectOnGitHub(new Project("Dauphine-Open-Data"), repo);
		p.init();
		p.initAllIssuesAndEvents();
		final GitHubIssue issue = p.getIssue("Course").get();
		assertEquals(1, issue.getNumber());
	}

	@Test
	public void testHist() throws Exception {
		final Github gitHub = new RtGithub(Utils.getToken());
		final Repo repo = gitHub.repos().get(new Coordinates.Simple("oliviercailloux", "testrel"));
		final Issues issues = repo.issues();
		final Issue issueJcabi = issues.get(2);
		final GitHubIssue issue = new GitHubIssue(issueJcabi);
		issue.init();
		issue.initAllEvents();
		LOGGER.info("Issue: {}.", issue);
		assertEquals(Utils.newURL("https://github.com/oliviercailloux/testrel/issues/2"), issue.getHtmlURL());
		assertEquals("test1", issue.getTitle());
		assertEquals(2, issue.getNumber());
		assertEquals(Utils.newURL("https://api.github.com/repos/oliviercailloux/testrel"), issue.getRepoURL());
		assertEquals(Utils.newURL("https://api.github.com/repos/oliviercailloux/testrel/issues/2"), issue.getApiURL());

		final GitHubUser userC = new GitHubUser(gitHub.users().get("oliviercailloux"));
		final GitHubUser userN = new GitHubUser(gitHub.users().get("bnegreve"));
		final GitHubEvent event = issue.getFirstEventDone().get();
		final Set<GitHubUser> actual = event.getAssignees().get();
		assertEquals(ImmutableSet.of(userC, userN), actual);
	}

	@Test
	public void testOpen() throws Exception {
		final Github gitHub = new RtGithub();
		final Repo repo = gitHub.repos().get(new Coordinates.Simple("oliviercailloux", "testrel"));
		final Issues issues = repo.issues();
		final Issue issueJcabi = issues.get(3);
		final GitHubIssue issue = new GitHubIssue(issueJcabi);
		issue.init();
		issue.initAllEvents();
		LOGGER.info("Issue: {}.", issue);
		assertEquals(Utils.newURL("https://github.com/oliviercailloux/testrel/issues/3"), issue.getHtmlURL());
		assertEquals("test open", issue.getTitle());
		assertEquals(3, issue.getNumber());
		assertEquals(Utils.newURL("https://api.github.com/repos/oliviercailloux/testrel"), issue.getRepoURL());
		assertEquals(Utils.newURL("https://api.github.com/repos/oliviercailloux/testrel/issues/3"), issue.getApiURL());
		assertTrue(issue.hasBeenClosed());
	}

}
