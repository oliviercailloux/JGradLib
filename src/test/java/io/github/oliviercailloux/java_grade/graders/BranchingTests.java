package io.github.oliviercailloux.java_grade.graders;

import static com.google.common.base.Verify.verify;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.MoreCollectors;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.ImmutableGraph;
import com.google.common.graph.MutableGraph;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import io.github.oliviercailloux.git.GitHistory;
import io.github.oliviercailloux.git.GitUtils;
import io.github.oliviercailloux.git.git_hub.model.GitHubUsername;
import io.github.oliviercailloux.gitjfs.impl.GitFileSystemImpl;
import io.github.oliviercailloux.gitjfs.impl.GitFileSystemProviderImpl;
import io.github.oliviercailloux.gitjfs.impl.GitPathRootImpl;
import io.github.oliviercailloux.grade.BatchGitHistoryGrader;
import io.github.oliviercailloux.grade.Exam;
import io.github.oliviercailloux.grade.GitFileSystemHistory;
import io.github.oliviercailloux.grade.StaticFetcher;
import io.github.oliviercailloux.grade.format.HtmlGrades;
import io.github.oliviercailloux.grade.format.json.JsonSimpleGrade;
import io.github.oliviercailloux.jgit.JGit;
import io.github.oliviercailloux.xml.XmlUtils;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Iterator;
import java.util.Set;
import java.util.TimeZone;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class BranchingTests {
	public static PersonIdent personIdent(String username, String email, ZonedDateTime startTime) {
		return new PersonIdent(username, email, Date.from(startTime.toInstant()),
				TimeZone.getTimeZone(startTime.getZone()));
	}

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(BranchingTests.class);
	private static final GitHubUsername USERNAME = GitHubUsername.given("user");

	@Test
	void testEmpty() throws Exception {
		try (Repository repository = new InMemoryRepository(new DfsRepositoryDescription("myrepo"));
				GitFileSystemImpl gitFs = GitFileSystemProviderImpl.getInstance().newFileSystemFromRepository(repository)) {

			final GitFileSystemHistory gitH = GitFileSystemHistory.create(gitFs, GitUtils.getHistory(gitFs),
					ImmutableMap.of());
			final BatchGitHistoryGrader<RuntimeException> batchGrader = BatchGitHistoryGrader
					.given(() -> StaticFetcher.single(USERNAME, gitH));

			final ZonedDateTime nowTime = ZonedDateTime.parse("2022-01-01T10:00:00+01:00[Europe/Paris]");
			final Exam exam = batchGrader.getGrades(nowTime.plus(30, ChronoUnit.MINUTES),
					Duration.of(1, ChronoUnit.HOURS), new Branching(), 0.1d);

			assertEquals(ImmutableSet.of(USERNAME), exam.getUsernames());
			assertEquals(0d, exam.getGrade(USERNAME).mark().getPoints());
		}
	}

	@Test
	void testLateBranch() throws Exception {
		try (FileSystem jimFs = Jimfs.newFileSystem(Configuration.unix())) {
			final Path start = Files.createDirectories(jimFs.getPath("c1/"));
			final Path cA = Files.createDirectories(jimFs.getPath("c2/"));
			final Path cB = Files.createDirectories(jimFs.getPath("c3/"));
			final Path cC = Files.createDirectories(jimFs.getPath("c4/"));
			final Path cD = Files.createDirectories(jimFs.getPath("c5/"));
			final Path links = Files.createDirectories(jimFs.getPath("links/"));

			{
				Files.writeString(start.resolve("start.txt"), "A starting point");
			}
			{
				Files.writeString(cA.resolve("start.txt"), "A starting point");
				Files.writeString(cA.resolve("first.txt"), "Hello world");
			}
			{
				Files.writeString(cB.resolve("start.txt"), "A starting point");
				Files.writeString(cB.resolve("first.txt"), "Hello world");
				Files.writeString(Files.createDirectories(cB.resolve("a/")).resolve("some file.txt"), "Hey");
			}
			{
				Files.writeString(cC.resolve("start.txt"), "A starting point");
				Files.writeString(cC.resolve("first.txt"), "Coucou monde");
			}
			{
				Files.writeString(cD.resolve("start.txt"), "A starting point");
				Files.writeString(cD.resolve("first.txt"), "Hello world\nCoucou monde");
				Files.writeString(Files.createDirectories(cD.resolve("a/")).resolve("some file.txt"), "Hey");
			}
			{
				final Path origin = Files.createDirectories(links.resolve(Constants.R_REMOTES + "origin/"));
				Files.createSymbolicLink(origin.resolve("br1"), cA);
				Files.createSymbolicLink(origin.resolve("br2"), cC);
				Files.createSymbolicLink(origin.resolve("br3"), cD);
			}

			final MutableGraph<Path> graphBuilder = GraphBuilder.directed().allowsSelfLoops(false).build();
			graphBuilder.putEdge(start, cA);
			graphBuilder.putEdge(start, cC);
			graphBuilder.putEdge(cA, cB);
			graphBuilder.putEdge(cB, cD);
			graphBuilder.putEdge(cC, cD);
			final ImmutableGraph<Path> graph = ImmutableGraph.copyOf(graphBuilder);
			final ZonedDateTime startTime = ZonedDateTime.parse("2022-01-01T00:00:00+01:00[Europe/Paris]");
//			final ZonedDateTime cATime = startTime;
//			final ZonedDateTime cBTime = startTime;
			final ZonedDateTime cCTime = startTime.plus(1, ChronoUnit.HOURS);
//			final ZonedDateTime cDTime = startTime.plus(5, ChronoUnit.HOURS);
//			final ImmutableMap.Builder<Path, PersonIdent> builder = ImmutableMap.builder();
//			builder.put(start, new PersonIdent(personIdent, Date.from(startTime.toInstant()),
//					TimeZone.getTimeZone(startTime.getZone())));
//			builder.put(cA, new PersonIdent(personIdent, Date.from(cATime.toInstant()),
//					TimeZone.getTimeZone(cATime.getZone())));
//			builder.put(cB, new PersonIdent(personIdent, Date.from(cBTime.toInstant()),
//					TimeZone.getTimeZone(cBTime.getZone())));
//			builder.put(cC, new PersonIdent(personIdent, Date.from(cCTime.toInstant()),
//					TimeZone.getTimeZone(cCTime.getZone())));
//			builder.put(cD, new PersonIdent(personIdent, Date.from(cDTime.toInstant()),
//					TimeZone.getTimeZone(cDTime.getZone())));

			final PersonIdent personIdent = personIdent(USERNAME.getUsername(), "email", startTime);

			try (Repository repository = JGit.createRepository(personIdent, graph, links)) {
				try (GitFileSystemImpl gitFs = GitFileSystemProviderImpl.getInstance()
						.newFileSystemFromRepository(repository)) {
					final GitHistory history = GitUtils.getHistory(gitFs);
					final ObjectId startId = history.getRoots().stream().collect(MoreCollectors.onlyElement());
					LOGGER.info("Start id: {}", startId.getName());
					final GitPathRootImpl startPath = gitFs.getPathRoot(startId);
					verify(Files.find(startPath, Integer.MAX_VALUE, (p, a) -> Files.isRegularFile(p)).count() == 1);
					final Set<ObjectId> successors = history.getGraph().successors(startId);
					final Iterator<ObjectId> iterator = successors.iterator();
					final ObjectId commitFirstSucc = iterator.next();
					final ObjectId commitSecondSucc = iterator.next();
					verify(!iterator.hasNext());
					final GitPathRootImpl commitFirstSuccPath = gitFs.getPathRoot(commitFirstSucc);
					final boolean firstIsHello = Files.readString(commitFirstSuccPath.resolve("first.txt"))
							.equals("Hello world");
					final ObjectId commitAId = firstIsHello ? commitFirstSucc : commitSecondSucc;
					final ObjectId commitCId = firstIsHello ? commitSecondSucc : commitFirstSucc;
					LOGGER.info("A id: {}", commitAId.getName());
					final GitPathRootImpl commitAPath = gitFs.getPathRoot(commitAId);
					verify(Files.find(commitAPath, Integer.MAX_VALUE, (p, a) -> Files.isRegularFile(p)).count() == 2);
					final GitPathRootImpl commitCPath = gitFs.getPathRoot(commitCId);
					verify(Files.find(commitCPath, Integer.MAX_VALUE, (p, a) -> Files.isRegularFile(p)).count() == 2);
					verify(Files.readString(commitCPath.resolve("first.txt")).equals("Coucou monde"));
					final ObjectId commitBId = history.getGraph().successors(commitAId).stream()
							.collect(MoreCollectors.onlyElement());
					LOGGER.info("B id: {}", commitBId.getName());
					LOGGER.info("C id: {}", commitCId.getName());
					final GitPathRootImpl commitBPath = gitFs.getPathRoot(commitBId);
					verify(Files.find(commitBPath, Integer.MAX_VALUE, (p, a) -> Files.isRegularFile(p)).count() == 3);
					final ObjectId commitDId = history.getGraph().successors(commitBId).stream()
							.collect(MoreCollectors.onlyElement());
					LOGGER.info("D id: {}", commitDId.getName());
					final GitPathRootImpl commitDPath = gitFs.getPathRoot(commitDId);
					verify(Files.find(commitDPath, Integer.MAX_VALUE, (p, a) -> Files.isRegularFile(p)).count() == 3);
					final ObjectId commitChildCId = history.getGraph().successors(commitCId).stream()
							.collect(MoreCollectors.onlyElement());
					verify(commitDId.equals(commitChildCId));

					final GitFileSystemHistory gitH = GitFileSystemHistory.create(gitFs, history,
							ImmutableMap.of(startId, startTime.toInstant(), commitCId, cCTime.toInstant()));
					final BatchGitHistoryGrader<RuntimeException> batchGrader = BatchGitHistoryGrader
							.given(() -> StaticFetcher.single(USERNAME, gitH));
					final Exam exam = batchGrader.getGrades(startTime, Duration.ofHours(100), new Branching(),
							Branching.USER_WEIGHT);

					Files.writeString(Path.of("test grades.json"), JsonSimpleGrade.toJson(exam));
					Files.writeString(Path.of("test grades.html"), XmlUtils.asString(
							HtmlGrades.asHtml(exam.getGrade(USERNAME), Branching.PREFIX + " " + Instant.now(), 20d)));
					assertEquals(ImmutableSet.of(USERNAME), exam.getUsernames());
					assertEquals(0.99d, exam.getGrade(USERNAME).mark().getPoints());
				}
			}
		}

	}

	@Test
	void testPerfect() throws Exception {
		try (FileSystem jimFs = Jimfs.newFileSystem(Configuration.unix())) {
			final Path start = Files.createDirectories(jimFs.getPath("c1/"));
			final Path cA = Files.createDirectories(jimFs.getPath("c2/"));
			final Path cB = Files.createDirectories(jimFs.getPath("c3/"));
			final Path cC = Files.createDirectories(jimFs.getPath("c4/"));
			final Path cD = Files.createDirectories(jimFs.getPath("c5/"));
			final Path links = Files.createDirectories(jimFs.getPath("links/"));

			{
				Files.writeString(start.resolve("start.txt"), "A starting point");
			}
			{
				Files.writeString(cA.resolve("start.txt"), "A starting point");
				Files.writeString(cA.resolve("first.txt"), "Hello world");
			}
			{
				Files.writeString(cB.resolve("start.txt"), "A starting point");
				Files.writeString(cB.resolve("first.txt"), "Hello world");
				Files.writeString(Files.createDirectories(cB.resolve("a/")).resolve("some file.txt"), "Hey");
			}
			{
				Files.writeString(cC.resolve("start.txt"), "A starting point");
				Files.writeString(cC.resolve("first.txt"), "Coucou monde");
			}
			{
				Files.writeString(cD.resolve("start.txt"), "A starting point");
				Files.writeString(cD.resolve("first.txt"), "Hello world\nCoucou monde");
				Files.writeString(Files.createDirectories(cD.resolve("a/")).resolve("some file.txt"), "Hey");
			}
			{
				final Path origin = Files.createDirectories(links.resolve(Constants.R_REMOTES + "origin/"));
				Files.createSymbolicLink(origin.resolve("br1"), cA);
				Files.createSymbolicLink(origin.resolve("br2"), cC);
				Files.createSymbolicLink(origin.resolve("br3"), cD);
			}

			final PersonIdent personIdent = new PersonIdent(USERNAME.getUsername(), "email");

			final MutableGraph<Path> graphBuilder = GraphBuilder.directed().allowsSelfLoops(false).build();
			graphBuilder.putEdge(start, cA);
			graphBuilder.putEdge(start, cC);
			graphBuilder.putEdge(cA, cB);
			graphBuilder.putEdge(cB, cD);
			graphBuilder.putEdge(cC, cD);
			final ImmutableGraph<Path> graph = ImmutableGraph.copyOf(graphBuilder);

			try (Repository repository = JGit.createRepository(personIdent, graph, links)) {
				try (GitFileSystemImpl gitFs = GitFileSystemProviderImpl.getInstance()
						.newFileSystemFromRepository(repository)) {
					final GitHistory history = GitUtils.getHistory(gitFs);
					final ObjectId startId = history.getRoots().stream().collect(MoreCollectors.onlyElement());
					LOGGER.info("Start id: {}", startId.getName());
					final GitPathRootImpl startPath = gitFs.getPathRoot(startId);
					verify(Files.find(startPath, Integer.MAX_VALUE, (p, a) -> Files.isRegularFile(p)).count() == 1);
					final Set<ObjectId> successors = history.getGraph().successors(startId);
					final Iterator<ObjectId> iterator = successors.iterator();
					final ObjectId commitFirstSucc = iterator.next();
					final ObjectId commitSecondSucc = iterator.next();
					verify(!iterator.hasNext());
					final GitPathRootImpl commitFirstSuccPath = gitFs.getPathRoot(commitFirstSucc);
					final boolean firstIsHello = Files.readString(commitFirstSuccPath.resolve("first.txt"))
							.equals("Hello world");
					final ObjectId commitAId = firstIsHello ? commitFirstSucc : commitSecondSucc;
					final ObjectId commitCId = firstIsHello ? commitSecondSucc : commitFirstSucc;
					LOGGER.info("A id: {}", commitAId.getName());
					final GitPathRootImpl commitAPath = gitFs.getPathRoot(commitAId);
					verify(Files.find(commitAPath, Integer.MAX_VALUE, (p, a) -> Files.isRegularFile(p)).count() == 2);
					final GitPathRootImpl commitCPath = gitFs.getPathRoot(commitCId);
					verify(Files.find(commitCPath, Integer.MAX_VALUE, (p, a) -> Files.isRegularFile(p)).count() == 2);
					verify(Files.readString(commitCPath.resolve("first.txt")).equals("Coucou monde"));
					final ObjectId commitBId = history.getGraph().successors(commitAId).stream()
							.collect(MoreCollectors.onlyElement());
					LOGGER.info("B id: {}", commitBId.getName());
					LOGGER.info("C id: {}", commitCId.getName());
					final GitPathRootImpl commitBPath = gitFs.getPathRoot(commitBId);
					verify(Files.find(commitBPath, Integer.MAX_VALUE, (p, a) -> Files.isRegularFile(p)).count() == 3);
					final ObjectId commitDId = history.getGraph().successors(commitBId).stream()
							.collect(MoreCollectors.onlyElement());
					LOGGER.info("D id: {}", commitDId.getName());
					final GitPathRootImpl commitDPath = gitFs.getPathRoot(commitDId);
					verify(Files.find(commitDPath, Integer.MAX_VALUE, (p, a) -> Files.isRegularFile(p)).count() == 3);
					final ObjectId commitChildCId = history.getGraph().successors(commitCId).stream()
							.collect(MoreCollectors.onlyElement());
					verify(commitDId.equals(commitChildCId));

					final GitFileSystemHistory gitH = GitFileSystemHistory.create(gitFs, history, ImmutableMap.of());
					final BatchGitHistoryGrader<RuntimeException> batchGrader = BatchGitHistoryGrader
							.given(() -> StaticFetcher.single(USERNAME, gitH));
					final Exam exam = batchGrader.getGrades(BatchGitHistoryGrader.MAX_DEADLINE, Duration.ofMinutes(0),
							new Branching(), Branching.USER_WEIGHT);

					Files.writeString(Path.of("test grades.json"), JsonSimpleGrade.toJson(exam));
					Files.writeString(Path.of("test grades.html"), XmlUtils.asString(
							HtmlGrades.asHtml(exam.getGrade(USERNAME), Branching.PREFIX + " " + Instant.now(), 20d)));
					assertEquals(ImmutableSet.of(USERNAME), exam.getUsernames());
					assertEquals(1d, exam.getGrade(USERNAME).mark().getPoints(), 1e-6d);
				}
			}
		}

	}

}
