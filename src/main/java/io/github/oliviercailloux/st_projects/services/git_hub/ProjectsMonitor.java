package io.github.oliviercailloux.st_projects.services.git_hub;

import static java.util.Objects.requireNonNull;

import java.math.BigDecimal;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import javax.enterprise.context.ApplicationScoped;
import javax.ws.rs.Path;

import org.asciidoctor.Asciidoctor;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

import io.github.oliviercailloux.git_hub.RepositoryCoordinates;
import io.github.oliviercailloux.st_projects.model.Project;
import io.github.oliviercailloux.st_projects.model.RepositoryWithFiles;
import io.github.oliviercailloux.st_projects.services.read.FunctionalitiesReader;
import io.github.oliviercailloux.st_projects.services.read.IllegalFormat;
import io.github.oliviercailloux.st_projects.services.read.ProjectReader;
import io.github.oliviercailloux.st_projects.utils.Utils;

@ApplicationScoped
@Path("stateless-bean")
public class ProjectsMonitor {
	static public ProjectsMonitor using(Asciidoctor asciidoctor, String token) {
		return new ProjectsMonitor(asciidoctor, token);
	}

	private Asciidoctor asciidoctor;

	private final RepositoryCoordinates coordinates;

	private GitHubFetcher fetcher;

	private final BiMap<String, Project> nameToProjectEE = Maps.synchronizedBiMap(HashBiMap.create());

	private final BiMap<String, Project> nameToProjectSE = Maps.synchronizedBiMap(HashBiMap.create());

	private final ProjectReader projectReader;

	private RawGitHubFetcher rawFetcher;

	private ProjectsMonitor(Asciidoctor asciidoctor, String token) {
		this.asciidoctor = requireNonNull(asciidoctor);
		rawFetcher = RawGitHubFetcher.using(token);
		fetcher = GitHubFetcher.using(token);
		coordinates = RepositoryCoordinates.from("oliviercailloux", "projets");
		projectReader = ProjectReader.noInit();
	}

	public Optional<Project> getEEProject(String name) {
		return Utils.getOptionally(nameToProjectEE, name);
	}

	public Set<Project> getEEProjects() {
		return nameToProjectEE.values();
	}

	public Optional<Project> getSEProject(String name) {
		return Utils.getOptionally(nameToProjectSE, name);
	}

	public Set<Project> getSEProjects() {
		return nameToProjectSE.values();
	}

	public void update() throws IllegalFormat {
		projectReader.setFunctionalitiesReader(FunctionalitiesReader.usingDefault(asciidoctor, BigDecimal.ONE));
		nameToProject(Paths.get("SE/"), nameToProjectSE);
		projectReader.setFunctionalitiesReader(FunctionalitiesReader.using(asciidoctor));
		nameToProject(Paths.get("EE/"), nameToProjectEE);
	}

	private void nameToProject(java.nio.file.Path path, BiMap<String, Project> mapToWrite) throws IllegalFormat {
		final Instant queried = Instant.now();
		final ImmutableMap<String, String> contentFromFileNames;
		final Optional<RepositoryWithFiles> repoOpt = fetcher.getRepositoryWithFiles(coordinates, path);
		if (!repoOpt.isPresent()) {
			throw new IllegalStateException("Repository not found.");
		}
		final RepositoryWithFiles repo = repoOpt.get();
		contentFromFileNames = repo.getContentFromFileNames();
		mapToWrite.clear();
		for (String fileName : contentFromFileNames.keySet()) {
			final String content = contentFromFileNames.get(fileName);
			final Optional<Instant> lastModification = rawFetcher.getLastModification(coordinates,
					path.resolve(Paths.get(fileName)));
			if (!lastModification.isPresent()) {
				throw new IllegalStateException("Last modification time not found.");
			}
			final Project project = projectReader.asProject(content, fileName, lastModification.get(), queried);
			mapToWrite.put(project.getName(), project);
		}
	}
}
