package io.github.oliviercailloux.st_projects;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.net.URI;
import java.net.URL;
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
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.asciidoctor.Asciidoctor;
import org.eclipse.jgit.api.errors.CheckoutConflictException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Predicates;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Streams;

import io.github.oliviercailloux.git.git_hub.model.GitHubToken;
import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinates;
import io.github.oliviercailloux.git.git_hub.model.graph_ql.IssueWithHistory;
import io.github.oliviercailloux.git.git_hub.model.graph_ql.Repository;
import io.github.oliviercailloux.git.git_hub.model.graph_ql.RepositoryWithIssuesWithHistory;
import io.github.oliviercailloux.git.git_hub.model.v3.CommitGitHubDescription;
import io.github.oliviercailloux.git.git_hub.model.v3.SearchResult;
import io.github.oliviercailloux.git.git_hub.services.GitHubFetcherQL;
import io.github.oliviercailloux.git.git_hub.services.GitHubFetcherV3;
import io.github.oliviercailloux.git.git_hub.utils.Utils;
import io.github.oliviercailloux.st_projects.model.Functionality;
import io.github.oliviercailloux.st_projects.model.Project;
import io.github.oliviercailloux.st_projects.services.ProjectsMonitor;
import io.github.oliviercailloux.st_projects.services.git.Client;
import io.github.oliviercailloux.st_projects.services.read.IllegalFormat;
import io.github.oliviercailloux.st_projects.services.read.UsernamesReader;
import io.github.oliviercailloux.st_projects.services.spreadsheet.SpreadsheetException;
import io.github.oliviercailloux.st_projects.services.spreadsheet.SpreadsheetWriter;

public class App implements AutoCloseable {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(App.class);

	public static void main(String[] args) throws Exception {

		try (App app = new App()) {
			app.proceed();
		}
	}

	private final Map<Project, RepositoryWithIssuesWithHistory> cachedProjects;

	private ProjectsMonitor projectsMonitor;

	private SpreadsheetWriter writer;

	public App() {
		writer = new SpreadsheetWriter();
		cachedProjects = new LinkedHashMap<>();
		projectsMonitor = null;
		// TODO restore fct ignore after. Update client: don’t need history.
	}

	@Override
	public void close() throws Exception {
		projectsMonitor.close();
	}

	public void proceed() throws IOException, IllegalFormat, SpreadsheetException, GitAPIException,
			InterruptedException, ExecutionException {
		init();
//		projectsMonitor.showLastCommitsValidity();
		writeGHProjects(projectsMonitor.getSEProjects(), "SE");
//		writeGHProjects(projectsMonitor.getEEProjects(), "EE");
		retrieveCached();
//		final OutputStream out = Files.newOutputStream(Paths.get("out.txt"));
//		try (Writer output = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
//			reportL3Works(output);
//		}
	}

