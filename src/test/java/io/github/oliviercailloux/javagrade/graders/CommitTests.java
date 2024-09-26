package io.github.oliviercailloux.javagrade.graders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.graph.Graph;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import io.github.oliviercailloux.factogit.JGit;
import io.github.oliviercailloux.git.filter.GitHistorySimple;
import io.github.oliviercailloux.git.github.model.GitHubUsername;
import io.github.oliviercailloux.gitjfs.GitFileSystem;
import io.github.oliviercailloux.gitjfs.GitFileSystemProvider;
import io.github.oliviercailloux.gitjfs.GitPathRootSha;
import io.github.oliviercailloux.gitjfs.GitPathRootShaCached;
import io.github.oliviercailloux.grade.GitWork;
import io.github.oliviercailloux.grade.IGrade;
import io.github.oliviercailloux.grade.format.json.JsonGrade;
import io.github.oliviercailloux.jaris.graphs.GraphUtils;
import io.github.oliviercailloux.utils.Utils;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
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

class CommitTests {
  @SuppressWarnings("unused")
  private static final Logger LOGGER = LoggerFactory.getLogger(CommitTests.class);

  @Test
  void testEmpty() throws Exception {
    try (Repository repository = new InMemoryRepository(new DfsRepositoryDescription("myrepo"));
        GitFileSystem gitFs =
            GitFileSystemProvider.instance().newFileSystemFromRepository(repository)) {
      assertTrue(repository.getObjectDatabase().exists());
      assertFalse(repository.getRefDatabase().hasRefs());

      final GitHistorySimple empty = GitHistorySimple.create(gitFs, ImmutableMap.of());

      final IGrade direct = Commit.grade(GitWork.given(GitHubUsername.given("ploum"), empty));
      LOGGER.debug("Grade direct: {}.", JsonGrade.asJson(direct));
      assertEquals(0d, direct.getPoints());
    }

  }

  @Test
  void testAlmost() throws Exception {
    try (FileSystem jimFs = Jimfs.newFileSystem(Configuration.unix())) {
      final Path c1 = Files.createDirectories(jimFs.getPath("c1/"));
      final Path c2 = Files.createDirectories(jimFs.getPath("c2/"));
      final Path c3 = Files.createDirectories(jimFs.getPath("c3/"));
      final Path links = Files.createDirectories(jimFs.getPath("links/"));

      {
        Files.writeString(c1.resolve("afile.txt"), "coucou");
      }
      {
        Files.writeString(c2.resolve("afile.txt"), "coucou");
        Files.writeString(c2.resolve("myid.txt"), "222");
      }
      {
        Files.writeString(c3.resolve("afile.txt"), "coucou");
        Files.writeString(c3.resolve("myid.txt"), "222");
        Files.writeString(Files.createDirectories(c3.resolve("sub/a/")).resolve("another file.txt"),
            "coucou");
      }
      {
        final Path origin = Files.createDirectories(links.resolve(Constants.R_REMOTES + "origin/"));
        Files.createSymbolicLink(origin.resolve("coucou"), c1);
        Files.createSymbolicLink(origin.resolve("main"), c2);
        Files.createSymbolicLink(origin.resolve("dev"), c3);
      }

      final PersonIdent personIdent = new PersonIdent("Me", "email");

      try (
          Repository repository = JGit.createRepository(personIdent,
              Utils.asGraph(ImmutableList.of(c1, c2, c3)), links);
          GitFileSystem gitFs =
              GitFileSystemProvider.instance().newFileSystemFromRepository(repository)) {
        final Graph<ObjectId> graph = GraphUtils.transform(gitFs.graph(), p -> p.getCommit().id());
        final ImmutableSet<ObjectId> roots = graph.nodes().stream()
            .filter(n -> graph.predecessors(n).isEmpty()).collect(ImmutableSet.toImmutableSet());
        final ObjectId o1 = Iterables.getOnlyElement(roots);
        final ObjectId o2 = Iterables.getOnlyElement(graph.successors(o1));
        final ObjectId o3 = Iterables.getOnlyElement(graph.successors(o2));
        final Map<ObjectId, Instant> times =
            ImmutableMap.of(o1, Commit.DEADLINE.toInstant(), o2, Commit.DEADLINE.toInstant(), o3,
                Commit.DEADLINE.toInstant().plus(Duration.ofMinutes(4)));
        final GitHistorySimple withTimes = GitHistorySimple.create(gitFs, times);

        final IGrade direct =
            Commit.grade(GitWork.given(GitHubUsername.given("Not me"), withTimes));
        LOGGER.debug("Grade direct: {}.", JsonGrade.asJson(direct));
        assertEquals(0.85d, direct.getPoints(), 1e-5);
      }
    }

  }

  @Test
  void testPerfect() throws Exception {
    try (FileSystem jimFs = Jimfs.newFileSystem(Configuration.unix())) {
      final Path c1 = Files.createDirectories(jimFs.getPath("c1/"));
      final Path c2 = Files.createDirectories(jimFs.getPath("c2/"));
      final Path c3 = Files.createDirectories(jimFs.getPath("c3/"));
      final Path links = Files.createDirectories(jimFs.getPath("links/"));

      {
        Files.writeString(c1.resolve("afile.txt"), "coucou");
      }
      {
        Files.writeString(c2.resolve("afile.txt"), "coucou");
        Files.writeString(c2.resolve("myid.txt"), "222");
      }
      {
        Files.writeString(c3.resolve("afile.txt"), "coucou");
        Files.writeString(c3.resolve("myid.txt"), "222");
        Files.writeString(Files.createDirectories(c3.resolve("sub/a/")).resolve("another file.txt"),
            "coucou");
      }
      {
        final Path origin = Files.createDirectories(links.resolve(Constants.R_REMOTES + "origin/"));
        Files.createSymbolicLink(origin.resolve("coucou"), c1);
        Files.createSymbolicLink(origin.resolve("main"), c2);
        Files.createSymbolicLink(origin.resolve("dev"), c3);
      }

      final PersonIdent personIdent = new PersonIdent("Me", "email");

      try (Repository repository =
          JGit.createRepository(personIdent, Utils.asGraph(ImmutableList.of(c1, c2, c3)), links)) {
        try (GitFileSystem gitFs =
            GitFileSystemProvider.instance().newFileSystemFromRepository(repository)) {
          final Graph<GitPathRootShaCached> graph =
              GraphUtils.transform(gitFs.graph(), GitPathRootSha::toShaCached);
          final ImmutableSet<ObjectId> ids = graph.nodes().stream()
              .map(GitPathRootShaCached::getCommit).map(io.github.oliviercailloux.gitjfs.Commit::id)
              .collect(ImmutableSet.toImmutableSet());
          final Map<ObjectId, Instant> constantTimes =
              Maps.asMap(ids, o -> Commit.DEADLINE.toInstant());
          final GitHistorySimple withConstantTimes = GitHistorySimple.create(gitFs, constantTimes);

          final IGrade direct =
              Commit.grade(GitWork.given(GitHubUsername.given("Me"), withConstantTimes));
          LOGGER.debug("Grade direct: {}.", JsonGrade.asJson(direct));
          assertEquals(1.0d, direct.getPoints());
        }
      }
    }

  }
}
