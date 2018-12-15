package io.github.oliviercailloux.st_projects.servlets;

import static java.util.Objects.requireNonNull;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonBuilderFactory;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.asciidoctor.Asciidoctor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Predicates;

import io.github.oliviercailloux.git.git_hub.model.GitHubRealToken;
import io.github.oliviercailloux.git.git_hub.model.graph_ql.IssueWithHistory;
import io.github.oliviercailloux.git.git_hub.model.graph_ql.RepositoryWithIssuesWithHistory;
import io.github.oliviercailloux.git.git_hub.utils.JsonUtils;
import io.github.oliviercailloux.st_projects.model.Functionality;
import io.github.oliviercailloux.st_projects.model.Project;
import io.github.oliviercailloux.st_projects.services.ProjectsMonitor;
import io.github.oliviercailloux.st_projects.services.read.IllegalFormat;

@Path("project")
@RequestScoped
public class ProjectServlet {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(ProjectServlet.class);

	static public ProjectServlet using(Asciidoctor asciidoctor, GitHubRealToken token) {
		final ProjectServlet srv = new ProjectServlet();
		srv.setMonitor(ProjectsMonitor.using(asciidoctor, token));
		return srv;
	}

	private final JsonBuilderFactory factory;

	@Inject
	private ProjectsMonitor monitor;

	public ProjectServlet() {
		factory = Json.createBuilderFactory(null);
	}

	@Path("list")
	@GET
	@Produces(MediaType.TEXT_PLAIN + "; charset=UTF-8")
	public String getList() {
		LOGGER.info("Let’s show the list.");
		return monitor.getProjects().stream().map(Project::getName).collect(Collectors.joining("\n"));
	}

	@Path("get-raw/{project}")
	@GET
	@Produces(MediaType.APPLICATION_JSON + "; charset=UTF-8")
	public Project getProjectRaw(@PathParam("project") String projectName) {
		final Optional<Project> projectOpt = monitor.getProject(projectName);
		if (!projectOpt.isPresent()) {
			throw new WebApplicationException(Response.Status.NOT_FOUND);
		}
		return projectOpt.get();
	}

	@Path("get/{project}")
	@GET
	@Produces(MediaType.APPLICATION_JSON + "; charset=UTF-8")
	public JsonArray getRepositories(@PathParam("project") String projectName) {
		final Project project = getProjectRaw(projectName);
		final List<RepositoryWithIssuesWithHistory> repositories = monitor.getRepositories(project);
		final Stream<JsonObject> jsons = repositories.stream().map((r) -> toJsonSummary(project, r));
		return JsonUtils.asArray(jsons, factory);
	}

	@Path("list-se")
	@GET
	@Produces(MediaType.TEXT_PLAIN + "; charset=UTF-8")
	public String getSEList() {
		LOGGER.info("Let’s show the list.");
		return monitor.getSEProjects().stream().map(Project::getName).collect(Collectors.joining("\n"));
	}

	public JsonObject toJsonSummary(Project project, IssueWithHistory issue) {
		final JsonObjectBuilder o = factory.createObjectBuilder();
		o.add("originalName", issue.getOriginalName());
		o.add("url", issue.getBare().getHtmlURI().toString());
		final boolean corrToFct = project.getFunctionalities().stream().map(Functionality::getName)
				.anyMatch(Predicates.equalTo(issue.getOriginalName()));
		o.add("functionalityDone", issue.getFirstSnapshotDone().isPresent() && corrToFct);
		return o.build();
	}

	public JsonObject toJsonSummary(Project project, RepositoryWithIssuesWithHistory repository) {
		final JsonObjectBuilder o = factory.createObjectBuilder();
		LOGGER.info("Generating repo.");
		o.add("repository", repository.getBare().toJsonSummary());
		LOGGER.info("Generated repo.");
		final Stream<JsonObject> stream = repository.getIssues().stream().map((i) -> toJsonSummary(project, i));
		LOGGER.info("Generated stream.");
		final JsonArray array = JsonUtils.asArray(stream, factory);
		LOGGER.info("Generated array.");
		o.add("issues", array);
		return o.build();
	}

	@Path("update")
	@POST
	public void update() throws IllegalFormat {
		LOGGER.info("Let’s update.");
		monitor.updateProjectsAsync();
		monitor.updateRepositoriesAsync();
		LOGGER.info("Update launched.");
	}

	ProjectsMonitor getProjectsMonitor() {
		return monitor;
	}

	void setMonitor(ProjectsMonitor monitor) {
		this.monitor = requireNonNull(monitor);
	}
}
