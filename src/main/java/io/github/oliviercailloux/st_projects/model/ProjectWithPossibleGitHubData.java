package io.github.oliviercailloux.st_projects.model;

import java.util.Optional;

public class ProjectWithPossibleGitHubData {
	private Optional<ProjectOnGitHub> ghProject;

	private Project project;

	public ProjectWithPossibleGitHubData(Project project) {
		this.project = project;
		ghProject = Optional.empty();
	}

	public ProjectWithPossibleGitHubData(Project project, ProjectOnGitHub projectGH) {
		this.project = project;
		ghProject = Optional.of(projectGH);
	}

	public Optional<ProjectOnGitHub> getGhProject() {
		return ghProject;
	}

	public Project getProject() {
		return project;
	}

	public void setGhProject(Optional<ProjectOnGitHub> ghProject) {
		this.ghProject = ghProject;
	}

	public void setProject(Project project) {
		this.project = project;
	}
}
