package io.github.oliviercailloux.st_projects.model;

import java.math.BigDecimal;
import java.net.URL;

import org.mockito.Mockito;

import io.github.oliviercailloux.st_projects.services.git_hub.Utils;

public class ModelMocker {

	public static Contributor newContributor(String name) {
		return newContributor(name, Utils.EXAMPLE_URL);
	}

	public static Contributor newContributor(String name, URL url) {
		final Contributor c1 = Mockito.mock(Contributor.class);
		Mockito.when(c1.getName()).thenReturn(name);
		Mockito.when(c1.getUrl()).thenReturn(url);
		return c1;
	}

	public static GitHubIssue newGitHubIssue(String name, URL url) {
		final GitHubIssue issue = Mockito.mock(GitHubIssue.class);
		Mockito.when(issue.getName()).thenReturn(name);
		Mockito.when(issue.getUrl()).thenReturn(url);
		return issue;
	}

	public static GitHubProject newGitHubProject(Project project, Contributor owner, URL url) {
		final GitHubProject ghp1 = Mockito.mock(GitHubProject.class);
		Mockito.when(ghp1.getApiURL()).thenReturn(url);
		Mockito.when(ghp1.getHtmlURL()).thenReturn(url);
		Mockito.when(ghp1.getProject()).thenReturn(project);
		Mockito.when(ghp1.getOwner()).thenReturn(owner);
		return ghp1;
	}

	public static GitHubProject newGitHubProject(String projectName, String ownerName) {
		final Project project = newProject(projectName, 0);
		final Contributor owner = newContributor(ownerName);
		return newGitHubProject(project, owner, Utils.EXAMPLE_URL);
	}

	public static Project newProject(String name, int numberOfFunctionalities) {
		final Project project = new Project(name);
		for (int i = 1; i <= numberOfFunctionalities; ++i) {
			project.getFunctionalities()
					.add(new Functionality(name + "-f" + i, name + "-d" + i, BigDecimal.valueOf(i)));
		}
		return project;
	}

}
