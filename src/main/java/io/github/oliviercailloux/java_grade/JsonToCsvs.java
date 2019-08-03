package io.github.oliviercailloux.java_grade;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSet;

import io.github.oliviercailloux.grade.GradeWithStudentAndCriterion;
import io.github.oliviercailloux.grade.format.CsvGrades;
import io.github.oliviercailloux.grade.format.json.JsonGradeWithStudentAndCriterion;
import io.github.oliviercailloux.grade.mycourse.csv.MyCourseCsvWriter;

public class JsonToCsvs {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(JsonToCsvs.class);

	public static void main(String[] args) throws Exception {
		final String workName = "junit";
		final int myCourseId = 112998;
//		final Path srcDir = Paths.get("../../Java SITN, app, concept°/");
		final Path srcDir = Paths.get("../../Java L3/");
		final String namePrefix = "all grades ";
//		final String namePrefix = "patched grades ";
//		final String suffix = " manual";
		final String suffix = " manual merged";

		final String jsonStr = Files.readString(srcDir.resolve(namePrefix + workName + suffix + ".json"));
		final ImmutableSet<GradeWithStudentAndCriterion> grades = JsonGradeWithStudentAndCriterion.asGrades(jsonStr);
		LOGGER.info("First grade: {}.", grades.stream().findFirst());
		Files.writeString(srcDir.resolve("MyCourse.csv"),
				new MyCourseCsvWriter().asMyCourseCsv("Devoir " + workName, myCourseId, grades, 20));
		Files.writeString(srcDir.resolve(namePrefix + workName + ".csv"), CsvGrades.asCsv(grades));
	}
}
