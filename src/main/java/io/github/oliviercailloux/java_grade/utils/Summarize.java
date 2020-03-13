package io.github.oliviercailloux.java_grade.utils;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import com.google.common.collect.ImmutableMap;

import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinates;
import io.github.oliviercailloux.grade.IGrade;
import io.github.oliviercailloux.grade.WeightingGrade;
import io.github.oliviercailloux.grade.format.CsvGrades;
import io.github.oliviercailloux.grade.format.json.JsonGrade;
import io.github.oliviercailloux.grade.mycourse.json.StudentsReaderFromJson;
import io.github.oliviercailloux.json.JsonbUtils;

public class Summarize {
	private static final Path WORK_DIR = Paths.get("../../Java L3/");
	private static final String PREFIX = "git-br";

	public static void main(String[] args) throws Exception {
		summarize(PREFIX, WORK_DIR);
	}

	public static void summarize(String prefix, Path outDir) throws IOException {
		@SuppressWarnings("all")
		final Type type = new HashMap<RepositoryCoordinates, IGrade>() {
		}.getClass().getGenericSuperclass();

		final Map<String, IGrade> grades = JsonbUtils.fromJson(
				Files.readString(WORK_DIR.resolve("all grades " + prefix + ".json")), type, JsonGrade.asAdapter());

		final StudentsReaderFromJson usernames = new StudentsReaderFromJson();
		usernames.read(WORK_DIR.resolve("usernames.json"));

		final Path outFile = outDir.resolve("all grades " + prefix + ".csv");
		Files.writeString(outFile,
				CsvGrades.asCsv(grades.entrySet().stream().filter(e -> e.getValue() instanceof WeightingGrade)
						.collect(ImmutableMap.toImmutableMap(e -> usernames.getStudentOnGitHub(e.getKey()),
								e -> (WeightingGrade) e.getValue()))));
	}
}
