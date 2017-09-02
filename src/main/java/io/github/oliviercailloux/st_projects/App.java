package io.github.oliviercailloux.st_projects;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Paths;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.oliviercailloux.st_projects.model.GitHubProject;
import io.github.oliviercailloux.st_projects.model.Project;
import io.github.oliviercailloux.st_projects.services.git_hub.RepositoryFinder;
import io.github.oliviercailloux.st_projects.services.read.IllegalFormat;
import io.github.oliviercailloux.st_projects.services.read.ProjectReader;
import io.github.oliviercailloux.st_projects.services.spreadsheet.SpreadsheetException;
import io.github.oliviercailloux.st_projects.services.spreadsheet.SpreadsheetWriter;

public class App {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(App.class);

	public static void main(String[] args) throws Exception {
		new App().proceed();
	}

	public void proceed() throws Exception {
		writeLocalProjects();
	}

	public void searchForGHProjectsFromLocal() throws IOException, IllegalFormat {
		LOGGER.info("Started.");
		final List<Project> projects = new ProjectReader()
				.asProjects(Paths.get("/home/olivier/Professions/Enseignement/projets/EE"));
		final RepositoryFinder finder = new RepositoryFinder();
		for (Project project : projects) {
			final List<GitHubProject> found = finder.find(project);
			LOGGER.info("Found for {}: {}.", project, found);
		}
	}

	public void writeLocalProjects() throws IOException, IllegalFormat, SpreadsheetException {
		LOGGER.info("Started write projects.");
		final List<Project> projects = new ProjectReader()
				.asProjects(Paths.get("/home/olivier/Professions/Enseignement/projets/EE"));
		try (OutputStream out = new FileOutputStream("out.ods")) {
			final SpreadsheetWriter writer = new SpreadsheetWriter();
			writer.setOutputStream(out);
			writer.writeProjects(projects);
		}
	}
}
