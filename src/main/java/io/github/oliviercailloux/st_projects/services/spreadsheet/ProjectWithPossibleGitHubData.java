package io.github.oliviercailloux.st_projects.services.spreadsheet;

import java.util.Optional;

import io.github.oliviercailloux.st_projects.model.GitHubProject;
import io.github.oliviercailloux.st_projects.model.Project;

class ProjectWithPossibleGitHubData {
	private Optional<GitHubProject> ghProject;

	private Project project;

	public ProjectWithPossibleGitHubData(GitHubProject project) {
		this.project = project.getProject();
		ghProject = Optional.of(project);
	}

	public ProjectWithPossibleGitHubData(Project project) {
		this.project = project;
		ghProject = Optional.empty();
	}

	public Optional<GitHubProject> getGhProject() {
		return ghProject;
	}

	public Project getProject() {
		return project;
	}

	public void setGhProject(Optional<GitHubProject> ghProject) {
		this.ghProject = ghProject;
	}

	public void setProject(Project project) {
		this.project = project;
	}
}
