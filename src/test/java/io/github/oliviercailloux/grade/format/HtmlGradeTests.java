package io.github.oliviercailloux.grade.format;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;

import javax.mail.Message;
import javax.mail.internet.InternetAddress;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

import com.google.common.io.Resources;

import io.github.oliviercailloux.email.EMailer;
import io.github.oliviercailloux.email.EMailerMain;
import io.github.oliviercailloux.email.Email;
import io.github.oliviercailloux.grade.GradeTestsHelper;
import io.github.oliviercailloux.grade.WeightingGrade;
import io.github.oliviercailloux.grade.format.json.JsonGrade;
import io.github.oliviercailloux.xml.XmlUtils;

class HtmlGradeTests {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(HtmlGradeTests.class);

	public static void main(String[] args) throws Exception {
		final WeightingGrade grade = GradeTestsHelper.getComplexGradeWithPenalty();
		final Document document = HtmlGrade.asHtml(grade, "Ze grade");
		final Email email = Email.withDocumentAndFile(document, "data.json", JsonGrade.asJson(grade).toString(),
				"json");
		final InternetAddress to = new InternetAddress("olivier.cailloux@gmail.com", "O.C");

		final Message sent = EMailer.sendTo(email, to);

		EMailer.saveInto(sent, EMailerMain.SENT_FOLDER);
	}

	@Test
	void testComplexGrade() throws Exception {
		final WeightingGrade grade = GradeTestsHelper.getComplexGradeWithPenalty();
		final Document document = HtmlGrade.asHtml(grade, "Ze grade");
//		XmlUtils.validate(document);
		final String written = XmlUtils.asString(document);
		LOGGER.info("Complex grade: {}.", written);

		final String expected = Resources.toString(getClass().getResource("ComplexGradeWithPenalty.html"),
				StandardCharsets.UTF_8);
		assertEquals(expected, written);
	}

}
