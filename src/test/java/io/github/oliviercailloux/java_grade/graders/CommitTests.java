package io.github.oliviercailloux.java_grade.graders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.ImmutableGraph;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;

import io.github.oliviercailloux.git.GitHistory;
import io.github.oliviercailloux.git.GitUtils;
import io.github.oliviercailloux.git.JGit;
import io.github.oliviercailloux.git.fs.GitFileSystem;
import io.github.oliviercailloux.git.fs.GitFileSystemProvider;
import io.github.oliviercailloux.grade.GitFileSystemHistory;
import io.github.oliviercailloux.grade.GitGeneralGrader;
import io.github.oliviercailloux.grade.IGrade;
import io.github.oliviercailloux.grade.format.json.JsonGrade;

class CommitTests {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(CommitTests.class);

	@Test
	void testEmpty() throws Exception {
		try (Repository repository = new InMemoryRepository(new DfsRepositoryDescription("myrepo"));
				GitFileSystem gitFs = GitFileSystemProvider.getInstance().newFileSystemFromRepository(repository)) {
			assertTrue(repository.getObjectDatabase().exists());
			assertFalse(repository.getRefDatabase().hasRefs());

			final GitFileSystemHistory empty = GitFileSystemHistory.create(gitFs,
					GitHistory.create(GraphBuilder.directed().build(), ImmutableMap.of()));
			final IGrade grade = GitGeneralGrader.grade(empty, Commit.DEADLINE, "ploum", new Commit());
			LOGGER.debug("Grade: {}.", JsonGrade.asJson(grade));
			assertEquals(0d, grade.getPoints());

			final IGrade direct = new Commit().grade(empty, "ploum");
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
				Files.writeString(Files.createDirectories(c3.resolve("sub/a/")).resolve("another file.txt"), "coucou");
			}
			{
				Files.createSymbolicLink(links.resolve("coucou"), c1);
				Files.createSymbolicLink(links.resolve("main"), c2);
				Files.createSymbolicLink(links.resolve("dev"), c3);
			}

			final PersonIdent personIdent = new PersonIdent("Me", "email");

			try (Repository repository = JGit.createRepository(personIdent, ImmutableList.of(c1, c2, c3), links);
					GitFileSystem gitFs = GitFileSystemProvider.getInstance().newFileSystemFromRepository(repository)) {
				final GitHistory history = GitUtils.getHistory(gitFs);
				final ImmutableGraph<ObjectId> graph = history.getGraph();
				final ObjectId o1 = Iterables.getOnlyElement(history.getRoots());
				final ObjectId o2 = Iterables.getOnlyElement(graph.successors(o1));
				final ObjectId o3 = Iterables.getOnlyElement(graph.successors(o2));
				final Map<ObjectId, Instant> times = ImmutableMap.of(o1, Commit.DEADLINE.toInstant(), o2,
						Commit.DEADLINE.toInstant(), o3, Commit.DEADLINE.toInstant().plus(Duration.ofMinutes(4)));
				final GitFileSystemHistory withTimes = GitFileSystemHistory.create(gitFs,
						GitHistory.create(graph, times));

				final IGrade direct = new Commit().grade(withTimes, "Not me");
				LOGGER.debug("Grade direct: {}.", JsonGrade.asJson(direct));
				assertEquals(0.45d, direct.getPoints(), 1e-5);

				final IGrade grade = GitGeneralGrader.grade(withTimes, Commit.DEADLINE, "Not me", new Commit());
				LOGGER.info("Grade: {}.", JsonGrade.asJson(grade));
				assertEquals(0.35d, grade.getPoints(), 1e-5d);
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
				Files.writeString(Files.createDirectories(c3.resolve("sub/a/")).resolve("another file.txt"), "coucou");
			}
			{
				Files.createSymbolicLink(links.resolve("coucou"), c1);
				Files.createSymbolicLink(links.resolve("main"), c2);
				Files.createSymbolicLink(links.resolve("dev"), c3);
			}

			final PersonIdent personIdent = new PersonIdent("Me", "email");

			try (Repository repository = JGit.createRepository(personIdent, ImmutableList.of(c1, c2, c3), links)) {
				try (GitFileSystem gitFs = GitFileSystemProvider.getInstance()
						.newFileSystemFromRepository(repository)) {
					final ImmutableGraph<ObjectId> graph = GitUtils.getHistory(gitFs).getGraph();
					final Map<ObjectId, Instant> constantTimes = Maps.asMap(graph.nodes(),
							o -> Commit.DEADLINE.toInstant());
					final GitFileSystemHistory withConstantTimes = GitFileSystemHistory.create(gitFs,
							GitHistory.create(graph, constantTimes));

					final IGrade grade = GitGeneralGrader.grade(withConstantTimes, Commit.DEADLINE, "Me", new Commit());
					LOGGER.debug("Grade: {}.", JsonGrade.asJson(grade));
					/**
					 * TODO make it so that we can work around the problem that my branches are
					 * local, whereas the correction looks for remote branches.
					 */
					assertEquals(0.6d, grade.getPoints());

					final IGrade direct = new Commit().grade(withConstantTimes, "Me");
					LOGGER.debug("Grade direct: {}.", JsonGrade.asJson(direct));
					assertEquals(0.6d, direct.getPoints());
				}
			}
		}

	}

}
