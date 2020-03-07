package io.github.oliviercailloux.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import io.github.oliviercailloux.java_grade.utils.FindEnds;

class UtilsTests {

	@Test
	void testQuery() throws Exception {
		final String queryStr = "first=first value&second=(already)?&first=";
		final URI uri = new URI("ssh", "", "host", -1, "/path", queryStr, null);
		assertEquals(queryStr, uri.getQuery());
		assertEquals("first=first%20value&second=(already)?&first=", uri.getRawQuery());
		final Map<String, ImmutableList<String>> query = Utils.getQuery(uri);
		assertEquals(ImmutableSet.of("first", "second"), query.keySet());
		assertEquals(ImmutableList.of("first value", ""), query.get("first"));
		assertEquals(ImmutableList.of("(already)?"), query.get("second"));
	}

	@Test
	@EnabledIfEnvironmentVariable(named = "CONTINUOUS_INTEGRATION", matches = "true")
	void testFindEnds() throws Exception {
		assertEquals(ImmutableSet.of(), FindEnds.withPrefix("commit").getEnded());
		assertEquals(ImmutableSet.of(), FindEnds.withPrefix("git-br").getEnded());
	}
}
