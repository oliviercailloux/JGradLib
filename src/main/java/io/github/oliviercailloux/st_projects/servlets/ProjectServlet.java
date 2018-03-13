package io.github.oliviercailloux.st_projects.servlets;

import java.util.Optional;
import java.util.stream.Collectors;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import javax.json.bind.JsonbConfig;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.oliviercailloux.st_projects.model.Project;
import io.github.oliviercailloux.st_projects.services.git_hub.ProjectsMonitor;
import io.github.oliviercailloux.st_projects.services.read.IllegalFormat;

@Path("project")
@RequestScoped
public class ProjectServlet {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(ProjectServlet.class);

	@Inject
	private ProjectsMonitor monitor;

	@Path("list")
	@GET
	@Produces(MediaType.TEXT_PLAIN)
	public String getSEList() {
		LOGGER.info("Test log list.");
		return monitor.getSEProjects().stream().map(Project::getName).collect(Collectors.joining("\n"));
	}

	@Path("get/{project}")
	@GET
	@Produces(MediaType.TEXT_PLAIN)
	public String getSEProject(@PathParam("project") String projectName) {
		final Optional<Project> project = monitor.getSEProject(projectName);
		if (!project.isPresent()) {
			return null;
		}
		try (Jsonb jsonb = JsonbBuilder.create(new JsonbConfig().withFormatting(true))) {
			return jsonb.toJson(project.get());
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	@Path("update")
	@GET
	public void update() throws IllegalFormat {
		LOGGER.info("Test log.");
		monitor.update();
	}
}
