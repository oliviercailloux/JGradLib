package io.github.oliviercailloux.st_projects;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jcabi.github.Coordinates;
import com.jcabi.github.Github;
import com.jcabi.github.Issue;
import com.jcabi.github.Issues;
import com.jcabi.github.Limits;
import com.jcabi.github.Repo;
import com.jcabi.github.RtGithub;

import io.github.oliviercailloux.st_projects.model.GitHubProject;
import io.github.oliviercailloux.st_projects.model.Project;
import io.github.oliviercailloux.st_projects.services.git.Client;
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
		final Github github = new RtGithub();
		final BigInteger reset = github.limits().get(Limits.CORE).json().getJsonNumber("reset").bigIntegerValue();
		LOGGER.info("Reset: {}.", reset);
		LOGGER.info("Reset time: {}.", Instant.ofEpochSecond(reset.longValue()).atZone(ZoneId.systemDefault()));
		final Repo repo = github.repos().get(new Coordinates.Simple("oliviercailloux", "testrel"));
		final Issues issues = repo.issues();
		final Issue issue = issues.get(2);

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

	public void tryCheckout() throws IOException, GitAPIException {
		new Client().checkout("f58a9b8fee746d9116ae5d87df2f05a4ab90da5a");
	}

	public void writeLocalProjects() throws IOException, IllegalFormat, SpreadsheetException {
		LOGGER.info("Started write projects.");
		final List<Project> projects = new ProjectReader()
				.asProjects(Paths.get("/home/olivier/Professions/Enseignement/Projets/EE"));
		try (OutputStream out = new FileOutputStream("out.ods")) {
			final SpreadsheetWriter writer = new SpreadsheetWriter();
			writer.setOutputStream(out);
			writer.writeProjects(projects);
		}
	}
}
