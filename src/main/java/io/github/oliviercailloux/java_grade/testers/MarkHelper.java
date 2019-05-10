package io.github.oliviercailloux.java_grade.testers;

import static com.google.common.base.Preconditions.checkState;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.nio.file.Path;
import java.util.regex.Pattern;

import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.management.UnixOperatingSystemMXBean;

import io.github.oliviercailloux.git.FileContent;
import io.github.oliviercailloux.grade.GradingException;
import io.github.oliviercailloux.grade.context.FilesSource;

public class MarkHelper {

	private final static Pattern HAS_JUNIT_TEST_CONTENT = Pattern
			.compile("(\\h*@Test)|(org\\.junit\\.jupiter\\.api\\.Assertions)");

	public static boolean isSurefireTestFile(Path path) {
		final boolean ok = path.startsWith("src/test/java")
				&& path.getFileName().toString().matches("(Test.*)|(.*Test)\\.java");
		LOGGER.debug("Testing whether is Surefire test file on {}: {}.", path, ok);
		return ok;
	}

	public static boolean isTestFile(FileContent f) {
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

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(MarkHelper.class);

	public static FilesSource getTestFiles(FilesSource f) {
		return f.filter(MarkHelper::isTestFile);
	}

	/**
	 * Require a Unix-like operating system.
	 */
	public static int getOpenFileDescriptorCount() {
		final OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
		checkState(os instanceof UnixOperatingSystemMXBean);
		final UnixOperatingSystemMXBean os2 = (UnixOperatingSystemMXBean) os;
		return Math.toIntExact(os2.getOpenFileDescriptorCount());
	}

	public static boolean committerIsGitHub(RevCommit commit) {
		final PersonIdent author = commit.getAuthorIdent();
		final PersonIdent committer = commit.getCommitterIdent();
		final boolean committerIsGitHub = committer.getName().equals("GitHub");
		if (!committerIsGitHub && !author.getName().equals(committer.getName())) {
			throw new GradingException(String.format("Author: %s; Committer: %s.", author, committer));
		}
		return committerIsGitHub;
	}
}
