package io.github.oliviercailloux.grade.format;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.common.io.Resources;
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
	void testComplexGrade() throws Exception {
		final WeightingGrade grade = GradeTestsHelper.getComplexGradeWithPenalty();
		final Document document = HtmlGrades.asHtml(grade, "Ze grade");
//		XmlUtils.validate(document);
		final String written = XmlUtils.asString(document);
		LOGGER.info("Complex grade: {}.", written);

		final String expected = Resources.toString(getClass().getResource("ComplexGradeWithPenalty.html"),
				StandardCharsets.UTF_8);
		assertEquals(expected, written);
	}

	@Test
	void testEclecticGrade() throws Exception {
		final IGrade grade = GradeTestsHelper.getEclecticWeightedGrade();
		final Document document = HtmlGrades.asHtml(grade, "Ze grade");
//		XmlUtils.validate(document);
		final String written = XmlUtils.asString(document);
		LOGGER.info("Eclectic grade: {}.", written);

		final String expected = Resources.toString(getClass().getResource("EclecticGrade.html"),
				StandardCharsets.UTF_8);
		assertEquals(expected, written);
	}

}
