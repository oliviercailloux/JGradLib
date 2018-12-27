package io.github.oliviercailloux.st_projects.services.grading;

import static com.google.common.base.Preconditions.checkState;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import org.eclipse.jgit.api.errors.CheckoutConflictException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;

import io.github.oliviercailloux.git.Client;
import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinates;
import io.github.oliviercailloux.st_projects.ex2.GitAndGitHub;
import io.github.oliviercailloux.st_projects.model.GitContext;
import io.github.oliviercailloux.st_projects.model.GradingContextWithTimeline;

public class ContextInitializer implements GitContext {

	public static ContextInitializer ignoreAfter(Instant ignoreAfter) {
		return new ContextInitializer(ignoreAfter);
	}

	public static ContextInitializer noIgnore() {
		return new ContextInitializer(Instant.MAX);
	}

	private Client client;
	private Instant ignoreAfter;
	private GradingContextWithTimeline context;
	private Optional<RevCommit> lastCommitNotIgnored;

	private ContextInitializer(Instant ignoredAfter) {
		this.ignoreAfter = ignoredAfter;
		clear();
	}

	public void clear() {
		client = null;
		context = null;
		lastCommitNotIgnored = null;
	}

	public void init(RepositoryCoordinates coordinates) throws GitAPIException, IOException, CheckoutConflictException {
		clear();

		client = Client.about(coordinates);
		{
			client.tryRetrieve();
			client.hasContent();
			client.getWholeHistory();
		}
		if (client.hasContentCached()) {
			final Map<ObjectId, Instant> receivedAt = new GitAndGitHub().check(client);
			context = GradingContextWithTimeline.given(client, receivedAt);
			lastCommitNotIgnored = context.getLatestNotIgnoredChildOf(client.getCommit(client.resolve("origin/master")),
					ignoreAfter);
		} else {
			lastCommitNotIgnored = Optional.empty();
		}
		if (lastCommitNotIgnored.isPresent()) {
			client.checkout(lastCommitNotIgnored.get());
			client.setDefaultRevSpec(lastCommitNotIgnored.get());
		}
	}

	@Override
	public Client getClient() {
		return client;
	}

	public Instant getIgnoredAfter() {
		return ignoreAfter;
	}

	@Override
	public Optional<RevCommit> getMainCommit() {
		return lastCommitNotIgnored;
	}

	public Instant getSubmittedTime() {
		checkState(lastCommitNotIgnored.isPresent());
		return context.getCommitsReceptionTime().get(lastCommitNotIgnored.get());
	}

}
