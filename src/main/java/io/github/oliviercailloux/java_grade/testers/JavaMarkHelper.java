package io.github.oliviercailloux.java_grade.testers;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.sun.management.UnixOperatingSystemMXBean;
import io.github.oliviercailloux.gitjfs.Commit;
import io.github.oliviercailloux.gitjfs.GitPathRoot;
import io.github.oliviercailloux.gitjfs.GitPathRootShaCached;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JavaMarkHelper {

  @SuppressWarnings("unused")
  private static final Logger LOGGER = LoggerFactory.getLogger(JavaMarkHelper.class);

  final static Pattern HAS_JUNIT_TEST_CONTENT =
      Pattern.compile("(\\h*@Test)|(org\\.junit\\.jupiter\\.api\\.Assertions)");

  public static final String GIT_HUB_CLASSROOM_COMMITTER = "github-classroom[bot]";

  public static final String GIT_HUB_COMMITTER = "GitHub";

  public static boolean isSurefireTestFile(Path path) {
    final boolean ok = path.startsWith("src/test/java")
        && path.getFileName().toString().matches("(Test.*)|(.*Test)\\.java");
    LOGGER.debug("Testing whether is Surefire test file on {}: {}.", path, ok);
    return ok;
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
   * When the GitHub GUI is used, the committer is set to GitHub <noreply@github.com> while the
   * author seems to be the logged user.
   */
  public static boolean committerIsGitHub(Commit commit) {
    return commit.committerName().equals(GIT_HUB_COMMITTER)
        || commit.committerName().equals(GIT_HUB_CLASSROOM_COMMITTER);
  }

  /**
   * When the GitHub GUI is used, the committer is set to GitHub <noreply@github.com> while the
   * author seems to be the logged user.
   *
   */
  public static boolean committerIsGitHub(GitPathRoot commit)
      throws NoSuchFileException, IOException {
    return committerIsGitHub(commit.getCommit());
  }

  /**
   * When the GitHub GUI is used, the committer is set to GitHub <noreply@github.com> while the
   * author seems to be the logged user.
   *
   */
  public static boolean committerIsGitHub(GitPathRootShaCached commit) {
    return committerIsGitHub(commit.getCommit());
  }

  /**
   * When the GitHub GUI is used, the committer is set to GitHub <noreply@github.com> while the
   * author seems to be the logged user.
   */
  public static boolean committerIsGitHub(RevCommit commit) {
    return commit.getCommitterIdent().getName().equals("GitHub");
  }

  public static boolean committerAndAuthorIs(Commit commit, String name) {
    checkNotNull(name);
    final boolean committerIsRight = commit.committerName().equals(name);
    final boolean authorIsRight = commit.authorName().equals(name);
    return committerIsRight && authorIsRight;
  }

  public static boolean committerAndAuthorIs(RevCommit commit, String name) {
    checkNotNull(name);
    final boolean committerIsRight = commit.getCommitterIdent().getName().equals(name);
    final boolean authorIsRight = commit.getAuthorIdent().getName().equals(name);
    return committerIsRight && authorIsRight;
  }

  public static boolean committerAndAuthorIs(GitPathRoot commit, String name)
      throws NoSuchFileException, IOException {
    checkNotNull(name);
    final boolean committerIsRight = commit.getCommit().committerName().equals(name);
    final boolean authorIsRight = commit.getCommit().authorName().equals(name);
    return committerIsRight && authorIsRight;
  }

  public static String getContentOrEmpty(Path path) {
    try {
      return Files.readString(path);
    } catch (@SuppressWarnings("unused") NoSuchFileException e) {
      return "";
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    // if (!Files.exists(path)) {
    // return "";
    // }
    // return Utils.getOrThrow(() -> Files.readString(path));
  }
}
