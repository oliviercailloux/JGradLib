package io.github.oliviercailloux.grade.contexters;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;

import io.github.oliviercailloux.git.Client;
import io.github.oliviercailloux.git.FileContent;
import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinates;
import io.github.oliviercailloux.grade.GradingException;
import io.github.oliviercailloux.grade.context.FilesReader;
import io.github.oliviercailloux.grade.context.GitFullContext;
import io.github.oliviercailloux.grade.context.GradingContextWithTimeline;
import io.github.oliviercailloux.grade.context.MultiContent;

public class ContextInitializer implements GitFullContext {

	public static ContextInitializer with(RepositoryCoordinates coordinatesSupplier) {
		final String tmpDir = System.getProperty("java.io.tmpdir");
		return new ContextInitializer(coordinatesSupplier, Paths.get(tmpDir), Instant.MAX);
	}

	public static GitFullContext withPathAndIgnoreAndInit(RepositoryCoordinates coordinatesSupplier,
			Path projectsBaseDir, Instant ignoreAfter) {
		final ContextInitializer ci = new ContextInitializer(coordinatesSupplier, projectsBaseDir, ignoreAfter);
		ci.init();
		return ci;
	}

	public static ContextInitializer withIgnore(RepositoryCoordinates coordinatesSupplier, Instant ignoreAfter) {
		final String tmpDir = System.getProperty("java.io.tmpdir");
		return new ContextInitializer(coordinatesSupplier, Paths.get(tmpDir), ignoreAfter);
	}

	private Client client;
	private Instant ignoreAfter;
	private GradingContextWithTimeline context;
	private Optional<RevCommit> lastCommitNotIgnored;
	private RepositoryCoordinates coordinatesSupplier;
	private Path projectsBaseDir;

	private ContextInitializer(RepositoryCoordinates coordinatesSupplier, Path projectsBaseDir, Instant ignoredAfter) {
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

	String fetchContentFromClient(Path relativePath) throws GradingException {
		requireNonNull(relativePath);
		final String content;
		try {
			content = getClient().fetchBlobOrEmpty(relativePath);
		} catch (IOException e) {
			throw new GradingException(e);
		}
		return content;
	}

	public static ContextInitializer withPathAndIgnore(RepositoryCoordinates coordinatesSupplier, Path projectsBaseDir,
			Instant ignoreAfter) {
		return new ContextInitializer(coordinatesSupplier, projectsBaseDir, ignoreAfter);
	}

	@Override
	public FilesReader getFilesReader(RevCommit sourceCommit) {
		/**
		 * TODO (priority!) cache myself, instead of caching in the client; use the
		 * client more directly, make client immutable.
		 */
		return new FilesReader() {
			@Override
			public MultiContent getMultiContent(Predicate<FileContent> predicate) throws GradingException {
				return GitToMultipleSourcer.satisfyingOnContent(ContextInitializer.this, predicate);
			}

			@Override
			public String getContent(Path relativePath) throws GradingException {
				return fetchContentFromClient(relativePath);
			}
		};
	}

}
