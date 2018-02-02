package io.github.oliviercailloux.st_projects.services.git_hub;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.json.JsonArray;

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

import io.github.oliviercailloux.st_projects.model.GitHubEvent;
import io.github.oliviercailloux.st_projects.model.GitHubIssue;
import io.github.oliviercailloux.st_projects.model.GitHubUser;
import io.github.oliviercailloux.st_projects.model.Project;
import io.github.oliviercailloux.st_projects.services.read.IllegalFormat;
import io.github.oliviercailloux.st_projects.utils.Utils;

public class TestFetch {

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(TestFetch.class);

	@Test
	public void testAssignees() throws Exception {
		final Github github = new RtGithub(Utils.getToken());
		final Repo repo = github.repos().get(new Coordinates.Simple("badga", "Collaborative-exams"));
		final GitHubIssue issue = new GitHubIssue(repo.issues().get(3));
		issue.initAllEvents();
		final Optional<GitHubEvent> optDone = issue.getFirstEventDone();
		assertTrue(optDone.isPresent());
		final Optional<Set<GitHubUser>> assigneesOpt = optDone.get().getAssignees();
		assertTrue(assigneesOpt.isPresent());
		final Set<GitHubUser> assignees = assigneesOpt.get();
		for (GitHubUser gitHubUser : assignees) {
			gitHubUser.init();
		}
		final Stream<String> loginsStream = assignees.stream().map((u) -> u.getLogin());
		final Set<String> logins = loginsStream.collect(Collectors.toSet());
		final ImmutableSet<String> expected = ImmutableSet.of("jeffazzam", "laminetamendjari");
		assertEquals(expected, logins);
	}

	@Test
	public void testFetchAssignees() throws Exception {
		final Github github = new RtGithub(Utils.getToken());
		final Repo repo = github.repos().get(new Coordinates.Simple("waffleio", "waffle.io"));
		final Issues issues = repo.issues();
		final Issue issue = issues.get(3259);
		final JsonArray jsonArray = issue.json().getJsonArray("assignees");
		LOGGER.info("Issue assignees: {}.", jsonArray);
		assertEquals(2, jsonArray.size());
		/** https://github.com/waffleio/waffle.io/issues/3259 */
	}

	@Test
	public void testFetchProjects() throws IllegalFormat, IOException {
		try (Fetch fetch = new Fetch()) {
//		fetch.fetchReadme();
			final List<Project> projects = fetch.fetchProjects();
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
