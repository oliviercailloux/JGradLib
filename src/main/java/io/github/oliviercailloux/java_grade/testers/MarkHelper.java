package io.github.oliviercailloux.java_grade.testers;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.management.UnixOperatingSystemMXBean;

import io.github.oliviercailloux.git.FileContent;
import io.github.oliviercailloux.grade.context.FilesSource;
import io.github.oliviercailloux.utils.Utils;

public class MarkHelper {

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(MarkHelper.class);

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

	/**
	 * When the GitHub GUI is used, the committer is set to GitHub
	 * <noreply@github.com> while the author seems to be the logged user.
	 */
	public static boolean committerIsGitHub(RevCommit commit) {
		return commit.getCommitterIdent().getName().equals("GitHub");
	}

	public static boolean committerAndAuthorIs(RevCommit commit, String name) {
		checkNotNull(name);
		final boolean committerIsRight = commit.getCommitterIdent().getName().equals(name);
		final boolean authorIsRight = commit.getAuthorIdent().getName().equals(name);
		return committerIsRight && authorIsRight;
	}

	public static String getContentOrEmpty(Path path) {
		if (!Files.exists(path)) {
			return "";
		}
		return Utils.getOrThrowIO(() -> Files.readString(path));
	}
}
