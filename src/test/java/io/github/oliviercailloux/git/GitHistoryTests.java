package io.github.oliviercailloux.git;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.graph.GraphBuilder;

class GitHistoryTests {

	@Test
	void testEmptyHistory() {
		final GitLocalHistory history = GitLocalHistory.from(ImmutableList.of());
		assertEquals(ImmutableSet.of(), history.getRoots());
		assertEquals(ImmutableSet.of(), history.getTips());
		assertEquals(ImmutableMap.of(), history.getCommitDates());
		assertEquals(GraphBuilder.directed().immutable().build(), history.getGraph());
	}

}
