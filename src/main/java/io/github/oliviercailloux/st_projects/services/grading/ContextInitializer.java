package io.github.oliviercailloux.st_projects.services.grading;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;

import io.github.oliviercailloux.git.Client;
import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinates;
import io.github.oliviercailloux.st_projects.ex2.GitAndGitHub;
import io.github.oliviercailloux.st_projects.model.GitFullContext;
import io.github.oliviercailloux.st_projects.model.GradingContextWithTimeline;
import io.github.oliviercailloux.st_projects.model.GradingContexter;

public class ContextInitializer implements GitFullContext, GradingContexter {

	public static ContextInitializer withPathAndIgnore(Supplier<RepositoryCoordinates> coordinatesSupplier,
			Path projectsBaseDir, Instant ignoreAfter) {
		return new ContextInitializer(coordinatesSupplier, projectsBaseDir, ignoreAfter);
	}

	public static ContextInitializer withIgnore(Supplier<RepositoryCoordinates> coordinatesSupplier,
			Instant ignoreAfter) {
		final String tmpDir = System.getProperty("java.io.tmpdir");
		return new ContextInitializer(coordinatesSupplier, Paths.get(tmpDir), ignoreAfter);
	}

	private Client client;
	private Instant ignoreAfter;
	private GradingContextWithTimeline context;
	private Optional<RevCommit> lastCommitNotIgnored;
	private Supplier<RepositoryCoordinates> coordinatesSupplier;
	private Path projectsBaseDir;

	private ContextInitializer(Supplier<RepositoryCoordinates> coordinatesSupplier, Path projectsBaseDir,
			Instant ignoredAfter) {
		this.ignoreAfter = requireNonNull(ignoredAfter);
		this.coordinatesSupplier = requireNonNull(coordinatesSupplier);
		this.projectsBaseDir = requireNonNull(projectsBaseDir);
		clear();
	}

	@Override
	public void clear() {
		client = null;
		context = null;
		lastCommitNotIgnored = null;
	}

	@Override
	public void init() throws GradingException {
		try {
			client = Client.aboutAndUsing(coordinatesSupplier.get(), projectsBaseDir);
			{
				client.tryRetrieve();
				client.hasContent();
				client.getWholeHistory();
			}
			if (client.hasContentCached()) {
				final Map<ObjectId, Instant> receivedAt = new GitAndGitHub().check(client);
				context = GradingContextWithTimeline.given(client, receivedAt);
				lastCommitNotIgnored = context
						.getLatestNotIgnoredChildOf(client.getCommit(client.resolve("origin/master")), ignoreAfter);
			} else {
				lastCommitNotIgnored = Optional.empty();
			}
			if (lastCommitNotIgnored.isPresent()) {
				client.checkout(lastCommitNotIgnored.get());
				client.setDefaultRevSpec(lastCommitNotIgnored.get());
			}
		} catch (GitAPIException | IOException e) {
			throw new GradingException(e);
		}
	}

	@Override
	public Client getClient() {
		assert client != null;
		return client;
	}

	@Override
	public Instant getIgnoredAfter() {
		return ignoreAfter;
	}

	@Override
	public Optional<RevCommit> getMainCommit() {
		return lastCommitNotIgnored;
	}

	@Override
	public Instant getSubmittedTime() {
		checkState(lastCommitNotIgnored.isPresent());
		return context.getCommitsReceptionTime().get(lastCommitNotIgnored.get());
	}

	public static ContextInitializer with(Supplier<RepositoryCoordinates> coordinatesSupplier) {
		final String tmpDir = System.getProperty("java.io.tmpdir");
		return new ContextInitializer(coordinatesSupplier, Paths.get(tmpDir), Instant.MAX);
	}

}
