package io.github.oliviercailloux.git;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.time.Instant;
import java.util.Arrays;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.internal.storage.dfs.DfsRepository;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Predicates;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.graph.Graph;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.ImmutableGraph;

import io.github.oliviercailloux.git.GitLocalHistory.GitRaw;
import io.github.oliviercailloux.git.GitRawHistoryDecorator.GitRawHistory;
import io.github.oliviercailloux.java_grade.testers.JavaMarkHelper;
import io.github.oliviercailloux.utils.Utils;

class GitHistoryTests {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GitHistoryTests.class);

	@Test
	void testEmptyHistory() {
		final GitLocalHistory history = GitLocalHistory.from(ImmutableList.of());
		assertEquals(ImmutableSet.of(), history.getRoots());
		assertEquals(ImmutableSet.of(), history.getTips());
		assertEquals(ImmutableMap.of(), history.getCommitDates());
		assertEquals(GraphBuilder.directed().immutable().build(), history.getGraph());
	}

	@Test
	void testFilter() throws Exception {
		try (DfsRepository repository = new InMemoryRepository(new DfsRepositoryDescription("myrepo"))) {
			repository.create(true);
			new GitCloner().clone(
					GitUri.fromGitUri(URI.create("https://github.com/oliviercailloux/assisted-board-games.git")),
					repository);
			final GitLocalHistory basic;
			final ImmutableSet<RevCommit> allCommits;
			try (Git git = Git.wrap(repository)) {
				final Iterable<RevCommit> commits = git.log().all().call();
				allCommits = ImmutableSet.copyOf(commits);
			}
			final Graph<RevCommit> graph = Utils.asGraph((c) -> Arrays.asList(c.getParents()), allCommits);
			final GitRaw raw = new GitRaw(graph);
			basic = new GitLocalHistory(raw);
			for (RevCommit c : basic.getGraph().nodes()) {
				LOGGER.info("Creation time: {}.", GitUtils.getCreationTime(c));
			}
			LOGGER.info("Pred 90: {}.", raw.getGraph()
					.predecessors(basic.getCommit(ObjectId.fromString("909320dd00b118fe2a3a3362bb37a8806c7a367c"))));
			LOGGER.info("Pred 90: {}.", basic.getGraph()
					.predecessors(basic.getCommit(ObjectId.fromString("909320dd00b118fe2a3a3362bb37a8806c7a367c"))));
//			final GitLocalHistory history = basic.filter(
//					r -> GitUtils.getCreationTime(r).toInstant().isBefore(Instant.parse("2018-12-18T16:00:00Z")));
			final GitLocalHistory history = basic.filter(Predicates.alwaysTrue());
			LOGGER.info("Nb nodes before filtering: {}, edges: {}.", history.getGraph().nodes().size(),
					history.getGraph().edges().size());
			LOGGER.info("Graph before: {}.", history.getGraph().edges());
			final GitLocalHistory manual = history.filter(o -> !JavaMarkHelper.committerIsGitHub(basic.getCommit(o)));
			LOGGER.info("Nb nodes after filtering: {}, edges: {}.", manual.getGraph().nodes().size(),
					manual.getGraph().edges().size());
			LOGGER.debug("Graph manual: {}.", manual.getGraph().edges());
			assertTrue(manual.getGraph().edges().size() <= history.getGraph().edges().size());
		}
	}

	/**
	 * Creates two paths to join a to c, to check that only one is retained when an
	 * object on one of the paths is filtered out.
	 */
	@Test
	void testFilterShort() throws Exception {
		final ObjectId a = ObjectId.fromString(Strings.repeat("a", 40));
		final ObjectId b = ObjectId.fromString(Strings.repeat("b", 40));
		final ObjectId c = ObjectId.fromString(Strings.repeat("c", 40));
		final ObjectId f = ObjectId.fromString(Strings.repeat("f", 40));

		final ImmutableGraph.Builder<ObjectId> builder = GraphBuilder.directed().immutable();
		builder.putEdge(a, b);
		builder.putEdge(a, f);
		builder.putEdge(b, c);
		builder.putEdge(f, c);
		final GitRawHistory<ObjectId> raw = GitRawHistoryDecorator.raw(builder.build(),
				ImmutableMap.of(a, Instant.MAX, b, Instant.MAX, c, Instant.MAX, f, Instant.MAX));

		final GitRawHistory<ObjectId> filtered = GitRawHistoryDecorator.filter(raw, o -> !o.equals(f));
		final ImmutableGraph.Builder<ObjectId> expectedBuilder = GraphBuilder.directed().immutable();
		assertEquals(expectedBuilder.putEdge(a, b).putEdge(b, c).build(), filtered.getGraph());
	}

}
