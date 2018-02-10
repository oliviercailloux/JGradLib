package io.github.oliviercailloux.st_projects;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Iterables;
import com.jcabi.github.Coordinates;
import com.jcabi.github.RtGithub;

import io.github.oliviercailloux.st_projects.model.Project;
import io.github.oliviercailloux.st_projects.model.ProjectOnGitHub;
import io.github.oliviercailloux.st_projects.services.git.Client;
import io.github.oliviercailloux.st_projects.services.git_hub.GitHubFetcher;
import io.github.oliviercailloux.st_projects.services.git_hub.RepositoryFinder;
import io.github.oliviercailloux.st_projects.services.read.FunctionalitiesReader;
import io.github.oliviercailloux.st_projects.services.read.IllegalFormat;
import io.github.oliviercailloux.st_projects.services.read.ProjectReader;
import io.github.oliviercailloux.st_projects.services.spreadsheet.SpreadsheetException;
import io.github.oliviercailloux.st_projects.services.spreadsheet.SpreadsheetWriter;
import io.github.oliviercailloux.st_projects.utils.Utils;

public class App {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(App.class);

	public static void main(String[] args) throws Exception {
		new App().proceed();
	}

	private final Map<Project, ProjectOnGitHub> ghProjects;

	private List<Project> projects;

	private Path projectsDir;

	private String suffix;

	private SpreadsheetWriter writer;

	public App() {
		writer = new SpreadsheetWriter();
		projects = null;
		ghProjects = new LinkedHashMap<>();
	}

	public void proceed() throws Exception {
		Utils.logLimits();
//		writeSEProjects();
		projectsDir = Paths.get("/home/olivier/Professions/Enseignement/Projets/EE");
		suffix = "EE";
		projects = new ProjectReader().asProjects(projectsDir);
		find();
		writeGHProjects();
		retrieveEE();
//		searchForGHProjectsFromLocal();
	}

	public void retrieveEE() throws Exception {
		projectsDir = Paths.get("/home/olivier/Professions/Enseignement/Projets/EE");
		projects = new ProjectReader().asProjects(projectsDir);
		find();

		for (ProjectOnGitHub ghProject : ghProjects.values()) {
			new Client().retrieve(ghProject);
		}
	}

	public void searchForGHProjectsFromLocal() throws IOException, IllegalFormat {
		LOGGER.info("Started.");
		projectsDir = Paths.get("/home/olivier/Professions/Enseignement/Projets/EE");
		projects = new ProjectReader().asProjects(projectsDir);
		final RepositoryFinder finder = new RepositoryFinder();
		finder.setGitHub(new RtGithub(Utils.getToken()));
//		finder.setFloorSearchDate(LocalDate.of(2017, Month.NOVEMBER, 5));
		for (Project project : projects) {
			final List<Coordinates> found = finder.find(project);
			LOGGER.info("Found for {}: {}.", project.getName(), found);
			final List<Coordinates> foundWithPom = finder.withPom();
			LOGGER.info("Found with POM for {}: {}.", project.getName(), foundWithPom);
		}
	}

	public void writeGHProjects() throws IOException, SpreadsheetException {
		LOGGER.info("Started write GH projects.");

		try (OutputStream out = new FileOutputStream("Deep-" + suffix + ".ods")) {
			writer.setWide(false);
			writer.setOutputStream(out);
			writer.write(projects, ghProjects);
		}
		try (OutputStream out = new FileOutputStream("Wide-" + suffix + ".ods")) {
			writer.setWide(true);
			writer.setOutputStream(out);
			writer.write(projects, ghProjects);
		}
	}

	public void writeSEProjects() throws IOException, IllegalFormat, SpreadsheetException {
		projectsDir = Paths.get("/home/olivier/Professions/Enseignement/Projets/SE");
		suffix = "SE";
		final ProjectReader projectReader = new ProjectReader();
		projectReader.setFunctionalitiesReader(new FunctionalitiesReader(Optional.of(BigDecimal.valueOf(1d))));
		projects = projectReader.asProjects(projectsDir);
		find();
		writeGHProjects();
	}

	private Map<Project, ProjectOnGitHub> find() throws IOException {
		final RepositoryFinder finder = new RepositoryFinder();
		final RtGithub gitHub = new RtGithub(Utils.getToken());
		finder.setGitHub(gitHub);
		final GitHubFetcher factory = GitHubFetcher.using(gitHub);
		for (Project project : projects) {
			LOGGER.info("Searching for {}.", project);
			final List<Coordinates> found = finder.find(project);
			LOGGER.info("Found: {}.", found);
			final List<Coordinates> foundWithPom = finder.withPom();
			final int nbMatches = foundWithPom.size();
			switch (nbMatches) {
			case 0:
				break;
			case 1:
				final Coordinates matching = Iterables.getOnlyElement(foundWithPom);
				ghProjects.put(project, factory.getProject(matching));
				break;
			default:
				throw new IllegalStateException(
						String.format("Found multiple matches for %s: %s.", project, foundWithPom));
			}
		}
		return ghProjects;
	}
}
