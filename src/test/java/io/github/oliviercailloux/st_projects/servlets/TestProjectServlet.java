package io.github.oliviercailloux.st_projects.servlets;

import javax.json.JsonArray;
import javax.json.JsonObject;

import org.asciidoctor.Asciidoctor;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinates;
import io.github.oliviercailloux.git.git_hub.model.graph_ql.RepositoryWithIssuesWithHistory;
import io.github.oliviercailloux.git.git_hub.services.GitHubFetcherQL;
import io.github.oliviercailloux.git.git_hub.utils.JsonUtils;
import io.github.oliviercailloux.git.git_hub.utils.Utils;
import io.github.oliviercailloux.st_projects.model.Project;

public class TestProjectServlet {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(TestProjectServlet.class);

	@Test
	public void testMix() throws Exception {
		final Project myProject = Project.from("java-course");
		final RepositoryWithIssuesWithHistory repository;
		try (GitHubFetcherQL fetcher = GitHubFetcherQL.using(Utils.getToken())) {
			repository = fetcher.getRepository(RepositoryCoordinates.from("oliviercailloux", "java-course")).get();
		}
		final ProjectServlet srv = ProjectServlet.using(Asciidoctor.Factory.create(), Utils.getToken());
		final JsonObject iss = srv.toJsonSummary(myProject, repository.getIssues().get(0));
		LOGGER.debug("Summ: {}.", iss);
		final JsonObject re = srv.toJsonSummary(myProject, repository);
		LOGGER.debug("Summ: {}.", re);
		srv.update();
		srv.getProjectsMonitor().await();
		final JsonArray repos = srv.getRepositories("XM-GUI");
		LOGGER.info("Repos: {}.", JsonUtils.asPrettyString(repos));
	}
}
