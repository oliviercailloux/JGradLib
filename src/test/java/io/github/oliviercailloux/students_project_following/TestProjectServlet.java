package io.github.oliviercailloux.students_project_following;

import javax.json.JsonArray;
import javax.json.JsonObject;

import org.asciidoctor.Asciidoctor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinates;
import io.github.oliviercailloux.git.git_hub.model.graph_ql.RepositoryWithIssuesWithHistory;
import io.github.oliviercailloux.git.git_hub.services.GitHubFetcherQL;
import io.github.oliviercailloux.json.PrintableJsonValueFactory;
import io.github.oliviercailloux.students_project_following.Project;
import io.github.oliviercailloux.students_project_following.ProjectServlet;

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
		LOGGER.info("Repos: {}.", PrintableJsonValueFactory.wrapValue(repos));
	}
}