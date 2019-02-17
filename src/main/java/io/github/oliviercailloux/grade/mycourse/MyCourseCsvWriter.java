package io.github.oliviercailloux.grade.mycourse;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Sets;
import com.univocity.parsers.csv.CsvWriter;
import com.univocity.parsers.csv.CsvWriterSettings;

import io.github.oliviercailloux.grade.Grade;

public class MyCourseCsvWriter {

	private static final String NAME_COLUMN = "Nom";
	private static final String USERNAME_COLUMN = "Nom d'utilisateur";
	private static final String NOTES_COLUMN = "Notes";
	private static final String NOTES_FORMAT_COLUMN = "Format des notes";
	private static final String FEEDBACK_COLUMN = "Feed-back fourni à l'apprenant";
	private static final String FEEDBACK_FORMAT_COLUMN = "Format du feed-back";
	private CsvWriter writer;

	public MyCourseCsvWriter() {
		writer = null;
	}

	@Deprecated
	public void writeCsv(String gradeName, int gradeId, Map<StudentOnMyCourse, Double> grades,
			Map<StudentOnMyCourse, String> feedbacks) throws IOException {
		LOGGER.info("Writing for {}.", grades.keySet());
		final Path out = Paths.get("out.csv");
		final String gradeC = gradeName + " |" + gradeId;
		checkNotNull(gradeName);
		checkArgument(grades.keySet().equals(feedbacks.keySet()),
				Sets.symmetricDifference(grades.keySet(), feedbacks.keySet()));
		final NumberFormat formatter = NumberFormat.getNumberInstance(Locale.FRENCH);
		try (BufferedWriter fileWriter = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
			/** Stupid MyCourse requires a BOM. */
			fileWriter.write('\uFEFF');
			writer = new CsvWriter(fileWriter, new CsvWriterSettings());
			writer.writeHeaders(NAME_COLUMN, USERNAME_COLUMN, gradeC, NOTES_COLUMN, NOTES_FORMAT_COLUMN,
					FEEDBACK_COLUMN, FEEDBACK_FORMAT_COLUMN);
			for (StudentOnMyCourse student : grades.keySet()) {
				LOGGER.info("Writing {}.", student);
				writer.addValue(NAME_COLUMN, student.getLastName());
				writer.addValue(USERNAME_COLUMN, student.getMyCourseUsername());
				writer.addValue(gradeC, formatter.format(grades.get(student)));
				addFeedback(feedbacks.get(student));
				writer.writeValuesToRow();
			}
			writer.close();
		}
	}

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(MyCourseCsvWriter.class);

	private void addFeedback(String feedback) {
		writer.addValue(FEEDBACK_COLUMN, feedback);
		writer.addValue(FEEDBACK_FORMAT_COLUMN, "SMART_TEXT");
	}

	public void writeCsv(String gradeName, int gradeId, Set<Grade> grades) throws IOException {
		final Path out = Paths.get("out.csv");
		final String gradeC = gradeName + " |" + gradeId;
		final NumberFormat formatter = NumberFormat.getNumberInstance(Locale.FRENCH);
		try (BufferedWriter fileWriter = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
			/** Stupid MyCourse requires a BOM. */
			fileWriter.write('\uFEFF');
			writer = new CsvWriter(fileWriter, new CsvWriterSettings());
			writer.writeHeaders(NAME_COLUMN, USERNAME_COLUMN, gradeC, NOTES_COLUMN, NOTES_FORMAT_COLUMN,
					FEEDBACK_COLUMN, FEEDBACK_FORMAT_COLUMN);
			for (Grade grade : grades) {
				final StudentOnMyCourse student = grade.getStudent().asStudentOnGitHubKnown().asStudentOnMyCourse();
				LOGGER.info("Writing {}.", student);
				writer.addValue(NAME_COLUMN, student.getLastName());
				writer.addValue(USERNAME_COLUMN, student.getMyCourseUsername());
				writer.addValue(gradeC, formatter.format(grade.getGrade()));
				addFeedback(grade.getAsMyCourseString());
				writer.writeValuesToRow();
			}
			writer.close();
		}
	}

}
