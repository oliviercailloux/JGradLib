package io.github.oliviercailloux.st_projects.services.grading;

import static com.google.common.base.Preconditions.checkArgument;
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

/**
 *
 * TODO change Supplier into GradingContextSupplier, which throws a
 * GradingException.
 *
 * @author Olivier Cailloux
 *
 */
public class ContextInitializer implements Supplier<GitFullContext> {
	private static class GitFullContexter implements GitFullContext {

		private Client client;
		private Instant ignoreAfter;
		private Optional<RevCommit> lastCommitNotIgnored;
		private Optional<GradingContextWithTimeline> context;

		public GitFullContexter(Client client, Optional<RevCommit> lastCommitNotIgnored,
				Optional<GradingContextWithTimeline> context, Instant ignoreAfter) {
			checkArgument(lastCommitNotIgnored.isPresent() == context.isPresent());
			this.client = requireNonNull(client);
			this.lastCommitNotIgnored = requireNonNull(lastCommitNotIgnored);
			this.context = requireNonNull(context);
			this.ignoreAfter = requireNonNull(ignoreAfter);
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
			return context.get().getCommitsReceptionTime().get(lastCommitNotIgnored.get());
		}

	}

	public static ContextInitializer with(RepositoryCoordinates coordinates) {
		final String tmpDir = System.getProperty("java.io.tmpdir");
		return new ContextInitializer(coordinates, Paths.get(tmpDir), Instant.MAX);
	}

	public static ContextInitializer withPathAndIgnore(RepositoryCoordinates coordinates, Path projectsBaseDir,
			Instant ignoreAfter) {
		return new ContextInitializer(coordinates, projectsBaseDir, ignoreAfter);
	}

	public static ContextInitializer withIgnore(RepositoryCoordinates coordinates, Instant ignoreAfter) {
		final String tmpDir = System.getProperty("java.io.tmpdir");
		return new ContextInitializer(coordinates, Paths.get(tmpDir), ignoreAfter);
	}

	private RepositoryCoordinates coordinates;
	private Path projectsBaseDir;
	private Instant ignoreAfter;

	private ContextInitializer(RepositoryCoordinates coordinates, Path projectsBaseDir, Instant ignoredAfter) {
		this.ignoreAfter = requireNonNull(ignoredAfter);
		this.coordinates = requireNonNull(coordinates);
		this.projectsBaseDir = requireNonNull(projectsBaseDir);
	}

	@Override
	public GitFullContext get() throws GradingException {
		try {
			final Client client = Client.aboutAndUsing(coordinates, projectsBaseDir);
			{
				client.tryRetrieve();
				client.hasContent();
				client.getWholeHistory();
			}
			final Optional<RevCommit> lastCommitNotIgnored;
			final Optional<GradingContextWithTimeline> context;
			if (client.hasContentCached()) {
				final Map<ObjectId, Instant> receivedAt = new GitAndGitHub().check(client);
				context = Optional.of(GradingContextWithTimeline.given(client, receivedAt));
				lastCommitNotIgnored = context.get()
						.getLatestNotIgnoredChildOf(client.getCommit(client.resolve("origin/master")), ignoreAfter);
			} else {
				context = Optional.empty();
				lastCommitNotIgnored = Optional.empty();
			}
			if (lastCommitNotIgnored.isPresent()) {
				client.checkout(lastCommitNotIgnored.get());
				client.setDefaultRevSpec(lastCommitNotIgnored.get());
			}
			return new GitFullContexter(client, lastCommitNotIgnored, context, ignoreAfter);
		} catch (GitAPIException | IOException e) {
			throw new GradingException(e);
		}
	}

}
