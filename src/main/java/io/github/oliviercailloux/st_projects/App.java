package io.github.oliviercailloux.st_projects;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonReader;
import javax.json.JsonValue;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Iterables;

import io.github.oliviercailloux.git_hub.RepositoryCoordinates;
import io.github.oliviercailloux.git_hub.graph_ql.Repository;
import io.github.oliviercailloux.git_hub.low.CommitGitHubDescription;
import io.github.oliviercailloux.git_hub.low.SearchResult;
import io.github.oliviercailloux.st_projects.model.Project;
import io.github.oliviercailloux.st_projects.model.RepositoryWithIssuesWithHistory;
import io.github.oliviercailloux.st_projects.services.git.Client;
import io.github.oliviercailloux.st_projects.services.git_hub.GitHubFetcher;
import io.github.oliviercailloux.st_projects.services.git_hub.RawGitHubFetcher;
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
		final App app = new App();
//		app.proceed();
//		final RawGitHubGraphQLFetcher fetcher = new RawGitHubGraphQLFetcher();
//		fetcher.setToken(Utils.getToken());
//		fetcher.log();
//		final Pattern javadocRegex = Pattern.compile("^[ \\v\\h]*/\\*\\*.*", Pattern.DOTALL);
//		System.out.println(javadocRegex.matcher("	/** \n").find());
		final OutputStream out = Files.newOutputStream(Paths.get("out.txt"));
		try (Writer output = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
			app.reportL3Works(output);
		}
	}

	private final Map<Project, RepositoryWithIssuesWithHistory> ghProjects;

	private List<Project> projects;

	private Path projectsDir;

	private String suffix;

	private SpreadsheetWriter writer;

	public App() {
		writer = new SpreadsheetWriter();
		projects = null;
		ghProjects = new LinkedHashMap<>();
		// TODO restore fct ignore after. Update client: don’t need history.
	}

	public Map<Integer, String> getIdsToUsernames() throws IOException {
		final Path inp = Paths.get("usernames.json");
		final JsonArray json;
		try (JsonReader jr = Json.createReader(Files.newInputStream(inp))) {
			json = jr.readArray();
		}
		final Map<Integer, String> idsToUsernames = json.stream().sequential().map(JsonValue::asJsonObject)
				.filter((o) -> !o.isNull("username"))
				.collect(Utils.toLinkedMap((o) -> o.getInt("studentId"), (o) -> o.getString("username")));
		LOGGER.info("Got: {}.", idsToUsernames);
		return idsToUsernames;
	}

	public void proceed() throws IOException, IllegalFormat, SpreadsheetException, GitAPIException {
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

	public void reportL3Works(Writer output) throws IOException {
		final Pattern packageRegex = Pattern.compile("^[ \\h]*package .*", Pattern.DOTALL | Pattern.MULTILINE);
		final Pattern javadocRegex = Pattern.compile("^[ \\v\\h]*/\\*\\*.*", Pattern.DOTALL | Pattern.MULTILINE);
		try (RawGitHubFetcher rawFetcher = new RawGitHubFetcher();
				GitHubFetcher fetcher = GitHubFetcher.using(Utils.getToken())) {
			rawFetcher.setToken(Utils.getToken());

			final Map<Integer, String> idsToUsernames = getIdsToUsernames();

			final String repo = "Java-L3-Eck-Ex";
			final Instant deadline = Instant.parse("2018-03-04T00:00:00Z");
			final ImmutableSortedMap<Integer, String> idsSorted = ImmutableSortedMap.copyOf(idsToUsernames,
					Comparator.naturalOrder());
			for (Integer id : idsSorted.keySet()) {
				final String username = idsSorted.get(id);
				final RepositoryCoordinates coord = RepositoryCoordinates.from(username, repo);
				output.write("= Id: " + id + " (" + coord.getOwner() + ")\n");
				final URL base = Utils.newURL("https://github.com/");
				final URL userPath = new URL(base, coord.getOwner());
				final Optional<RepositoryWithIssuesWithHistory> prjOpt = fetcher.getProject(coord);
				if (!prjOpt.isPresent()) {
					output.write(userPath + "\n");
					output.write("Project not found.\n");
					continue;
				}
				final URL repoUrl = prjOpt.get().getBare().getHtmlURL();
				output.write(repoUrl + "\n");
				final Stream<Path> paths = rawFetcher.searchForCode(coord, "class", "java").stream()
						.map(SearchResult::getPath);
				final Iterable<Path> pathsIt = paths::iterator;
				for (Path path : pathsIt) {
					final String contents = rawFetcher.getContents(coord, path).get();
					LOGGER.debug("Contents: {}.", contents);
					final String noPack = packageRegex.matcher(contents).find() ? "" : " NO-PACK";
					final String noJavadoc = javadocRegex.matcher(contents).find() ? "" : " NO-DOC";
					final ObjectId sha = rawFetcher.getCreationSha(coord, path).get();
					final List<CommitGitHubDescription> commits = rawFetcher.getCommitsGitHubDescriptions(coord, path);
					final Optional<Instant> receivedTimeOpt = rawFetcher.getReceivedTime(coord, sha);
					final List<String> committerNames = commits.stream().map(CommitGitHubDescription::getCommitterName)
							.collect(Collectors.toList());
					final String matchingName = committerNames.stream().anyMatch((s) -> s.equals(username)) ? ""
							: " WRONG-COMMITTER '" + committerNames.stream().collect(Collectors.joining(", ")) + "'";
					final Path filePartialPath = Paths.get("blob", "master", path.toString());
					final URL fileAbsPath = new URL(repoUrl, filePartialPath.toString());
					final String late = (receivedTimeOpt.isPresent() && receivedTimeOpt.get().isAfter(deadline))
							? "LATE "
							: "";
					final String receivedStr = receivedTimeOpt.isPresent()
							? receivedTimeOpt.get().atZone(ZoneId.systemDefault()).toString()
							: "unknown";
					output.write(fileAbsPath + matchingName + noPack + noJavadoc + " " + late + "received at "
							+ receivedStr + "\n");
				}
				output.write("\n");
				output.flush();
			}
		}
	}

	public void retrieveEE() throws IOException, GitAPIException {
		final Client client = new Client();
		final Stream<Repository> repositoriesStream = ghProjects.values().stream()
				.map(RepositoryWithIssuesWithHistory::getBare);
		final Iterable<Repository> it = repositoriesStream::iterator;
		for (Repository repository : it) {
			client.retrieve(repository);
		}
	}

	public void searchForGHProjectsFromLocal() throws IOException, IllegalFormat {
		LOGGER.info("Started.");
		projectsDir = Paths.get("/home/olivier/Professions/Enseignement/Projets/EE");
		projects = new ProjectReader().asProjects(projectsDir);
		try (GitHubFetcher finder = GitHubFetcher.using(Utils.getToken())) {
			for (Project project : projects) {
				final List<RepositoryWithIssuesWithHistory> found = finder.find(project,
						Instant.parse("2017-11-05T00:00:00Z"));
				LOGGER.info("Found for {}: {}.", project.getName(), found);
				final List<RepositoryWithIssuesWithHistory> foundWithPom = found.stream()
						.filter((r) -> r.getFiles().contains("pom.xml")).collect(Collectors.toList());
				LOGGER.info("Found with POM for {}: {}.", project.getName(), foundWithPom);
			}
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
		LOGGER.info("Finished write GH projects.");
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

	private Map<Project, RepositoryWithIssuesWithHistory> find() throws IOException {
		try (GitHubFetcher finder = GitHubFetcher.using(Utils.getToken())) {
			for (Project project : projects) {
				LOGGER.info("Searching for {}.", project);
				final List<RepositoryWithIssuesWithHistory> found = finder.find(project, Instant.EPOCH);
				LOGGER.info("Found: {}.", found);
				final List<RepositoryWithIssuesWithHistory> foundWithPom = found.stream()
						.filter((r) -> r.getFiles().contains("pom.xml")).collect(Collectors.toList());
				final int nbMatches = foundWithPom.size();
				switch (nbMatches) {
				case 0:
					break;
				case 1:
					final RepositoryWithIssuesWithHistory matching = Iterables.getOnlyElement(foundWithPom);
					ghProjects.put(project, matching);
					break;
				default:
					throw new IllegalStateException(
							String.format("Found multiple matches for %s: %s.", project, foundWithPom));
				}
			}
			return ghProjects;
		}
	}
}
