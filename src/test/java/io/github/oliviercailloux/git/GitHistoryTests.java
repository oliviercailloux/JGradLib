package io.github.oliviercailloux.git;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;

import org.eclipse.jgit.lib.ObjectId;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
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

}
