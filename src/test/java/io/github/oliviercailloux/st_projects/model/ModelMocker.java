package io.github.oliviercailloux.st_projects.model;

import java.math.BigDecimal;
import java.net.URL;

import org.mockito.Mockito;

import com.google.common.collect.ImmutableSortedSet;

import io.github.oliviercailloux.st_projects.utils.Utils;

public class ModelMocker {

	public static void addIssue(final ProjectOnGitHub project, final String title) {
		final Issue issue = ModelMocker.newGitHubIssue(title, Utils.EXAMPLE_URL);
		Mockito.when(project.getIssuesNamed(title)).thenReturn(ImmutableSortedSet.of(issue));
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

	public static Issue newGitHubIssue(String name, URL url) {
		final Issue issue = Mockito.mock(Issue.class);
		Mockito.when(issue.getOriginalName()).thenReturn(name);
		Mockito.when(issue.getApiURL()).thenReturn(url);
		Mockito.when(issue.getHtmlURL()).thenReturn(url);
		return issue;
	}

	public static ProjectOnGitHub newGitHubProject(String ownerName) {
		final User owner = newContributor(ownerName);
		return newGitHubProject(owner, Utils.EXAMPLE_URL);
	}

	public static ProjectOnGitHub newGitHubProject(User owner, URL url) {
		final ProjectOnGitHub ghp1 = Mockito.mock(ProjectOnGitHub.class);
		Mockito.when(ghp1.getApiURL()).thenReturn(url);
		Mockito.when(ghp1.getHtmlURL()).thenReturn(url);
		Mockito.when(ghp1.getOwner()).thenReturn(owner);
		return ghp1;
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
