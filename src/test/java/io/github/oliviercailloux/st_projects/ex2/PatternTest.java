package io.github.oliviercailloux.st_projects.ex2;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

class PatternTest {

	@Test
	void test() {
//		final String any = "(.|\\v)*";
		assertTrue(Pattern.compile("@WebServlet")
				.matcher("@WebServlet(name = \"AdditionerServlet\", urlPatterns = { \"/add\" })").find());
	}

}
