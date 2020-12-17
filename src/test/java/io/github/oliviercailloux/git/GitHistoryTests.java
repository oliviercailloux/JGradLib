package io.github.oliviercailloux.git;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.time.Instant;

import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.ImmutableGraph;

class GitHistoryTests {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GitHistoryTests.class);

	@Test
	void testEmptyHistory() {
		final GitHistory history = GitHistory.create(GraphBuilder.directed().build(),
				ImmutableMap.<ObjectId, Instant>of());
		assertEquals(ImmutableSet.of(), history.getRoots());
		assertEquals(ImmutableSet.of(), history.getLeaves());
		assertEquals(ImmutableMap.of(), history.getCommitDates());
		assertEquals(GraphBuilder.directed().build(), history.getGraph());
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

		final GitHistory history = GitHistory.create(builder.build(),
				ImmutableMap.of(a, Instant.MAX, b, Instant.MAX, c, Instant.MAX, f, Instant.MAX));
		final GitHistory filtered = history.filter(o -> !o.equals(f));

		final ImmutableGraph.Builder<ObjectId> expectedBuilder = GraphBuilder.directed().immutable();
		assertEquals(expectedBuilder.putEdge(a, b).putEdge(b, c).build(), filtered.getGraph());
		assertEquals(ImmutableMap.of(a, Instant.MAX, b, Instant.MAX, c, Instant.MAX), filtered.getCommitDates());
	}

	@Test
	void testHistoryBasic() throws Exception {
		try (Repository repo = new InMemoryRepository(new DfsRepositoryDescription())) {
			final ImmutableList<ObjectId> commits = JGit.createBasicRepo(repo);
			final GitHistory history = GitUtils.getHistory(repo);
			assertEquals(2, history.getGraph().nodes().size());
			assertEquals(commits.get(0), Iterables.getOnlyElement(history.getRoots()));
			assertEquals(commits.get(1), Iterables.getOnlyElement(history.getLeaves()));
		}
	}

	@Test
	void testHistoryCloning() throws Exception {
		try (Repository repo = new InMemoryRepository(new DfsRepositoryDescription())) {
			repo.create(true);
			GitCloner.create().clone(GitUri.fromUri(new URI("https", "github.com", "/oliviercailloux/CLut", null)),
					repo);
			final GitHistory history = GitUtils.getHistory(repo);
			final ObjectId parent = ObjectId.fromString("21af8bffc747eaee04217b9c8bb9e3e4a3a6293d");
			final ObjectId child = ObjectId.fromString("c145866575e55309f943ad2c2b4d547b926f38d0");
			final ObjectId childChild = ObjectId.fromString("4016d7b1b09e2a188fb99d30d1ca5b0f726a4a3d");
			assertEquals(Instant.parse("2018-08-21T14:35:14Z"), history.getCommitDate(parent));
			assertEquals(Instant.parse("2018-08-21T18:25:52Z"), history.getCommitDate(child));
			assertEquals(Instant.parse("2018-08-21T18:28:49Z"), history.getCommitDate(childChild));
			final ImmutableGraph<ObjectId> graph = history.getGraph();
			assertTrue(graph.successors(parent).contains(child));
			assertTrue(graph.successors(child).contains(childChild));
		}
	}

}
