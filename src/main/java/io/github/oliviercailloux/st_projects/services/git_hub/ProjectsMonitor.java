package io.github.oliviercailloux.st_projects.services.git_hub;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

import java.math.BigDecimal;
import java.net.URL;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.Path;

import org.asciidoctor.Asciidoctor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.diffplug.common.base.Errors;
import com.diffplug.common.base.Throwing;
import com.google.common.base.Predicates;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.MoreCollectors;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import com.google.common.collect.Streams;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import io.github.oliviercailloux.git_hub.RepositoryCoordinates;
import io.github.oliviercailloux.git_hub.low.CommitGitHubDescription;
import io.github.oliviercailloux.st_projects.model.Project;
import io.github.oliviercailloux.st_projects.model.RepositoryWithFiles;
import io.github.oliviercailloux.st_projects.model.RepositoryWithIssuesWithHistory;
import io.github.oliviercailloux.st_projects.services.read.FunctionalitiesReader;
import io.github.oliviercailloux.st_projects.services.read.IllegalFormat;
import io.github.oliviercailloux.st_projects.services.read.ProjectReader;
import io.github.oliviercailloux.st_projects.servlets.GitHubToken;

@ApplicationScoped
@Path("stateless-bean")
public class ProjectsMonitor implements AutoCloseable {
	private static final Instant FLOOR_SEARCH_DATE = Instant.parse("2018-03-01T00:00:00Z");

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(ProjectsMonitor.class);

	static public ProjectsMonitor using(Asciidoctor asciidoctor, String token) {
		return new ProjectsMonitor(asciidoctor, token);
	}

	private final RepositoryCoordinates baseCoordinates;

	private final ExecutorService executor;

	private boolean fetched;

	private GitHubFetcher fetcher;

	private Future<?> lastTask;

	private final BiMap<String, Project> nameToProjectEE = Maps.synchronizedBiMap(HashBiMap.create());

	private final BiMap<String, Project> nameToProjectSE = Maps.synchronizedBiMap(HashBiMap.create());

	private final ProjectReader projectReader;

	private final ListMultimap<Project, RepositoryWithIssuesWithHistory> projectToRepos = Multimaps
			.synchronizedListMultimap(
					MultimapBuilder.treeKeys(Comparator.comparing(Project::getName)).arrayListValues().build());

	private RawGitHubFetcher rawFetcher;

	/**
	 * To be proxyable (CDI), a constructor must be non-private and with no
	 * arguments. The container will not execute the methods of this instance,
	 * however. TODO use an interface, if appropriate.
	 */
	public ProjectsMonitor() {
		LOGGER.info("Invoked no-args constructor.");
		rawFetcher = null;
		fetcher = null;
		baseCoordinates = null;
		projectReader = null;
		executor = null;
		lastTask = null;
		fetched = false;
	}

