package io.github.oliviercailloux.grade.mycourse.csv;

import static io.github.oliviercailloux.grade.mycourse.csv.CsvStudentsOnMyCourse.LAST_NAME_COLUMN;
import static io.github.oliviercailloux.grade.mycourse.csv.CsvStudentsOnMyCourse.USERNAME_COLUMN;

import java.io.StringWriter;
import java.text.NumberFormat;
import java.util.Collection;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.univocity.parsers.csv.CsvWriter;
import com.univocity.parsers.csv.CsvWriterSettings;

import io.github.oliviercailloux.grade.GradeWithStudentAndCriterion;
import io.github.oliviercailloux.grade.mycourse.StudentOnMyCourse;

public class MyCourseCsvWriter {

	private static final String NOTES_COLUMN = "Notes";
	private static final String NOTES_FORMAT_COLUMN = "Format des notes";
	private static final String FEEDBACK_COLUMN = "Feed-back fourni à l'apprenant";
	private static final String FEEDBACK_FORMAT_COLUMN = "Format du feed-back";
	private CsvWriter cSVWriter;

	public MyCourseCsvWriter() {
		cSVWriter = null;
	}

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(MyCourseCsvWriter.class);

	private void addFeedback(String feedback) {
		cSVWriter.addValue(FEEDBACK_COLUMN, feedback);
		cSVWriter.addValue(FEEDBACK_FORMAT_COLUMN, "SMART_TEXT");
	}

	public String asMyCourseCsv(String gradeName, int gradeId, Collection<GradeWithStudentAndCriterion> grades, double scaleMax) {
		final String gradeC = gradeName + " |" + gradeId;
		final NumberFormat formatter = NumberFormat.getNumberInstance(Locale.FRENCH);
		final StringWriter stringWriter = new StringWriter();
		/** Stupid MyCourse requires a BOM. */
		stringWriter.write('\uFEFF');
		cSVWriter = new CsvWriter(stringWriter, new CsvWriterSettings());
		cSVWriter.writeHeaders(LAST_NAME_COLUMN, USERNAME_COLUMN, gradeC, NOTES_COLUMN, NOTES_FORMAT_COLUMN,
				FEEDBACK_COLUMN, FEEDBACK_FORMAT_COLUMN);
		for (GradeWithStudentAndCriterion grade : grades) {
			final StudentOnMyCourse student = grade.getStudent().asStudentOnGitHubKnown().asStudentOnMyCourse();
			LOGGER.info("Writing {}.", student);
			cSVWriter.addValue(LAST_NAME_COLUMN, student.getLastName());
			cSVWriter.addValue(USERNAME_COLUMN, student.getMyCourseUsername());
			cSVWriter.addValue(gradeC, formatter.format(grade.getScaledGrade(scaleMax)));
			addFeedback(grade.getAsMyCourseString(scaleMax));
			cSVWriter.writeValuesToRow();
		}
		cSVWriter.close();
		return stringWriter.toString();
	}

}
