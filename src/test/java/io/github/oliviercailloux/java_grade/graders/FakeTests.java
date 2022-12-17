package io.github.oliviercailloux.java_grade.graders;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.graph.ImmutableGraph;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import io.github.oliviercailloux.git.GitHistory;
import io.github.oliviercailloux.git.GitUtils;
import io.github.oliviercailloux.git.git_hub.model.GitHubUsername;
import io.github.oliviercailloux.gitjfs.impl.GitFileSystemImpl;
import io.github.oliviercailloux.gitjfs.impl.GitFileSystemProviderImpl;
import io.github.oliviercailloux.grade.BatchGitHistoryGrader;
import io.github.oliviercailloux.grade.Exam;
import io.github.oliviercailloux.grade.GitFileSystemHistory;
import io.github.oliviercailloux.grade.StaticFetcher;
import io.github.oliviercailloux.grade.format.HtmlGrades;
import io.github.oliviercailloux.grade.format.json.JsonSimpleGrade;
import io.github.oliviercailloux.jgit.JGit;
import io.github.oliviercailloux.utils.Utils;
import io.github.oliviercailloux.xml.XmlUtils;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class FakeTests {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(FakeTests.class);
	private static final GitHubUsername USERNAME = GitHubUsername.given("user");

	@Test
	void testEmpty() throws Exception {
		try (Repository repository = new InMemoryRepository(new DfsRepositoryDescription("myrepo"));
				GitFileSystemImpl gitFs = GitFileSystemProviderImpl.getInstance().newFileSystemFromRepository(repository)) {

			final GitFileSystemHistory gitH = GitFileSystemHistory.create(gitFs, GitUtils.getHistory(gitFs));
			final BatchGitHistoryGrader<RuntimeException> batchGrader = BatchGitHistoryGrader
					.given(() -> StaticFetcher.single(USERNAME, gitH));

			final ZonedDateTime nowTime = ZonedDateTime.parse("2022-01-01T10:00:00+01:00[Europe/Paris]");
			final Exam exam = batchGrader.getGrades(nowTime.plus(30, ChronoUnit.MINUTES),
					Duration.of(1, ChronoUnit.HOURS), new Fake(), 0.1d);

			assertEquals(ImmutableSet.of(USERNAME), exam.getUsernames());
			assertEquals(0d, exam.getGrade(USERNAME).mark().getPoints());
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
				Files.writeString(c1.resolve("Some file.txt"), "A file!\n");
				Files.writeString(Files.createDirectories(c1.resolve("Some folder/")).resolve("Another file.txt"),
						"More content!\n");
			}
			{
				Files.writeString(c2.resolve("Some file.txt"), "A file!\nMore content!\n");
				Files.writeString(Files.createDirectories(c2.resolve("Some folder/")).resolve("Another file.txt"),
						"More content!\n");
			}
			{
				Files.writeString(c3.resolve("Some file.txt"), "A file!\nMore content!\n");
			}
			{
				final Path origin = Files.createDirectories(links.resolve(Constants.R_REMOTES + "origin/"));
				Files.createSymbolicLink(origin.resolve("main"), c3);
			}

			final PersonIdent personIdent = new PersonIdent(USERNAME.getUsername(), "email");

			final ImmutableGraph<Path> graph = Utils.asGraph(ImmutableList.of(c1, c2, c3));
			try (Repository repository = JGit.createRepository(personIdent, graph, links)) {
				try (GitFileSystemImpl gitFs = GitFileSystemProviderImpl.getInstance()
						.newFileSystemFromRepository(repository)) {
					final GitHistory history = GitUtils.getHistory(gitFs);
					final GitFileSystemHistory gitH = GitFileSystemHistory.create(gitFs, history);
					final BatchGitHistoryGrader<RuntimeException> batchGrader = BatchGitHistoryGrader
							.given(() -> StaticFetcher.single(USERNAME, gitH));
					final Exam exam = batchGrader.getGrades(BatchGitHistoryGrader.MAX_DEADLINE, Duration.ofMinutes(0),
							new Fake(), 0.1d);

					Files.writeString(Path.of("test grades.json"), JsonSimpleGrade.toJson(exam));
					Files.writeString(Path.of("test grades.html"), XmlUtils
							.asString(HtmlGrades.asHtml(exam.getGrade(USERNAME), "fake " + Instant.now(), 20d)));
					assertEquals(ImmutableSet.of(USERNAME), exam.getUsernames());
					assertEquals(1d, exam.getGrade(USERNAME).mark().getPoints());
				}
			}
		}

	}

}
