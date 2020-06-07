package io.github.oliviercailloux.java_grade.utils;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

import com.google.common.collect.ImmutableMap;

import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinates;
import io.github.oliviercailloux.grade.IGrade;
import io.github.oliviercailloux.grade.comm.StudentOnGitHub;
import io.github.oliviercailloux.grade.format.CsvGrades;
import io.github.oliviercailloux.grade.format.HtmlGrades;
import io.github.oliviercailloux.grade.format.json.JsonGrade;
import io.github.oliviercailloux.grade.mycourse.json.StudentsReaderFromJson;
import io.github.oliviercailloux.json.JsonbUtils;
import io.github.oliviercailloux.xml.XmlUtils;

public class Summarize {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(Summarize.class);

	private static final Path READ_DIR = Paths.get("../../Java L3/");

	public static void main(String[] args) throws Exception {
		summarize("chess-homework", Paths.get("../../Java L3/"));
	}

	public static void summarize(String prefix, Path outDir) throws IOException {
		@SuppressWarnings("all")
		final Type type = new LinkedHashMap<RepositoryCoordinates, IGrade>() {
		}.getClass().getGenericSuperclass();

		LOGGER.debug("Reading grades.");
		final String sourceGrades = Files.readString(READ_DIR.resolve("all grades " + prefix + ".json"));
		final Map<String, IGrade> grades = JsonbUtils.fromJson(sourceGrades, type, JsonGrade.asAdapter());
		LOGGER.debug("Read {}, keys: {}.", sourceGrades, grades.keySet());

		LOGGER.debug("Reading usernames.");
		final StudentsReaderFromJson usernames = new StudentsReaderFromJson();
		usernames.read(READ_DIR.resolve("usernames.json"));

		final ImmutableMap<StudentOnGitHub, IGrade> byStudent = grades.entrySet().stream().collect(
				ImmutableMap.toImmutableMap(e -> usernames.getStudentOnGitHub(e.getKey()), Map.Entry::getValue));
		LOGGER.debug("Grades keys: {}.", byStudent.keySet());

		LOGGER.info("Writing grades CSV.");
		Files.writeString(outDir.resolve("all grades " + prefix + ".csv"), CsvGrades.asCsv(byStudent, 20d));

		LOGGER.info("Writing grades Html.");
		final Document doc = HtmlGrades.asHtml(grades, "All grades " + prefix, 20d);
		Files.writeString(outDir.resolve("all grades " + prefix + ".html"), XmlUtils.asString(doc));
	}
}
