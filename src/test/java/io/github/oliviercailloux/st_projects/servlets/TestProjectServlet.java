package io.github.oliviercailloux.st_projects.servlets;

import javax.json.JsonArray;
import javax.json.JsonObject;

import org.asciidoctor.Asciidoctor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinates;
import io.github.oliviercailloux.git.git_hub.model.graph_ql.RepositoryWithIssuesWithHistory;
import io.github.oliviercailloux.git.git_hub.services.GitHubFetcherQL;
import io.github.oliviercailloux.git.git_hub.utils.JsonUtils;
import io.github.oliviercailloux.st_projects.model.Project;

public class TestProjectServlet {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(TestProjectServlet.class);

//	@Test
	public void testMix() throws Exception {
		final Project myProject = Project.from("java-course");
		final RepositoryWithIssuesWithHistory repository;
		try (GitHubFetcherQL fetcher = GitHubFetcherQL
				.using(io.github.oliviercailloux.git.git_hub.model.GitHubToken.getRealInstance())) {
			repository = fetcher.getRepository(RepositoryCoordinates.from("oliviercailloux", "java-course")).get();
		}
		final ProjectServlet srv = ProjectServlet.using(Asciidoctor.Factory.create(),
				io.github.oliviercailloux.git.git_hub.model.GitHubToken.getRealInstance());
		final JsonObject iss = srv.toJsonSummary(myProject, repository.getIssues().get(0));
		LOGGER.info("Summ: {}.", iss);
		final JsonObject re = srv.toJsonSummary(myProject, repository);
		LOGGER.info("Summ: {}.", re);
		srv.update();
		srv.getProjectsMonitor().await();
		final JsonArray repos = srv.getRepositories("XM-GUI");
		LOGGER.info("Repos: {}.", JsonUtils.asPrettyString(repos));
	}
}
