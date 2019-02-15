package io.github.oliviercailloux.st_projects.services.grading;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;

import io.github.oliviercailloux.git.Client;
import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinates;
import io.github.oliviercailloux.st_projects.ex2.GitAndGitHub;
import io.github.oliviercailloux.st_projects.model.GitFullContext;
import io.github.oliviercailloux.st_projects.model.GradingContextWithTimeline;

public class ContextInitializerNew implements GitFullContext {

	public static ContextInitializerNew with(RepositoryCoordinates coordinatesSupplier) {
		final String tmpDir = System.getProperty("java.io.tmpdir");
		return new ContextInitializerNew(coordinatesSupplier, Paths.get(tmpDir), Instant.MAX);
	}

	public static GitFullContext withPathAndIgnoreAndInit(RepositoryCoordinates coordinatesSupplier,
			Path projectsBaseDir, Instant ignoreAfter) {
		final ContextInitializerNew ci = new ContextInitializerNew(coordinatesSupplier, projectsBaseDir, ignoreAfter);
		ci.init();
		return ci;
	}

	public static ContextInitializerNew withIgnore(RepositoryCoordinates coordinatesSupplier, Instant ignoreAfter) {
		final String tmpDir = System.getProperty("java.io.tmpdir");
		return new ContextInitializerNew(coordinatesSupplier, Paths.get(tmpDir), ignoreAfter);
	}

	private Client client;
	private Instant ignoreAfter;
	private GradingContextWithTimeline context;
	private Optional<RevCommit> lastCommitNotIgnored;
	private RepositoryCoordinates coordinatesSupplier;
	private Path projectsBaseDir;

	private ContextInitializerNew(RepositoryCoordinates coordinatesSupplier, Path projectsBaseDir,
			Instant ignoredAfter) {
		this.ignoreAfter = requireNonNull(ignoredAfter);
		this.coordinatesSupplier = requireNonNull(coordinatesSupplier);
		this.projectsBaseDir = requireNonNull(projectsBaseDir);
		clear();
	}

	private void clear() {
		client = null;
		context = null;
		lastCommitNotIgnored = null;
	}

	public void init() throws GradingException {
		try {
			client = Client.aboutAndUsing(coordinatesSupplier, projectsBaseDir);
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

	public static ContextInitializerNew withPathAndIgnore(RepositoryCoordinates coordinatesSupplier,
			Path projectsBaseDir, Instant ignoreAfter) {
		return new ContextInitializerNew(coordinatesSupplier, projectsBaseDir, ignoreAfter);
	}

}
