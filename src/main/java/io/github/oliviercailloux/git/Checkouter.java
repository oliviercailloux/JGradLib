package io.github.oliviercailloux.git;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.AnyObjectId;

import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinates;

public class Checkouter extends Client {

	Checkouter(RepositoryCoordinates coordinates, Path outputBaseDir) {
		super(coordinates, outputBaseDir);
	}

	public void checkout(AnyObjectId commitId) throws IOException, GitAPIException {
		try (Git git = open()) {
			git.checkout().setName(commitId.getName()).call();
		}
	}

	public void checkout(String name) throws IOException, GitAPIException {
		try (Git git = open()) {
			git.checkout().setName(name).call();
		}
	}

	public static Checkouter about(RepositoryCoordinates coordinates) {
		final String tmpDir = System.getProperty("java.io.tmpdir");
		return new Checkouter(coordinates, Paths.get(tmpDir));
	}

	public static Checkouter aboutAndUsing(RepositoryCoordinates coordinates, Path path) {
		return new Checkouter(coordinates, path);
	}

}
