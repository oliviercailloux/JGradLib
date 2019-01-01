package io.github.oliviercailloux.st_projects.services.grading;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.function.Predicate;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;

import io.github.oliviercailloux.git.Client;
import io.github.oliviercailloux.st_projects.model.GitContext;
import io.github.oliviercailloux.st_projects.model.GradingContexter;
import io.github.oliviercailloux.st_projects.model.MultiContentSupplier;

public class GitToMultipleSourcer implements GradingContexter, MultiContentSupplier {

	private final GitContext context;
	private ImmutableMap<Path, String> contents;

	public static GitToMultipleSourcer satisfyingPath(GitContext context, Predicate<Path> pathPredicate) {
		return new GitToMultipleSourcer(context, (f) -> pathPredicate.test(f.getPath()));
	}

	public static GitToMultipleSourcer satisfyingPathThenContent(GitContext context, Predicate<Path> pathPredicate,
			Predicate<String> contentPredicate) {
		return new GitToMultipleSourcer(context,
				(f) -> pathPredicate.test(f.getPath()) && contentPredicate.test(f.getContent()));
	}

	public static GitToMultipleSourcer satisfyingOnContent(GitContext context, Predicate<FileContent> predicate) {
		return new GitToMultipleSourcer(context, predicate);
	}

	private GitToMultipleSourcer(GitContext context, Predicate<FileContent> predicate) {
		this.context = requireNonNull(context);
		this.predicate = requireNonNull(predicate);
		clear();
	}

	@Override
	public ImmutableMap<Path, String> getContents() {
		assert contents != null;
		return contents;
	}

	@Override
	public void clear() {
		contents = null;
	}

	@Override
	public void init() throws GradingException {
		final Client client = context.getClient();
		final FileCrawler fileCrawler = new FileCrawler(client);

		try {
			initSources(fileCrawler);
		} catch (IOException e) {
			throw new GradingException(e);
		}
	}

	void initSources(FileCrawler fileCrawler) throws IOException, MissingObjectException, IncorrectObjectTypeException {
		final Set<Path> allPaths = fileCrawler.getRecursively(Paths.get(""));

		final ImmutableMap.Builder<Path, String> contentBuilder = ImmutableMap.builder();

		for (Path path : allPaths) {
			assert !path.toString().equals("");
			final FileContent fileContent = getAsFileContent(fileCrawler, path);
			final boolean test = predicate.test(fileContent);
			LOGGER.debug("Testing against {}: {}.", fileContent.getPath(), test);
			if (test) {
				contentBuilder.put(path, fileContent.getContent());
			}
		}
		contents = contentBuilder.build();
	}

	@SuppressWarnings("unused")
	static final Logger LOGGER = LoggerFactory.getLogger(GitToMultipleSourcer.class);
	private final Predicate<FileContent> predicate;

	static FileContent getAsFileContent(FileCrawler crawler, Path path) {
		checkArgument(!path.toString().equals(""));
		return new FileContent() {
			private String content = null;

			@Override
			public Path getPath() {
				return path;
			}

			@Override
			public String getContent() throws GradingException {
				if (content == null) {
					try {
						content = crawler.getFileContent(path);
					} catch (IOException e) {
						throw new GradingException(e);
					}
				}
				return content;
			}
		};
	}
}
