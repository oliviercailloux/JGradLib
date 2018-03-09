package io.github.oliviercailloux.st_projects.model;

import java.math.BigDecimal;
import java.net.URL;

import org.mockito.Mockito;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.ImmutableSortedSet;

import io.github.oliviercailloux.git_hub_gql.IssueBareQL;
import io.github.oliviercailloux.git_hub_gql.RepositoryQL;
import io.github.oliviercailloux.git_hub_gql.UserQL;
import io.github.oliviercailloux.st_projects.utils.Utils;

public class ModelMocker {

	public static void addIssue(RepositoryWithIssuesWithHistoryQL project, String title) {
		final IssueWithHistoryQL issue = ModelMocker.newGitHubIssue(title, Utils.EXAMPLE_URL);
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

	public static UserQL newContributor(String name) {
		return newContributor(name, Utils.EXAMPLE_URL);
	}

	public static UserQL newContributor(String login, URL url) {
		final UserQL c1 = Mockito.mock(UserQL.class);
		Mockito.when(c1.getLogin()).thenReturn(login);
		Mockito.when(c1.getHtmlURL()).thenReturn(url);
		return c1;
	}

	public static IssueWithHistoryQL newGitHubIssue(String name, URL url) {
		final IssueWithHistoryQL issue = Mockito.mock(IssueWithHistoryQL.class);
		Mockito.when(issue.getOriginalName()).thenReturn(name);
		final IssueBareQL issueBare = newIssueBare(url);
		Mockito.when(issue.getBare()).thenReturn(issueBare);
		return issue;
	}

	public static RepositoryWithIssuesWithHistoryQL newGitHubProject(String ownerName) {
		final UserQL owner = newContributor(ownerName);
		return newGitHubProject(owner, Utils.EXAMPLE_URL);
	}

	public static RepositoryWithIssuesWithHistoryQL newGitHubProject(UserQL owner, URL url) {
		final RepositoryWithIssuesWithHistoryQL ghp1 = Mockito.mock(RepositoryWithIssuesWithHistoryQL.class);
		final RepositoryQL mockedRepo = newRepository(url);
		Mockito.when(ghp1.getBare()).thenReturn(mockedRepo);
		Mockito.when(ghp1.getOwner()).thenReturn(owner);
		return ghp1;
	}

	public static IssueBareQL newIssueBare(URL url) {
		final IssueBareQL mocked = Mockito.mock(IssueBareQL.class);
		Mockito.when(mocked.getHtmlURL()).thenReturn(url);
		return mocked;
	}

	public static Project newProject(String name, int numberOfFunctionalities) {
		final ImmutableList<Functionality> fcts = getFunctionalities(name, numberOfFunctionalities);
		final Project project = Project.from(name, fcts);
		return project;
	}

	public static RepositoryQL newRepository(URL url) {
		final RepositoryQL mocked = Mockito.mock(RepositoryQL.class);
		Mockito.when(mocked.getHtmlURL()).thenReturn(url);
		return mocked;
	}

}
