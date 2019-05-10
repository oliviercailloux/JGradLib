package io.github.oliviercailloux.grade.contexters;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Optional;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;

import com.google.common.collect.ImmutableMap;

import io.github.oliviercailloux.git.Client;
import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinates;
import io.github.oliviercailloux.git.git_hub.services.GitHubTimelineReader;
import io.github.oliviercailloux.grade.GradingException;
import io.github.oliviercailloux.grade.context.FilesSource;
import io.github.oliviercailloux.grade.context.GitContext;
import io.github.oliviercailloux.grade.context.GitFullContext;
import io.github.oliviercailloux.grade.context.GradingContextWithTimeline;

public class FullContextInitializer implements GitFullContext {

	public static GitFullContext withPath(RepositoryCoordinates coordinatesSupplier, Path projectsBaseDir) {
		return withPathAndIgnore(coordinatesSupplier, projectsBaseDir, Instant.MAX);
	}

	public static GitFullContext withPathAndIgnore(RepositoryCoordinates coordinatesSupplier, Path projectsBaseDir,
			Instant ignoreAfter) {
		final GitContext context = ContextInitializer.withPath(coordinatesSupplier, projectsBaseDir);
		final FullContextInitializer ci = new FullContextInitializer(context, ignoreAfter);
		ci.init();
		return ci;
	}

	public static GitFullContext withIgnore(RepositoryCoordinates coordinatesSupplier, Instant ignoreAfter) {
		final String tmpDir = System.getProperty("java.io.tmpdir");
		return withPathAndIgnore(coordinatesSupplier, Paths.get(tmpDir), ignoreAfter);
	}

	private Instant ignoreAfter;
	private GradingContextWithTimeline context;
	private Optional<RevCommit> lastCommitNotIgnored;
	private GitContext delegate;

	private FullContextInitializer(GitContext context, Instant ignoredAfter) {
		delegate = requireNonNull(context);
		this.ignoreAfter = requireNonNull(ignoredAfter);
		lastCommitNotIgnored = null;
	}

	public void init() throws GradingException {
		final Client client = getClient();
		try {
			if (client.hasContentCached()) {
				final GitHubTimelineReader gitHubReceptionTimer = new GitHubTimelineReader();
				gitHubReceptionTimer.getReceptionRanges(client);
				final ImmutableMap<ObjectId, Instant> receivedAt = gitHubReceptionTimer.getReceivedAtLowerBounds();
				context = GradingContextWithTimeline.given(client, receivedAt);
				lastCommitNotIgnored = context
						.getLatestNotIgnoredChildOf(client.getCommit(client.resolve("origin/master")), ignoreAfter);
			} else {
				lastCommitNotIgnored = Optional.empty();
			}
		} catch (IOException e) {
			throw new GradingException(e);
		}
	}

	@Override
	public Client getClient() {
		return delegate.getClient();
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

	public ImmutableMap<ObjectId, Instant> getCommitsReceptionTime() {
		return context.getCommitsReceptionTime();
	}

	@Override
	public FilesSource getFilesReader(Optional<RevCommit> sourceCommit) {
		return delegate.getFilesReader(sourceCommit);
	}

	public static GitFullContext with(RepositoryCoordinates coordinatesSupplier) {
		final String tmpDir = System.getProperty("java.io.tmpdir");
		return withPathAndIgnore(coordinatesSupplier, Paths.get(tmpDir), Instant.MAX);
	}

}
