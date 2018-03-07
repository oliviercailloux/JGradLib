package io.github.oliviercailloux.st_projects.model;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jcabi.github.Github;
import com.jcabi.github.RtGithub;

import io.github.oliviercailloux.git_hub.low.User;
import io.github.oliviercailloux.st_projects.services.git_hub.GitHubFetcher;
import io.github.oliviercailloux.st_projects.utils.Utils;

public class TestGitHubUser {

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(TestGitHubUser.class);

	@Test
	public void test() throws Exception {
		Utils.logLimits();
		final Github gitHub = new RtGithub(Utils.getToken());
		final User user = GitHubFetcher.using(gitHub).getUser("oliviercailloux");
		assertEquals(Utils.newURL("https://api.github.com/users/oliviercailloux"), user.getApiURL());
		assertEquals(Utils.newURL("https://github.com/oliviercailloux"), user.getHtmlURL());
		assertEquals("oliviercailloux", user.getLogin());
	}

}
