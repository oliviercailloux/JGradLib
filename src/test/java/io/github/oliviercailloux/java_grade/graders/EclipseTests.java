package io.github.oliviercailloux.java_grade.graders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.Graphs;
import com.google.common.graph.ImmutableGraph;
import io.github.oliviercailloux.git.GitCloner;
import io.github.oliviercailloux.git.GitHistory;
import io.github.oliviercailloux.git.GitUtils;
import io.github.oliviercailloux.git.fs.GitFileSystem;
import io.github.oliviercailloux.git.fs.GitFileSystemProvider;
import io.github.oliviercailloux.git.fs.GitPathRoot;
import io.github.oliviercailloux.git.git_hub.model.GitHubUsername;
import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinates;
import io.github.oliviercailloux.grade.GitFileSystemHistory;
import io.github.oliviercailloux.grade.GitWork;
import io.github.oliviercailloux.grade.IGrade;
import io.github.oliviercailloux.grade.format.json.JsonGrade;
import io.github.oliviercailloux.utils.Utils;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EclipseTests {

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(EclipseTests.class);

	@Test
	void testEmpty() throws Exception {
		try (Repository repository = new InMemoryRepository(new DfsRepositoryDescription("myrepo"));
				GitFileSystem gitFs = GitFileSystemProvider.getInstance().newFileSystemFromRepository(repository)) {

			final GitFileSystemHistory empty = GitFileSystemHistory.create(gitFs,
					GitHistory.create(GraphBuilder.directed().build(), ImmutableMap.of()));

			final IGrade grade = new Eclipse().grade(GitWork.given(GitHubUsername.given("ploum"), empty));
			LOGGER.debug("Grade direct: {}.", JsonGrade.asJson(grade));
			assertEquals(0d, grade.getPoints());
		}
	}

	@Test
	void testNothing() throws Exception {
		try (FileRepository repository = GitCloner.create().setCheckCommonRefsAgree(false).download(
				RepositoryCoordinates.from("oliviercailloux-org", "minimax-ex").asGitUri(),
				Utils.getTempDirectory().resolve("minimax-ex"));
				GitFileSystem gitFs = GitFileSystemProvider.getInstance().newFileSystemFromRepository(repository)) {
			final GitHistory rawHistory = GitUtils.getHistory(gitFs);
			final ImmutableGraph<ObjectId> graph = rawHistory.getGraph();
			final Map<ObjectId, Instant> constantTimes = Maps.asMap(graph.nodes(), o -> Commit.DEADLINE.toInstant());
			final GitFileSystemHistory withConstantTimes = GitFileSystemHistory.create(gitFs,
					GitHistory.create(graph, constantTimes));
			LOGGER.debug("Cst: {}.", withConstantTimes.getGraph().nodes().size());

			final GitPathRoot master = gitFs.getPathRoot("/refs/remotes/origin/master/");
			final GitPathRoot masterId = gitFs.getPathRoot(master.getCommit().getId());
			final GitFileSystemHistory justMaster = withConstantTimes.filter(r -> r.equals(masterId));
			LOGGER.debug("From master: {}.", justMaster);

			final IGrade grade = new Eclipse().setIncludeMine()
					.grade(GitWork.given(GitHubUsername.given("Olivier Cailloux"), justMaster));
			LOGGER.debug("Grade: {}.", JsonGrade.asJson(grade));
			assertEquals(0.05d, grade.getPoints());
		}
	}

	@Test
	void testPerfect() throws Exception {
		try (FileRepository repository = GitCloner.create().setCheckCommonRefsAgree(false).download(
				RepositoryCoordinates.from("oliviercailloux-org", "minimax-ex").asGitUri(),
				Utils.getTempDirectory().resolve("minimax-ex"));
				GitFileSystem gitFs = GitFileSystemProvider.getInstance().newFileSystemFromRepository(repository)) {
			final GitHistory rawHistory = GitUtils.getHistory(gitFs);
			final ImmutableGraph<ObjectId> graph = rawHistory.getGraph();
			final Map<ObjectId, Instant> constantTimes = Maps.asMap(graph.nodes(), o -> Commit.DEADLINE.toInstant());
			final GitFileSystemHistory withConstantTimes = GitFileSystemHistory.create(gitFs,
					GitHistory.create(graph, constantTimes));
			LOGGER.debug("Cst: {}.", withConstantTimes.getGraph().nodes().size());

			final GitPathRoot master = gitFs.getPathRoot("/refs/remotes/origin/master/");
			final GitPathRoot masterId = gitFs.getPathRoot(master.getCommit().getId());
			final Set<GitPathRoot> afterMaster = Graphs.reachableNodes(withConstantTimes.getGraph(), masterId);
			final GitFileSystemHistory fromMaster = withConstantTimes
					.filter(r -> afterMaster.contains(r) || r.equals(masterId));
			LOGGER.debug("From master: {}.", fromMaster);

			assertFalse(new Eclipse().formatted(masterId));
			final IGrade grade = new Eclipse().setIncludeMine()
					.grade(GitWork.given(GitHubUsername.given("Olivier Cailloux"), fromMaster));
			LOGGER.debug("Grade: {}.", JsonGrade.asJson(grade));
			assertEquals(1.0d, grade.getPoints());
		}
	}

}
