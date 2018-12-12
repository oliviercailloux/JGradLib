package io.github.oliviercailloux.st_projects.model;

import java.math.BigDecimal;
import java.net.URI;

import org.mockito.Mockito;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.ImmutableSortedSet;

import io.github.oliviercailloux.git.git_hub.model.graph_ql.IssueBare;
import io.github.oliviercailloux.git.git_hub.model.graph_ql.IssueWithHistory;
import io.github.oliviercailloux.git.git_hub.model.graph_ql.Repository;
import io.github.oliviercailloux.git.git_hub.model.graph_ql.RepositoryWithIssuesWithHistory;
import io.github.oliviercailloux.git.git_hub.model.graph_ql.User;
import io.github.oliviercailloux.git.git_hub.utils.Utils;

public class ModelMocker {

	public static void addIssue(RepositoryWithIssuesWithHistory project, String title) {
		final IssueWithHistory issue = ModelMocker.newGitHubIssue(title, Utils.EXAMPLE_URI);
		Mockito.when(project.getIssuesOriginallyNamed(title)).thenReturn(ImmutableSortedSet.of(issue));
	}

	public static ImmutableList<Functionality> getFunctionalities(String prefix, int numberOfFunctionalities) {
		final Builder<Functionality> builder = ImmutableList.builder();
		for (int i = 1; i <= numberOfFunctionalities; ++i) {
			builder.add(new Functionality(prefix + "-f" + i, prefix + "-d" + i, BigDecimal.valueOf(i)));
		}
		final ImmutableList<Functionality> fcts = builder.build();
		return fcts;
	}

	public static User newContributor(String name) {
		return newContributor(name, Utils.EXAMPLE_URI);
	}

	public static User newContributor(String login, URI url) {
		final User c1 = Mockito.mock(User.class);
		Mockito.when(c1.getLogin()).thenReturn(login);
		Mockito.when(c1.getHtmlURI()).thenReturn(url);
		return c1;
	}

	public static IssueWithHistory newGitHubIssue(String name, URI uri) {
		final IssueWithHistory issue = Mockito.mock(IssueWithHistory.class);
		Mockito.when(issue.getOriginalName()).thenReturn(name);
		final IssueBare issueBare = newIssueBare(uri);
		Mockito.when(issue.getBare()).thenReturn(issueBare);
		return issue;
	}

	public static RepositoryWithIssuesWithHistory newGitHubProject(String ownerName) {
		final User owner = newContributor(ownerName);
		return newGitHubProject(owner, Utils.EXAMPLE_URI);
	}

	public static RepositoryWithIssuesWithHistory newGitHubProject(User owner, URI uri) {
		final RepositoryWithIssuesWithHistory ghp1 = Mockito.mock(RepositoryWithIssuesWithHistory.class);
		final Repository mockedRepo = newRepository(uri);
		Mockito.when(ghp1.getBare()).thenReturn(mockedRepo);
		Mockito.when(ghp1.getOwner()).thenReturn(owner);
		return ghp1;
	}

	public static IssueBare newIssueBare(URI uri) {
		final IssueBare mocked = Mockito.mock(IssueBare.class);
		Mockito.when(mocked.getHtmlURI()).thenReturn(uri);
		return mocked;
	}

	public static Project newProject(String name, int numberOfFunctionalities) {
		final ImmutableList<Functionality> fcts = getFunctionalities(name, numberOfFunctionalities);
		final Project project = Project.from(name, fcts);
		return project;
	}

	public static Repository newRepository(URI uri) {
		final Repository mocked = Mockito.mock(Repository.class);
		Mockito.when(mocked.getURI()).thenReturn(uri);
		return mocked;
	}

}
