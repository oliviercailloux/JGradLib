package io.github.oliviercailloux.st_projects.services.grading;

import java.nio.file.Path;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;

import io.github.oliviercailloux.st_projects.model.GitContext;
import io.github.oliviercailloux.st_projects.model.GradingContexter;
import io.github.oliviercailloux.st_projects.model.MultiContent;

public class GitToTestSourcer implements GradingContexter, MultiContent {
	private final static Pattern HAS_JUNIT_TEST_CONTENT = Pattern
			.compile("(\\h*@Test)|(org\\.junit\\.jupiter\\.api\\.Assertions)");
	private final GitToMultipleSourcerOld delegate;

	private GitToTestSourcer(GitContext context) {
		delegate = GitToMultipleSourcerOld.satisfyingOnContent(context, this::isTestFile);
	}

	GitToMultipleSourcerOld getDelegate() {
		return delegate;
	}

	private boolean isTestFile(FileContent f) {
		final Path path = f.getPath();
		if (isSurefireTestFile(path)) {
			return true;
		}
		/**
		 * We must do it in two steps because we do not want to call getContent()
		 * unnecessarily.
		 */
		return HAS_JUNIT_TEST_CONTENT.matcher(f.getContent()).find();
	}

	public boolean isSurefireTestFile(Path path) {
		final boolean ok = path.startsWith("src/test/java")
				&& path.getFileName().toString().matches("(Test.*)|(.*Test)\\.java");
		LOGGER.debug("Testing whether is Surefire test file on {}: {}.", path, ok);
		return ok;
	}

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GitToTestSourcer.class);

	@Override
	public void clear() {
		delegate.clear();
	}

	@Override
	public ImmutableMap<Path, String> getContents() {
		return delegate.getContents();
	}

	@Override
	public void init() throws GradingException {
		delegate.init();
	}

	public static GitToTestSourcer testSourcer(GitContext context) {
		return new GitToTestSourcer(context);
	}

}
