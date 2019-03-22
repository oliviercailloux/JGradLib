package io.github.oliviercailloux.grade.mycourse.csv;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.StringReader;

import com.google.common.collect.ImmutableList;
import com.univocity.parsers.common.record.Record;
import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;

import io.github.oliviercailloux.grade.mycourse.StudentOnMyCourse;

/**
 * The declaration order of the fields here is the order in which MyCourse
 * exports the columns.
 */
public class CsvStudentsOnMyCourse {

	public static ImmutableList<StudentOnMyCourse> asStudentsOnMyCourse(String csvSource) {
		final CsvParser parser = new CsvParser(new CsvParserSettings());
		parser.beginParsing(new StringReader(csvSource));

		final Record header = parser.parseNextRecord();
		checkArgument(LAST_NAME_COLUMN.equals(header.getString(0)), header.getString(0));
		checkArgument(STUDENT_ID_COLUMN.equals(header.getString(1)));
		checkArgument(FIRST_NAME_COLUMN.equals(header.getString(2)));
		checkArgument(USERNAME_COLUMN.equals(header.getString(3)));

		final ImmutableList.Builder<StudentOnMyCourse> students = ImmutableList.builder();
		for (Record record = parser.parseNextRecord(); record != null; record = parser.parseNextRecord()) {
			final String firstName = record.getString(FIRST_NAME_COLUMN);
			final String lastName = record.getString(LAST_NAME_COLUMN);
			final int id = record.getInt(STUDENT_ID_COLUMN);
			final String username = record.getString(USERNAME_COLUMN);
			final StudentOnMyCourse student = StudentOnMyCourse.with(id, firstName, lastName, username);

			students.add(student);
		}
		return students.build();
	}

	public static final String LAST_NAME_COLUMN = "Nom";
	public static final String STUDENT_ID_COLUMN = "Code Étudiant";
	public static final String FIRST_NAME_COLUMN = "Prénom";
	public static final String USERNAME_COLUMN = "Nom d'utilisateur";

}
