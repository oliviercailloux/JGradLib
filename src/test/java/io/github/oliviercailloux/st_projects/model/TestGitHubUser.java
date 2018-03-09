package io.github.oliviercailloux.st_projects.model;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.oliviercailloux.git_hub.low.User;
import io.github.oliviercailloux.st_projects.services.git_hub.GitHubFetcher;
import io.github.oliviercailloux.st_projects.utils.Utils;

public class TestGitHubUser {

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(TestGitHubUser.class);

	@Test
	public void test() throws Exception {
		Utils.logLimits();
		final User user;
		try (GitHubFetcher factory = GitHubFetcher.using(Utils.getToken())) {
			user = factory.getUser("oliviercailloux");
		}
		assertEquals(Utils.newURL("https://api.github.com/users/oliviercailloux"), user.getApiURL());
		assertEquals(Utils.newURL("https://github.com/oliviercailloux"), user.getHtmlURL());
		assertEquals("oliviercailloux", user.getLogin());
	}

}
