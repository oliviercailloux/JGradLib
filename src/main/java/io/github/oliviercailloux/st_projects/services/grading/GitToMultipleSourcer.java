package io.github.oliviercailloux.st_projects.services.grading;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;

import io.github.oliviercailloux.git.Client;
import io.github.oliviercailloux.st_projects.model.GitContext;
import io.github.oliviercailloux.st_projects.model.MultiContent;

public class GitToMultipleSourcer implements Supplier<MultiContent> {

	private static class MultiContentSupplier implements MultiContent {
		private ImmutableMap<Path, String> contents;

		public MultiContentSupplier(ImmutableMap<Path, String> contents) {
			this.contents = contents;
		}

		@Override
		public ImmutableMap<Path, String> getContents() {
			return contents;
		}
	}

	private final Supplier<? extends GitContext> contextSupplier;

	public static GitToMultipleSourcer satisfyingPath(Supplier<? extends GitContext> context,
			Predicate<Path> pathPredicate) {
		return new GitToMultipleSourcer(context, (f) -> pathPredicate.test(f.getPath()));
	}

	public static GitToMultipleSourcer satisfyingPathThenContent(Supplier<? extends GitContext> context,
			Predicate<Path> pathPredicate, Predicate<String> contentPredicate) {
		return new GitToMultipleSourcer(context,
				(f) -> pathPredicate.test(f.getPath()) && contentPredicate.test(f.getContent()));
	}

	public static GitToMultipleSourcer satisfyingOnContent(Supplier<? extends GitContext> context,
			Predicate<FileContent> predicate) {
		return new GitToMultipleSourcer(context, predicate);
	}

	private GitToMultipleSourcer(Supplier<? extends GitContext> context, Predicate<FileContent> predicate) {
		this.contextSupplier = requireNonNull(context);
		this.predicate = requireNonNull(predicate);
	}

	@Override
	public MultiContent get() {
		ImmutableMap<Path, String> contents = init(contextSupplier.get());
		assert contents != null;
		return new MultiContentSupplier(contents);
	}

	private ImmutableMap<Path, String> init(GitContext context) throws GradingException {
		final Client client = context.getClient();
		final FileCrawler fileCrawler = new FileCrawler(client);

		try {
			return initSources(fileCrawler);
		} catch (IOException e) {
			throw new GradingException(e);
		}
	}

	ImmutableMap<Path, String> initSources(FileCrawler fileCrawler)
			throws IOException, MissingObjectException, IncorrectObjectTypeException {
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
		return contentBuilder.build();
	}

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GitToMultipleSourcer.class);
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
