package io.github.oliviercailloux.st_projects;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;
import com.jcabi.github.RtGithub;

import io.github.oliviercailloux.st_projects.model.Project;
import io.github.oliviercailloux.st_projects.model.ProjectOnGitHub;
import io.github.oliviercailloux.st_projects.model.ProjectWithPossibleGitHubData;
import io.github.oliviercailloux.st_projects.services.git.Client;
import io.github.oliviercailloux.st_projects.services.git_hub.RepositoryFinder;
import io.github.oliviercailloux.st_projects.services.read.FunctionalitiesReader;
import io.github.oliviercailloux.st_projects.services.read.IllegalFormat;
import io.github.oliviercailloux.st_projects.services.read.ProjectReader;
import io.github.oliviercailloux.st_projects.services.spreadsheet.SpreadsheetException;
import io.github.oliviercailloux.st_projects.services.spreadsheet.SpreadsheetWriter;
import io.github.oliviercailloux.st_projects.utils.Utils;
import jersey.repackaged.com.google.common.collect.Iterables;

public class App {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(App.class);

	public static void main(String[] args) throws Exception {
		new App().proceed();
	}

	private List<Project> origProjects;

	private final List<ProjectWithPossibleGitHubData> projects;

	private Path projectsDir;

	private String suffix;

	private SpreadsheetWriter writer;

	public App() {
		writer = new SpreadsheetWriter();
		projects = Lists.newArrayList();
	}

	public void proceed() throws Exception {
		Utils.logLimits();
//		writeSEProjects();
		projectsDir = Paths.get("/home/olivier/Professions/Enseignement/Projets/EE");
		suffix = "EE";
		origProjects = new ProjectReader().asProjects(projectsDir);
		find();
		initAll();
		writeGHProjects();
		retrieveEE();
//		searchForGHProjectsFromLocal();
	}

	public void retrieveEE() throws Exception {
		projectsDir = Paths.get("/home/olivier/Professions/Enseignement/Projets/EE");
		origProjects = new ProjectReader().asProjects(projectsDir);
		find();

		for (ProjectWithPossibleGitHubData ghProject : projects) {
			if (ghProject.getGhProject().isPresent()) {
				new Client().retrieve(ghProject.getGhProject().get());
			}
		}
	}

	public void searchForGHProjectsFromLocal() throws IOException, IllegalFormat {
		LOGGER.info("Started.");
		projectsDir = Paths.get("/home/olivier/Professions/Enseignement/Projets/EE");
		origProjects = new ProjectReader().asProjects(projectsDir);
		final RepositoryFinder finder = new RepositoryFinder();
		finder.setGitHub(new RtGithub(Utils.getToken()));
//		finder.setFloorSearchDate(LocalDate.of(2017, Month.NOVEMBER, 5));
		for (Project project : origProjects) {
			final List<ProjectOnGitHub> found = finder.find(project);
			LOGGER.info("Found for {}: {}.", project.getName(), found);
			final List<ProjectOnGitHub> foundWithPom = finder.withPom();
			LOGGER.info("Found with POM for {}: {}.", project.getName(), foundWithPom);
		}
	}

	public void writeGHProjects() throws IOException, SpreadsheetException {
		LOGGER.info("Started write GH projects.");

		try (OutputStream out = new FileOutputStream("Deep-" + suffix + ".ods")) {
			writer.setWide(false);
			writer.setOutputStream(out);
			writer.writeGeneral(projects);
		}
		try (OutputStream out = new FileOutputStream("Wide-" + suffix + ".ods")) {
			writer.setWide(true);
			writer.setOutputStream(out);
			writer.writeGeneral(projects);
		}
	}

	public void writeSEProjects() throws IOException, IllegalFormat, SpreadsheetException {
		projectsDir = Paths.get("/home/olivier/Professions/Enseignement/Projets/SE");
		suffix = "SE";
		final ProjectReader projectReader = new ProjectReader();
		projectReader.setFunctionalitiesReader(new FunctionalitiesReader(Optional.of(BigDecimal.valueOf(1d))));
		origProjects = projectReader.asProjects(projectsDir);
		find();
		initAll();
		writeGHProjects();
	}

	private List<ProjectWithPossibleGitHubData> find() throws IOException {
		final RepositoryFinder finder = new RepositoryFinder();
		finder.setGitHub(new RtGithub(Utils.getToken()));
		for (Project project : origProjects) {
			LOGGER.info("Searching for {}.", project);
			final List<ProjectOnGitHub> found = finder.find(project);
			LOGGER.info("Found: {}.", found);
			final List<ProjectOnGitHub> foundWithPom = finder.withPom();
			final int nbMatches = foundWithPom.size();
			switch (nbMatches) {
			case 0:
				projects.add(new ProjectWithPossibleGitHubData(project));
				break;
			case 1:
				final ProjectOnGitHub matching = Iterables.getOnlyElement(foundWithPom);
				matching.init();
				projects.add(new ProjectWithPossibleGitHubData(matching));
				break;
			default:
				throw new IllegalStateException("Found multiple matches for " + project + ".");
			}
		}
		return projects;
	}

	private List<ProjectWithPossibleGitHubData> initAll() throws IOException {
		for (ProjectWithPossibleGitHubData projectWithPossibleGitHubData : projects) {
			if (projectWithPossibleGitHubData.getGhProject().isPresent()) {
				projectWithPossibleGitHubData.getGhProject().get().initAllIssuesAndEvents();
			}
		}
		return projects;
	}
}
