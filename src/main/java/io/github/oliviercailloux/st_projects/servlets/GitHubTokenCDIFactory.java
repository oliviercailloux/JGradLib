package io.github.oliviercailloux.st_projects.servlets;

import java.io.IOException;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Dependent;
import javax.enterprise.inject.Produces;

@ApplicationScoped
public class GitHubTokenCDIFactory {
	@Produces
	@Dependent
	@GitHubToken
	public io.github.oliviercailloux.git.git_hub.model.GitHubToken getInstance() throws IOException {
		return io.github.oliviercailloux.git.git_hub.model.GitHubToken.getRealInstance();
	}
}
