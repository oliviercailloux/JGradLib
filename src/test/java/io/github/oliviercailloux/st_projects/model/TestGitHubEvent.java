package io.github.oliviercailloux.st_projects.model;

import static org.junit.Assert.assertEquals;

import java.time.LocalDateTime;

import org.junit.Test;
import org.mockito.internal.util.collections.Iterables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jcabi.github.Coordinates;
import com.jcabi.github.Event;
import com.jcabi.github.Github;
import com.jcabi.github.Issue;
import com.jcabi.github.Issues;
import com.jcabi.github.Repo;
import com.jcabi.github.RtGithub;

import io.github.oliviercailloux.st_projects.utils.Utils;

public class TestGitHubEvent {

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(TestGitHubEvent.class);

	@Test
	public void test() throws Exception {
		Utils.logLimits();
		final Github gitHub = new RtGithub();
		final Repo repo = gitHub.repos().get(new Coordinates.Simple("oliviercailloux", "testrel"));
		final Issues issues = repo.issues();
		final Issue issue = issues.get(2);
		final Event eventJcabi = Iterables.firstOf(issue.events());
		final GitHubEvent event = new GitHubEvent(eventJcabi);
		event.init();
		assertEquals(Utils.newURL("https://api.github.com/repos/oliviercailloux/testrel/issues/events/1301249271"),
				event.getApiURL());
		assertEquals(LocalDateTime.of(2017, 10, 19, 14, 50, 22), event.getCreatedAt());
		assertEquals(1301249271, event.getId());
		assertEquals(Event.ASSIGNED, event.getType());
	}

}
