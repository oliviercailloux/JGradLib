package io.github.oliviercailloux.java_grade;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import io.github.oliviercailloux.grade.comm.StudentOnGitHubKnown;
import io.github.oliviercailloux.grade.comm.StudentOnMyCourse;
import io.github.oliviercailloux.grade.mycourse.csv.CsvStudentsOnMyCourse;
import io.github.oliviercailloux.grade.mycourse.json.JsonStudentOnGitHubKnown;
import io.github.oliviercailloux.grade.mycourse.json.JsonStudentOnMyCourse;
import io.github.oliviercailloux.grade.mycourse.other_formats.MyCourseHomeworkReader;
import io.github.oliviercailloux.json.PrintableJsonValue;

public class WriteUsernamesFromHomeworks {
	public static void main(String[] args) throws IOException {
		final Path l3Path = Paths.get("/home/olivier/Professions/Enseignement/Java L3");
		final ImmutableList<StudentOnMyCourse> studentsOnMyCourse = CsvStudentsOnMyCourse
				.asStudentsOnMyCourse(new String(
						Files.readAllBytes(
								l3Path.resolve("gc_18_A3MID-403_A3MADA-403_A3APP15_fullgc_2019-03-13-14-06-03.csv")),
						StandardCharsets.UTF_8));
		LOGGER.info("Read from MyCourse students: {}.", studentsOnMyCourse);
		final ImmutableList<StudentOnGitHubKnown> studentsOnGitHubKnown = MyCourseHomeworkReader
				.readGitHubUsernames("Votre nom d’utilisateur", l3Path, studentsOnMyCourse);
		LOGGER.info("Read from Homeworks: {}.", studentsOnGitHubKnown);

		{
			final PrintableJsonValue json = JsonStudentOnGitHubKnown.asJsonFromList(studentsOnGitHubKnown);
			Files.write(Paths.get("usernamesGH.json"), json.toString().getBytes(StandardCharsets.UTF_8));
		}
		{
			final PrintableJsonValue json = JsonStudentOnMyCourse.asJsonFromList(studentsOnMyCourse);
			Files.write(Paths.get("students.json"), json.toString().getBytes(StandardCharsets.UTF_8));
		}
		final ImmutableSet<StudentOnMyCourse> asMyCourseAndKnown = studentsOnGitHubKnown.stream()
				.map((s) -> s.asStudentOnMyCourse()).collect(ImmutableSet.toImmutableSet());
		LOGGER.info("Missing: {}.",
				Sets.symmetricDifference(asMyCourseAndKnown, ImmutableSet.copyOf(studentsOnMyCourse)));
	}

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(WriteUsernamesFromHomeworks.class);
}
