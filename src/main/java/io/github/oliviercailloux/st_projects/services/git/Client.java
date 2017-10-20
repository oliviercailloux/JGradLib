package io.github.oliviercailloux.st_projects.services.git;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Client {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(Client.class);

	public void checkout(String commitName) throws IOException, GitAPIException {
		/**
		 * 1. Find date issue closed.
		 *
		 * 2. Find last commit before that date according to git.
		 *
		 * 3. checkout that one.
		 */
		final Path dir = Paths.get("/home/olivier/Local/test-pdf-number-pages/");
		try (Git git = Git.open(dir.toFile())) {
			LOGGER.info("Work dir: {}.", git.getRepository().getWorkTree());
			final List<Ref> br = git.branchList().call();
			LOGGER.info("Branches: {}.", br);
			git.checkout().setName("master").call();
			git.checkout().setName(commitName).call();
		}
	}
}
