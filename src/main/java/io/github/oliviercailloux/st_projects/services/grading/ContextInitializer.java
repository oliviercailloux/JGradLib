package io.github.oliviercailloux.st_projects.services.grading;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

import java.io.IOException;
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

	public static ContextInitializer ignoreAfter(Supplier<RepositoryCoordinates> coordinatesSupplier,
			Instant ignoreAfter) {
		return new ContextInitializer(coordinatesSupplier, ignoreAfter);
	}

	public static ContextInitializer noIgnore(Supplier<RepositoryCoordinates> coordinatesSupplier) {
		return new ContextInitializer(coordinatesSupplier, Instant.MAX);
	}

	private Client client;
	private Instant ignoreAfter;
	private GradingContextWithTimeline context;
	private Optional<RevCommit> lastCommitNotIgnored;
	private Supplier<RepositoryCoordinates> coordinatesSupplier;

	private ContextInitializer(Supplier<RepositoryCoordinates> coordinatesSupplier, Instant ignoredAfter) {
		this.ignoreAfter = requireNonNull(ignoredAfter);
		this.coordinatesSupplier = requireNonNull(coordinatesSupplier);
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
			client = Client.about(coordinatesSupplier.get());
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
		} catch (IllegalStateException | GitAPIException | IOException e) {
			throw new GradingException(e);
		}
	}

	@Override
	public Client getClient() {
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

}
