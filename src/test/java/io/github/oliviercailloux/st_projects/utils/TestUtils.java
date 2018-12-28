package io.github.oliviercailloux.st_projects.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Queue;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.MutableGraph;

class TestUtils {

	@Test
	void test() {
		final ImmutableSet<String> roots = ImmutableSet.of("root");
		final MutableGraph<String> graph = GraphBuilder.directed().build();
		graph.addNode("root");
		graph.putEdge("root", "first");
		final Queue<String> sorted = Utils.topologicalSort(graph, roots);
		assertEquals(ImmutableList.of("root", "first"), ImmutableList.copyOf(sorted));
	}

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(TestUtils.class);

}
