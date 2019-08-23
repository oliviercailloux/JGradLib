package io.github.oliviercailloux.grade.contexters;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableSet;

import io.github.oliviercailloux.git.ComplexClient;
import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinates;
import io.github.oliviercailloux.grade.GradingException;
import io.github.oliviercailloux.grade.context.FilesSource;
import io.github.oliviercailloux.grade.context.GitContext;

public class ContextInitializer implements GitContext {

	public static GitContext withPath(RepositoryCoordinates coordinatesSupplier, Path projectsBaseDir) {
		final ContextInitializer ci = new ContextInitializer(coordinatesSupplier, projectsBaseDir);
		ci.init();
		return ci;
	}

	private ComplexClient client;
	private RepositoryCoordinates coordinatesSupplier;
	private Path projectsBaseDir;

	private ContextInitializer(RepositoryCoordinates coordinatesSupplier, Path projectsBaseDir) {
		this.coordinatesSupplier = requireNonNull(coordinatesSupplier);
		this.projectsBaseDir = requireNonNull(projectsBaseDir);
		client = null;
	}

	public void init() throws GradingException {
		try {
			client = ComplexClient.aboutAndUsing(coordinatesSupplier, projectsBaseDir);
			{
				client.tryRetrieve();
				if (client.hasContent()) {
					client.getWholeHistory();
				}
			}
		} catch (GitAPIException | IOException e) {
			throw new GradingException(e);
		}
	}

	@Override
	public ComplexClient getClient() {
		assert client != null;
		return client;
	}

	@Override
	public FilesSource getFilesReader(Optional<RevCommit> sourceCommitOpt) {
		if (!sourceCommitOpt.isPresent()) {
			return FilesSource.empty();
		}

		final RevCommit sourceCommit = sourceCommitOpt.get();
		final ImmutableSet<Path> paths;
		try {
			paths = getClient().getPaths(sourceCommit, Predicates.alwaysTrue());
		} catch (IOException e) {
			throw new GradingException(e);
		}
		return new FilesSourceImpl(paths, (p) -> getContentFromGit(sourceCommit, p));
	}

	private String getContentFromGit(RevCommit sourceCommit, Path path) {
		try {
			if (!client.hasContent() || !client.getAllCommits().contains(sourceCommit)) {
				return "";
			}
		} catch (IOException | GitAPIException e) {
			throw new GradingException(e);
		}
		try {
			return client.fetchBlob(sourceCommit, path);
		} catch (IOException e) {
			throw new GradingException(e);
		}
	}

	public static GitContext with(RepositoryCoordinates coordinatesSupplier) {
		final String tmpDir = System.getProperty("java.io.tmpdir");
		return withPath(coordinatesSupplier, Paths.get(tmpDir));
	}

}
