package io.github.oliviercailloux.java_grade.graders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.graph.Graph;
import com.google.common.graph.Graphs;
import io.github.oliviercailloux.git.GitCloner;
import io.github.oliviercailloux.git.fs.GitHistorySimple;
import io.github.oliviercailloux.git.git_hub.model.GitHubUsername;
import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinates;
import io.github.oliviercailloux.gitjfs.GitFileSystem;
import io.github.oliviercailloux.gitjfs.GitFileSystemProvider;
import io.github.oliviercailloux.gitjfs.GitPathRoot;
import io.github.oliviercailloux.gitjfs.GitPathRootSha;
import io.github.oliviercailloux.gitjfs.GitPathRootShaCached;
import io.github.oliviercailloux.grade.GitWork;
import io.github.oliviercailloux.grade.IGrade;
import io.github.oliviercailloux.grade.format.json.JsonGrade;
import io.github.oliviercailloux.jaris.collections.GraphUtils;
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
				GitFileSystem gitFs = GitFileSystemProvider.instance().newFileSystemFromRepository(repository)) {

			final GitHistorySimple empty = GitHistorySimple.create(gitFs, ImmutableMap.of());

			final IGrade grade = new Eclipse().grade(GitWork.given(GitHubUsername.given("ploum"), empty));
			LOGGER.debug("Grade direct: {}.", JsonGrade.asJson(grade));
			assertEquals(0d, grade.getPoints());
		}
	}

	@Test
	void testNothing() throws Exception {
		try (FileRepository repository = GitCloner.create().download(
				RepositoryCoordinates.from("oliviercailloux-org", "minimax-ex").asGitUri(),
				Utils.getTempDirectory().resolve("minimax-ex"));
				GitFileSystem gitFs = GitFileSystemProvider.instance().newFileSystemFromRepository(repository)) {
			final Graph<GitPathRootShaCached> graph = gitFs.graph();
			final ImmutableSet<ObjectId> ids = graph.nodes().stream().map(GitPathRootShaCached::getCommit)
					.map(io.github.oliviercailloux.gitjfs.Commit::id).collect(ImmutableSet.toImmutableSet());
			final Map<ObjectId, Instant> constantTimes = Maps.asMap(ids, o -> Commit.DEADLINE.toInstant());
			final GitHistorySimple withConstantTimes = GitHistorySimple.create(gitFs, constantTimes);
			LOGGER.debug("Cst: {}.", withConstantTimes.graph().nodes().size());

			final GitPathRoot master = gitFs.getPathRoot("/refs/remotes/origin/master/");
			final io.github.oliviercailloux.gitjfs.Commit masterCommit = master.getCommit();
			final GitHistorySimple justMaster = withConstantTimes.filteredCommits(r -> r.equals(masterCommit));
			LOGGER.debug("Just master: {}.", justMaster);

			final IGrade grade = new Eclipse().setIncludeMine()
					.grade(GitWork.given(GitHubUsername.given("Olivier Cailloux"), justMaster));
			LOGGER.debug("Grade: {}.", JsonGrade.asJson(grade));
			assertEquals(0.05d, grade.getPoints());
		}
	}

	@Test
	void testPerfect() throws Exception {
		try (FileRepository repository = GitCloner.create().download(
				RepositoryCoordinates.from("oliviercailloux-org", "minimax-ex").asGitUri(),
				Utils.getTempDirectory().resolve("minimax-ex"));
				GitFileSystem gitFs = GitFileSystemProvider.instance().newFileSystemFromRepository(repository)) {
			final Graph<GitPathRootShaCached> graph = gitFs.graph();
			final Graph<io.github.oliviercailloux.gitjfs.Commit> commitGraph = GraphUtils.transform(graph,
					GitPathRootSha::getCommit);
			final ImmutableSet<ObjectId> ids = graph.nodes().stream().map(GitPathRootShaCached::getCommit)
					.map(io.github.oliviercailloux.gitjfs.Commit::id).collect(ImmutableSet.toImmutableSet());
			final Map<ObjectId, Instant> constantTimes = Maps.asMap(ids, o -> Commit.DEADLINE.toInstant());
			final GitHistorySimple withConstantTimes = GitHistorySimple.create(gitFs, constantTimes);
			LOGGER.debug("Cst: {}.", withConstantTimes.graph().nodes().size());

			final GitPathRoot master = gitFs.getPathRoot("/refs/remotes/origin/master/");
			final io.github.oliviercailloux.gitjfs.Commit masterCommit = master.getCommit();
			final ObjectId masterId = masterCommit.id();
			final GitPathRootShaCached masterIdPath = gitFs.getPathRoot(masterId).toShaCached();
			final Set<io.github.oliviercailloux.gitjfs.Commit> afterMasterCommits = Graphs.reachableNodes(commitGraph,
					masterCommit);
//			final Graph<ObjectId> idGraph = GraphUtils.transform(commitGraph,
//					io.github.oliviercailloux.gitjfs.Commit::id);
//			final Set<ObjectId> afterMasterIds = Graphs.reachableNodes(idGraph, masterId);
//			final Set<GitPathRootShaCached> afterMaster = Graphs.reachableNodes(withConstantTimes.graph(),
//					masterIdPath);
			final GitHistorySimple fromMaster = withConstantTimes
					.filteredCommits(r -> afterMasterCommits.contains(r) || r.equals(masterCommit));
			LOGGER.debug("From master: {}.", fromMaster);

			assertFalse(new Eclipse().formatted(masterIdPath));
			final IGrade grade = new Eclipse().setIncludeMine()
					.grade(GitWork.given(GitHubUsername.given("Olivier Cailloux"), fromMaster));
			LOGGER.debug("Grade: {}.", JsonGrade.asJson(grade));
			assertEquals(1.0d, grade.getPoints());
		}
	}

}
