package io.github.oliviercailloux.st_projects.servlets;

import java.util.Optional;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import io.github.oliviercailloux.st_projects.model.Project;
import io.github.oliviercailloux.st_projects.services.git_hub.WordingMonitor;

@Path("wording")
public class Wording {
	@Inject
	private WordingMonitor monitor;

	@Path("get/{project}")
	@GET
	@Produces(MediaType.TEXT_PLAIN)
	public String getWording(@PathParam("project") String projectName) {
		final Optional<Project> project = monitor.getProject(projectName);
		if (!project.isPresent()) {
			return null;
		}
		return project.get().asJsonPretty();
	}

	@Path("update")
	@POST
	public void update() {
		// todo
	}
}
