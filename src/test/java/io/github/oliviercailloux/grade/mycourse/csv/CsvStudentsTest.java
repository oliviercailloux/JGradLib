package io.github.oliviercailloux.grade.mycourse.csv;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;

import io.github.oliviercailloux.grade.mycourse.StudentOnMyCourse;

class CsvStudentsTest {

	@Test
	void test() throws IOException {
		final URL sourceUrl = getClass().getResource("From MyCourse.csv");
		final String source = Resources.toString(sourceUrl, StandardCharsets.UTF_8);
		final ImmutableList<StudentOnMyCourse> students = CsvStudentsOnMyCourse.asStudentsOnMyCourse(source);
		assertEquals(1, students.size());
		assertEquals(StudentOnMyCourse.with(31803737, "First name 1", "Name1", "username 1"), students.get(0));
	}

}