	@Inject
	private ProjectsMonitor(Asciidoctor asciidoctor, @GitHubToken String token) {
		LOGGER.info("Received {} and {} through container injection.", asciidoctor, token.substring(0, 3));
		rawFetcher = RawGitHubFetcher.using(token);
		fetcher = GitHubFetcher.using(token);
		baseCoordinates = RepositoryCoordinates.from("oliviercailloux", "projets");
		projectReader = ProjectReader.using(requireNonNull(asciidoctor));
		executor = Executors
				.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat("Projects-Monitor").build());
		lastTask = null;
		fetched = false;
	}

	public void await() throws InterruptedException, ExecutionException {
		if (lastTask == null) {
			return;
		}
		lastTask.get();
	}

	@Override
	public void close() throws Exception {
		LOGGER.debug("Shutting down.");
		executor.shutdown();
		LOGGER.debug("Shut down.");
	}

	public ImmutableSet<Project> getEEProjects() {
		checkState(fetched);
		synchronized (nameToProjectEE) {
			return ImmutableSet.copyOf(nameToProjectEE.values());
		}
	}

	public Optional<Project> getProject(String name) {
		checkState(fetched);
		return Stream.of(nameToProjectSE, nameToProjectEE).map((m) -> m.get(name)).filter(Predicates.isNull().negate())
				.collect(MoreCollectors.toOptional());
	}

	public ImmutableSet<Project> getProjects() {
		checkState(fetched);
		return ImmutableSet.copyOf(Streams.concat(getSEProjects().stream(), getEEProjects().stream())::iterator);
	}

	public List<RepositoryWithIssuesWithHistory> getRepositories(Project project) {
		checkState(fetched);
		synchronized (projectToRepos) {
			if (projectToRepos.containsKey(requireNonNull(project))) {
				return ImmutableList.copyOf(projectToRepos.get(project));
			}
		}
		return ImmutableList.of();
	}

	public ImmutableSet<Project> getSEProjects() {
		checkState(fetched);
		synchronized (nameToProjectSE) {
			return ImmutableSet.copyOf(nameToProjectSE.values());
		}
	}

	public void showLastCommitsValidity() {
		checkState(fetched);
		synchronized (projectToRepos) {
			final Collection<RepositoryWithIssuesWithHistory> allRepos = projectToRepos.values();
			for (RepositoryWithIssuesWithHistory repo : allRepos) {
				final List<CommitGitHubDescription> commits = rawFetcher
						.getCommitsGitHubDescriptions(repo.getBare().getCoordinates(), true);
				for (CommitGitHubDescription descr : (Iterable<CommitGitHubDescription>) commits.stream()
						.limit(10)::iterator) {
					LOGGER.info("{} - {} - {}", repo.getBare().getName(), descr.getCommitterName(),
							descr.getCommitterCommitDate());
				}
			}
		}
	}

	public void showLastCommitsValidity(RepositoryCoordinates coordinates) {
		final List<CommitGitHubDescription> commits = rawFetcher.getCommitsGitHubDescriptions(coordinates);
		for (CommitGitHubDescription descr : (Iterable<CommitGitHubDescription>) commits.stream().limit(3)::iterator) {
			LOGGER.info("{} - {}", descr.getCommitterName(), descr.getCommitterCommitDate());
		}
	}

	public void updateProjectsAsync() {
		checkState(projectReader != null);
		fetched = true;
		lastTask = submit(this::updateProjects, "Updating projects.");
	}

	public void updateRepositoriesAsync() {
		checkState(projectReader != null);
		fetched = true;
		lastTask = submit(this::updateRepos, "Updating repositories.");
	}

	private synchronized void nameToProject(java.nio.file.Path path, BiMap<String, Project> mapToWrite)
			throws IllegalFormat {
		LOGGER.info("Querying {}.", path);
		final Instant queried = Instant.now();
		final Optional<RepositoryWithFiles> repoOpt = fetcher.getRepositoryWithFiles(baseCoordinates, path);
		if (!repoOpt.isPresent()) {
			throw new IllegalStateException("Repository not found.");
		}
		final RepositoryWithFiles repo = repoOpt.get();
		final ImmutableMap<java.nio.file.Path, String> contentFromFileNames = repo.getContentFromFileNames();
		final ImmutableSet<String> previously;
		synchronized (mapToWrite) {
			previously = ImmutableSet.copyOf(mapToWrite.keySet());
		}
		final Set<String> current = new LinkedHashSet<>();
		for (Entry<java.nio.file.Path, String> entry : contentFromFileNames.entrySet()) {
			final java.nio.file.Path file = entry.getKey();
			LOGGER.info("Getting {}.", file);
			final Optional<Instant> lastModification = rawFetcher.getLastModification(baseCoordinates, file);
			if (!lastModification.isPresent()) {
				throw new IllegalStateException("Last modification time not found.");
			}
			final URL url = repo.getURL(file);
			final Project project = projectReader.asProject(entry.getValue(), url, lastModification.get(), queried);
			final boolean added = current.add(project.getName());
			checkState(added, "Two projects with same name.");
			mapToWrite.put(project.getName(), project);
		}
		final Set<String> removed = Sets.difference(previously, current);
		for (String remv : removed) {
			mapToWrite.remove(remv);
		}
	}

	private Future<?> submit(Throwing.Runnable task, String debugString) {
		return executor.submit(Errors.createHandling((t) -> LOGGER.info(debugString, t)).wrap(task));
	}

	private synchronized void updateProjects() throws IllegalFormat {
		checkState(projectReader != null);
		final Asciidoctor asciidoctor = projectReader.getFunctionalitiesReader().getAsciidoctor();
		projectReader.setFunctionalitiesReader(FunctionalitiesReader.usingDefault(asciidoctor, BigDecimal.ONE));
		nameToProject(Paths.get("SE/"), nameToProjectSE);
		projectReader.setFunctionalitiesReader(FunctionalitiesReader.using(asciidoctor));
		nameToProject(Paths.get("EE/"), nameToProjectEE);
		checkState(Sets.intersection(nameToProjectSE.keySet(), nameToProjectEE.keySet()).isEmpty(),
				"Two projects with same name but different types.");
	}

	private synchronized void updateRepos() {
		final SetView<String> projectNames = Sets.union(nameToProjectSE.keySet(), nameToProjectEE.keySet());
		final Iterator<Entry<Project, RepositoryWithIssuesWithHistory>> it = projectToRepos.entries().iterator();
		while (it.hasNext()) {
			final Entry<Project, RepositoryWithIssuesWithHistory> e = it.next();
			if (!projectNames.contains(e.getKey().getName())) {
				it.remove();
			}
		}

		for (Project project : Sets.union(nameToProjectSE.values(), nameToProjectEE.values())) {
			List<RepositoryWithIssuesWithHistory> matching;
			try {
				matching = fetcher.find(project, FLOOR_SEARCH_DATE);
			} catch (UnsupportedOperationException e) {
				LOGGER.info(String.format("Oops with %s:", project), e);
				matching = ImmutableList.of();
			}
			projectToRepos.putAll(project, matching);
		}
	}

}
