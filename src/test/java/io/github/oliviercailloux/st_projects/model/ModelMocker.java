package io.github.oliviercailloux.st_projects.model;

import java.math.BigDecimal;
import java.net.URL;
import java.util.Optional;

import org.mockito.Mockito;

import io.github.oliviercailloux.st_projects.utils.Utils;

public class ModelMocker {

	public static void addIssue(final ProjectOnGitHub project, final String title) {
		final GitHubIssue issue = ModelMocker.newGitHubIssue(title, Utils.EXAMPLE_URL);
		Mockito.when(project.getIssue(title)).thenReturn(Optional.of(issue));
	}

	public static User newContributor(String name) {
		return newContributor(name, Utils.EXAMPLE_URL);
	}

	public static User newContributor(String login, URL url) {
		final User c1 = Mockito.mock(User.class);
		Mockito.when(c1.getLogin()).thenReturn(login);
		Mockito.when(c1.getHtmlURL()).thenReturn(url);
		return c1;
	}

	public static GitHubIssue newGitHubIssue(String title, URL url) {
		final GitHubIssue issue = Mockito.mock(GitHubIssue.class);
		Mockito.when(issue.getTitle()).thenReturn(title);
		Mockito.when(issue.getApiURL()).thenReturn(url);
		Mockito.when(issue.getHtmlURL()).thenReturn(url);
		return issue;
	}

	public static ProjectOnGitHub newGitHubProject(Project project, User owner, URL url) {
		final ProjectOnGitHub ghp1 = Mockito.mock(ProjectOnGitHub.class);
		Mockito.when(ghp1.getApiURL()).thenReturn(url);
		Mockito.when(ghp1.getHtmlURL()).thenReturn(url);
		Mockito.when(ghp1.getProject()).thenReturn(project);
		Mockito.when(ghp1.getOwner()).thenReturn(owner);
		return ghp1;
	}

	public static ProjectOnGitHub newGitHubProject(String projectName, String ownerName) {
		final Project project = newProject(projectName, 0);
		final User owner = newContributor(ownerName);
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
