package io.github.oliviercailloux.javagrade.graders;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.graph.Graph;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.MutableGraph;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import io.github.oliviercailloux.factogit.JGit;
import io.github.oliviercailloux.git.filter.GitHistorySimple;
import io.github.oliviercailloux.git.github.model.GitHubUsername;
import io.github.oliviercailloux.gitjfs.GitFileSystem;
import io.github.oliviercailloux.gitjfs.GitFileSystemProvider;
import io.github.oliviercailloux.gitjfs.GitPathRootShaCached;
import io.github.oliviercailloux.grade.GitWork;
import io.github.oliviercailloux.grade.IGrade;
import io.github.oliviercailloux.grade.format.json.JsonGrade;
import io.github.oliviercailloux.javagrade.graders.Commit;
import io.github.oliviercailloux.javagrade.graders.GitBranching;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GitBranchingTests {

  @SuppressWarnings("unused")
  private static final Logger LOGGER = LoggerFactory.getLogger(GitBranchingTests.class);

  @Test
  void testEmpty() throws Exception {
    try (Repository repository = new InMemoryRepository(new DfsRepositoryDescription("myrepo"));
        GitFileSystem gitFs =
            GitFileSystemProvider.instance().newFileSystemFromRepository(repository)) {

      final GitHistorySimple empty = GitHistorySimple.create(gitFs, ImmutableMap.of());

      final IGrade grade =
          new GitBranching().grade(GitWork.given(GitHubUsername.given("ploum"), empty));
      LOGGER.debug("Grade: {}.", JsonGrade.asJson(grade));
      assertEquals(0d, grade.getPoints());
    }

  }

  @Test
  void testBad() throws Exception {
    try (FileSystem jimFs = Jimfs.newFileSystem(Configuration.unix())) {
      final Path a = Files.createDirectories(jimFs.getPath("a/"));
      final Path spurious = Files.createDirectories(jimFs.getPath("spurious/"));
      final Path b = Files.createDirectories(jimFs.getPath("b/"));
      final Path c = Files.createDirectories(jimFs.getPath("c/"));
      final Path d = Files.createDirectories(jimFs.getPath("d/"));
      final Path start = Files.createDirectories(jimFs.getPath("start/"));
      final Path links = Files.createDirectories(jimFs.getPath("links/"));
      final MutableGraph<Path> baseDirs = GraphBuilder.directed().allowsSelfLoops(false).build();
      baseDirs.putEdge(start, a);
      baseDirs.putEdge(start, spurious);
      baseDirs.putEdge(a, d);
      baseDirs.putEdge(spurious, b);
      baseDirs.putEdge(b, c);
      baseDirs.putEdge(c, d);

      {
        Files.writeString(a.resolve("first.txt"), "wrong");
      }
      {
        Files.writeString(b.resolve("first.txt"), "coucou monde");
      }
      {
        Files.writeString(c.resolve("first.txt"), "wrong again");
        Files.writeString(Files.createDirectories(c.resolve("a/b/c/x/z/")).resolve("some file.txt"),
            "2021");
      }
      {
        Files.writeString(d.resolve("first.txt"), "wrong, wrong and wrong");
        Files.writeString(Files.createDirectories(d.resolve("a/b/c/x/z/")).resolve("some file.txt"),
            "2021");
      }
      {
        final Path origin = Files.createDirectories(links.resolve(Constants.R_REMOTES + "origin/"));
        Files.createSymbolicLink(origin.resolve("br1"), a);
        Files.createSymbolicLink(origin.resolve("brWrong"), c);
        Files.createSymbolicLink(origin.resolve("brNOT"), d);
      }

      final PersonIdent personIdent = new PersonIdent("Me", "email");

      try (Repository repository = JGit.createRepository(personIdent, baseDirs, links)) {
        try (GitFileSystem gitFs =
            GitFileSystemProvider.instance().newFileSystemFromRepository(repository)) {
          final Graph<GitPathRootShaCached> graph = gitFs.graph();
          final ImmutableSet<ObjectId> ids = graph.nodes().stream()
              .map(GitPathRootShaCached::getCommit).map(io.github.oliviercailloux.gitjfs.Commit::id)
              .collect(ImmutableSet.toImmutableSet());
          final Map<ObjectId, Instant> constantTimes =
              Maps.asMap(ids, o -> Commit.DEADLINE.toInstant());
          final GitHistorySimple withConstantTimes = GitHistorySimple.create(gitFs, constantTimes);

          final IGrade grade = new GitBranching()
              .grade(GitWork.given(GitHubUsername.given("Not me"), withConstantTimes));
          LOGGER.info("Grade: {}.", JsonGrade.asJson(grade));
          assertEquals(0.5d, grade.getPoints(), 1e-5d);
        }
      }
    }

  }

  @Test
  void testPerfect() throws Exception {
    try (FileSystem jimFs = Jimfs.newFileSystem(Configuration.unix())) {
      final Path a = Files.createDirectories(jimFs.getPath("a/"));
      final Path b = Files.createDirectories(jimFs.getPath("b/"));
      final Path c = Files.createDirectories(jimFs.getPath("c/"));
      final Path d = Files.createDirectories(jimFs.getPath("d/"));
      final Path start = Files.createDirectories(jimFs.getPath("start/"));
      final Path links = Files.createDirectories(jimFs.getPath("links/"));
      final MutableGraph<Path> baseDirs = GraphBuilder.directed().allowsSelfLoops(false).build();
      baseDirs.putEdge(start, a);
      baseDirs.putEdge(start, b);
      baseDirs.putEdge(a, d);
      baseDirs.putEdge(b, c);
      baseDirs.putEdge(c, d);

      {
        Files.writeString(a.resolve("first.txt"), "hello world");
      }
      {
        Files.writeString(b.resolve("first.txt"), "coucou monde");
      }
      {
        Files.writeString(c.resolve("first.txt"), "coucou monde");
        Files.writeString(Files.createDirectories(c.resolve("a/b/c/x/z/")).resolve("some file.txt"),
            "2021");
      }
      {
        Files.writeString(d.resolve("first.txt"), "hello world\ncoucou monde");
        Files.writeString(Files.createDirectories(d.resolve("a/b/c/x/z/")).resolve("some file.txt"),
            "2021");
      }
      {
        final Path origin = Files.createDirectories(links.resolve(Constants.R_REMOTES + "origin/"));
        Files.createSymbolicLink(origin.resolve("br1"), a);
        Files.createSymbolicLink(origin.resolve("br2"), c);
        Files.createSymbolicLink(origin.resolve("br3"), d);
      }

      final PersonIdent personIdent = new PersonIdent("Me", "email");

      try (Repository repository = JGit.createRepository(personIdent, baseDirs, links)) {
        try (GitFileSystem gitFs =
            GitFileSystemProvider.instance().newFileSystemFromRepository(repository)) {
          final Graph<GitPathRootShaCached> graph = gitFs.graph();
          final ImmutableSet<ObjectId> ids = graph.nodes().stream()
              .map(GitPathRootShaCached::getCommit).map(io.github.oliviercailloux.gitjfs.Commit::id)
              .collect(ImmutableSet.toImmutableSet());
          final Map<ObjectId, Instant> constantTimes =
              Maps.asMap(ids, o -> Commit.DEADLINE.toInstant());
          final GitHistorySimple withConstantTimes = GitHistorySimple.create(gitFs, constantTimes);

          final IGrade grade = new GitBranching()
              .grade(GitWork.given(GitHubUsername.given("Me"), withConstantTimes));
          LOGGER.debug("Grade direct: {}.", JsonGrade.asJson(grade));
          assertEquals(1.0d, grade.getPoints());
        }
      }
    }

  }
}