	public void reportL3Works(Writer output) throws IOException {
		final Pattern packageRegex = Pattern.compile("^[ \\h]*package .*", Pattern.DOTALL | Pattern.MULTILINE);
		final Pattern javadocRegex = Pattern.compile("^[ \\v\\h]*/\\*\\*.*", Pattern.DOTALL | Pattern.MULTILINE);
		try (GitHubFetcherV3 rawFetcher = GitHubFetcherV3.using(GitHubToken.getRealInstance());
				GitHubFetcherQL fetcher = GitHubFetcherQL.using(GitHubToken.getRealInstance())) {

			final Map<Integer, String> idsToUsernames;
			try (InputStream inputStream = Files.newInputStream(Paths.get("usernames.json"))) {
				final UsernamesReader reader = new UsernamesReader();
				reader.read(inputStream);
				idsToUsernames = reader.getIdsToGitHubUsernames();
			}

			final String repo = "Java-L3-Eck-Ex";
			final Instant deadline = Instant.parse("2018-03-20T00:00:00Z");
			final ImmutableSortedMap<Integer, String> idsSorted = ImmutableSortedMap.copyOf(idsToUsernames,
					Comparator.naturalOrder());
			for (Integer id : idsSorted.keySet()) {
				final String username = idsSorted.get(id);
				final RepositoryCoordinates coord = RepositoryCoordinates.from(username, repo);
				output.write("= Id: " + id + " (" + coord.getOwner() + ")\n");
				final URL base = Utils.newURL("https://github.com/");
				final URL userPath = new URL(base, coord.getOwner());
				final Optional<RepositoryWithIssuesWithHistory> prjOpt = fetcher.getRepository(coord);
				if (!prjOpt.isPresent()) {
					output.write(userPath + "\n");
					output.write("Project not found.\n");
					continue;
				}
				final URI repoUri = prjOpt.get().getBare().getURI();
				output.write(repoUri + "\n");
				final Stream<Path> paths = rawFetcher.searchForCode(coord, "class", "java").stream()
						.map(SearchResult::getPath);
				final Iterable<Path> pathsIt = paths::iterator;
				for (Path path : pathsIt) {
					final String contents = rawFetcher.getContent(coord, path).get();
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
					final URI fileAbsPath = repoUri.resolve(filePartialPath.toUri());
//					final URL fileAbsPath = new URL(repoUri, filePartialPath.toString());
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

	public void retrieveCached() throws IOException, GitAPIException {
		final Stream<Repository> repositoriesStream = cachedProjects.values().stream()
				.map(RepositoryWithIssuesWithHistory::getBare);
		final Iterable<Repository> it = repositoriesStream::iterator;
		for (Repository repository : it) {
			final Client client = Client.aboutAndUsing(
					RepositoryCoordinates.from(repository.getOwner().getLogin(), repository.getName()),
					Paths.get("../../En cours"));
			LOGGER.info("Retrieving {}.", repository);
			try {
				client.retrieve();
			} catch (CheckoutConflictException e) {
				LOGGER.error(String.format("Retrieving %s.", repository), e);
			}
		}
	}

	public void writeGHProjects(Iterable<Project> projects, String suffix) throws IOException, SpreadsheetException {
		cache(projects);
		LOGGER.info("Started write GH projects.");
		try (OutputStream out = new FileOutputStream("Wide-" + suffix + ".ods")) {
			writer.setWide(true);
			writer.setOutputStream(out);
			writer.write(projects, cachedProjects);
		}
		LOGGER.info("Finished write GH projects.");
	}

	private void cache(Iterable<Project> projects) throws IOException {
		Iterable<Project> nonCachedProjects = Streams.stream(projects)
				.filter(Predicates.not(cachedProjects.keySet()::contains))::iterator;
		try (GitHubFetcherQL finder = GitHubFetcherQL.using(GitHubToken.getRealInstance())) {
			for (Project project : nonCachedProjects) {
				final List<RepositoryWithIssuesWithHistory> found = projectsMonitor.getRepositories(project);
				LOGGER.info("Searching for {}, found {}.", project, found);

				final ImmutableSortedSet<String> fctNames = project.getFunctionalities().stream()
						.map(Functionality::getName)
						.collect(ImmutableSortedSet.toImmutableSortedSet(Comparator.naturalOrder()));
				final Function<RepositoryWithIssuesWithHistory, Long> fctCounter = CacheBuilder.newBuilder()
						.build(CacheLoader.from((r) -> r.getIssues().stream().map(IssueWithHistory::getOriginalName)
								.filter(fctNames::contains).count()));
				final Comparator<RepositoryWithIssuesWithHistory> byFctCount = Comparator
						.<RepositoryWithIssuesWithHistory, Long>comparing(fctCounter);

				final ImmutableList<RepositoryWithIssuesWithHistory> repoByMatch = ImmutableList
						.sortedCopyOf(byFctCount.reversed(), found);

				if (repoByMatch.size() >= 1) {
					final RepositoryWithIssuesWithHistory matching = repoByMatch.iterator().next();
					cachedProjects.put(project, matching);
				}
				if (repoByMatch.size() >= 2 && fctCounter.apply(repoByMatch.get(1)) >= 1) {
					throw new IllegalStateException(
							String.format("Found multiple close matches for %s: %s.", project, found));
				}
			}
		}
	}

	private void init() throws IOException, InterruptedException, ExecutionException {
		initBareProjectsMonitor();
		projectsMonitor.updateProjectsAsync();
		projectsMonitor.updateRepositoriesAsync();
		projectsMonitor.await();
	}

	private void initBareProjectsMonitor() throws IOException {
		LOGGER.info("Loading.");
		final Asciidoctor asciidoctor = Asciidoctor.Factory.create();
		LOGGER.info("Loaded.");
		projectsMonitor = ProjectsMonitor.using(asciidoctor, GitHubToken.getRealInstance());
	}
}
