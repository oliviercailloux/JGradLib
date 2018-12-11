package io.github.oliviercailloux.st_projects.servlets;

import java.io.IOException;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Dependent;
import javax.enterprise.inject.Produces;

import io.github.oliviercailloux.git.git_hub.utils.Utils;

@ApplicationScoped
public class GitHubTokenCDIFactory {
	@Produces
	@Dependent
	@GitHubToken
	public String getInstance() throws IOException {
		return Utils.getToken();
	}
}
