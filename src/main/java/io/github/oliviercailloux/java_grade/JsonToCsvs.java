package io.github.oliviercailloux.java_grade;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSet;

import io.github.oliviercailloux.grade.CsvGrades;
import io.github.oliviercailloux.grade.Grade;
import io.github.oliviercailloux.grade.json.JsonGrade;
import io.github.oliviercailloux.grade.mycourse.csv.MyCourseCsvWriter;

public class JsonToCsvs {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(JsonToCsvs.class);

	public static void main(String[] args) throws Exception {
		final String workName = "extractor";
		final int myCourseId = 112539;
//		final Path srcDir = Paths.get("../../Java SITN, app, concept°/");
		final Path srcDir = Paths.get("../../Java L3/");
		final String namePrefix = "all grades ";
//		final String namePrefix = "patched grades ";

		final String jsonStr = Files.readString(srcDir.resolve(namePrefix + workName + " manual.json"));
		final ImmutableSet<Grade> grades = JsonGrade.asGrades(jsonStr);
		LOGGER.info("First grade: {}.", grades.stream().findFirst());
		Files.writeString(srcDir.resolve("MyCourse.csv"),
				new MyCourseCsvWriter().asMyCourseCsv("Devoir " + workName, myCourseId, grades, 20));
		Files.writeString(srcDir.resolve(namePrefix + workName + ".csv"), CsvGrades.asCsv(grades));
	}
}