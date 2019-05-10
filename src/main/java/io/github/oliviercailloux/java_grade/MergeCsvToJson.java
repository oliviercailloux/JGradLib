package io.github.oliviercailloux.java_grade;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableSet;

import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.CsvGrades;
import io.github.oliviercailloux.grade.Grade;
import io.github.oliviercailloux.grade.Grade;
import io.github.oliviercailloux.grade.json.JsonGrade;
import io.github.oliviercailloux.java_grade.ex_jpa.ExJpaCriterion;

public class MergeCsvToJson {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(MergeCsvToJson.class);

	public static void main(String[] args) throws Exception {
		final String prefix = "jpa";
		final Path srcDir = Paths.get("../../Java SITN, app, concept°/");

		final String jsonStr = Files.readString(srcDir.resolve("all grades " + prefix + ".json"));
		final ImmutableSet<Grade> gradesStart = JsonGrade.asGrades(jsonStr);
		LOGGER.info("First grade: {}.", gradesStart.stream().findFirst());
		final ImmutableSet<Grade> patchGrades = CsvGrades.fromCsv(
				Files.newInputStream(srcDir.resolve("patch grades " + prefix + ".csv")),
				(s) -> ExJpaCriterion.valueOf(s));
		LOGGER.info("First patch: {}.", patchGrades.stream().findFirst());
		final ImmutableBiMap<String, Grade> patchGradesByStudent = patchGrades.stream()
				.collect(ImmutableBiMap.toImmutableBiMap((g) -> g.getStudent().getGitHubUsername(), (g) -> g));
		final ImmutableBiMap<String, Grade> patchedGradesByStudent = gradesStart.stream()
				.collect(ImmutableBiMap.toImmutableBiMap((g) -> g.getStudent().getGitHubUsername(),
						(startGrade) -> Optional
								.ofNullable(patchGradesByStudent.get(startGrade.getStudent().getGitHubUsername()))
								.map((pg) -> merge(startGrade, pg)).orElse(startGrade)));
		final String st = "dostaric";
		LOGGER.info("Start grade (?), patch grade: {}, patched grade: {}.", patchGradesByStudent.get(st),
				patchedGradesByStudent.get(st));
		Files.writeString(srcDir.resolve("patched grades " + prefix + ".json"),
				JsonGrade.asJsonArray(patchedGradesByStudent.values()).toString());
	}

	public static Grade merge(Grade grade, Grade patchGrade) {
		final ImmutableBiMap<Criterion, Grade> marksStart = grade.getMarks();
		final ImmutableBiMap<Criterion, Grade> marksOverride = patchGrade.getMarks();
		final ImmutableBiMap<Criterion, Grade> mergedMarks = marksStart.values().stream()
				.collect(ImmutableBiMap.toImmutableBiMap((m) -> m.getCriterion(),
						(m) -> Optional.ofNullable(marksOverride.get(m.getCriterion())).orElse(m)));
		return Grade.of(grade.getStudent(), mergedMarks.values());
	}
}
