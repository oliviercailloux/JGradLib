package io.github.oliviercailloux.grade.format;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import io.github.oliviercailloux.grade.Grade;
import io.github.oliviercailloux.grade.GradeTestsHelper;
import io.github.oliviercailloux.grade.IGrade;
import io.github.oliviercailloux.grade.WeightingGrade;
import io.github.oliviercailloux.xml.XmlUtils;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

class HtmlGradeTests {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(HtmlGradeTests.class);

	@Test
	void test3Plus2() throws Exception {
		final Grade grade = GradeTestsHelper.get3Plus2();
		final Document document = HtmlGrades.asHtml(ImmutableMap.of("g1", grade), "Ze grades", 20d);
		XmlUtils.validate(document);
		final String written = XmlUtils.asString(document);

		final String expected = Resources.toString(getClass().getResource("3Plus2.html"), StandardCharsets.UTF_8);
		assertEquals(expected, written);
	}

}
