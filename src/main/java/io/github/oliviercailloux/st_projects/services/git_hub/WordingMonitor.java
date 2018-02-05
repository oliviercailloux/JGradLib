package io.github.oliviercailloux.st_projects.services.git_hub;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import javax.enterprise.context.ApplicationScoped;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Maps;

import io.github.oliviercailloux.st_projects.model.Project;
import io.github.oliviercailloux.st_projects.services.read.IllegalFormat;
import io.github.oliviercailloux.st_projects.utils.Utils;

@ApplicationScoped
public class WordingMonitor {
	private final BiMap<String, Project> nameToProject = Maps.synchronizedBiMap(HashBiMap.create());

	public Optional<Project> getProject(String name) {
		return Utils.getOptionally(nameToProject, name);
	}

	public void update() throws IllegalFormat, IOException {
		final List<Project> projects;
		try (RawGitHubFetcher fetcher = new RawGitHubFetcher()) {
			projects = fetcher.fetchProjects();
		}
		nameToProject.clear();
		for (Project project : projects) {
			nameToProject.put(project.getName(), project);
		}
	}
}
