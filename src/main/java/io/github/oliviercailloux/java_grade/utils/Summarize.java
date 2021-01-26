package io.github.oliviercailloux.java_grade.utils;

import static com.google.common.base.Verify.verify;

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
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;

import io.github.oliviercailloux.git.git_hub.model.GitHubUsername;
import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinates;
import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.CriterionGradeWeight;
import io.github.oliviercailloux.grade.IGrade;
import io.github.oliviercailloux.grade.Mark;
import io.github.oliviercailloux.grade.WeightingGrade;
import io.github.oliviercailloux.grade.comm.StudentOnGitHub;
import io.github.oliviercailloux.grade.comm.json.JsonStudentsReader;
import io.github.oliviercailloux.grade.format.CsvGrades;
import io.github.oliviercailloux.grade.format.HtmlGrades;
import io.github.oliviercailloux.grade.format.json.JsonGrade;
import io.github.oliviercailloux.json.JsonbUtils;
import io.github.oliviercailloux.xml.XmlUtils;

public class Summarize {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(Summarize.class);

	private static final Path READ_DIR = Paths.get("");

	public static void main(String[] args) throws Exception {
		summarize("eclipse", Paths.get(""), true);
	}

	public static void summarize(String prefix, Path outDir, boolean ignoreZeroWeights) throws IOException {
		@SuppressWarnings("all")
		final Type type = new LinkedHashMap<RepositoryCoordinates, IGrade>() {
		}.getClass().getGenericSuperclass();

		LOGGER.debug("Reading grades.");
		final String sourceGrades = Files.readString(READ_DIR.resolve("grades " + prefix + " patched.json"));
		final Map<String, IGrade> grades = JsonbUtils.fromJson(sourceGrades, type, JsonGrade.asAdapter());
		LOGGER.debug("Read {}, keys: {}.", sourceGrades, grades.keySet());

		final ImmutableMap<String, IGrade> transformed;
		if (ignoreZeroWeights) {
			transformed = grades.entrySet().stream().collect(
					ImmutableMap.toImmutableMap(Map.Entry::getKey, e -> withTimePenalty(nonZero(e.getValue()))));
		} else {
			transformed = ImmutableMap.copyOf(grades);
		}
		final ImmutableSet<GitHubUsername> gradesUsernames = transformed.keySet().stream().map(GitHubUsername::given)
				.collect(ImmutableSet.toImmutableSet());
		LOGGER.debug("Reading usernames.");
		final JsonStudentsReader students = JsonStudentsReader
				.from(Files.readString(READ_DIR.resolve("usernames.json")));
		final ImmutableMap<GitHubUsername, StudentOnGitHub> usernames = students.getStudentsByGitHubUsername();
		final ImmutableSet<GitHubUsername> missing = Sets.difference(gradesUsernames, usernames.keySet())
				.immutableCopy();
		if (!missing.isEmpty()) {
			LOGGER.warn("Missing: {}.", missing);
		}
//
		final ImmutableMap<StudentOnGitHub, IGrade> byStudent = transformed.entrySet().stream().collect(
				ImmutableMap.toImmutableMap(e -> usernames.get(GitHubUsername.given(e.getKey())), Map.Entry::getValue));
//		final ImmutableMap<StudentOnGitHub, IGrade> byStudent = transformed.entrySet().stream()
//				.collect(ImmutableMap.toImmutableMap(e -> StudentOnGitHub.with(e.getKey()), Map.Entry::getValue));
		LOGGER.debug("Grades keys: {}.", byStudent.keySet());

		LOGGER.info("Writing grades CSV.");
		Files.writeString(outDir.resolve("grades " + prefix + ".csv"), CsvGrades.asCsv(byStudent, 20d));

		LOGGER.info("Writing grades Html.");
		final Document doc = HtmlGrades.asHtml(transformed, "All grades " + prefix, 20d);
		Files.writeString(outDir.resolve("grades " + prefix + ".html"), XmlUtils.asString(doc));
	}

	public static IGrade nonZero(IGrade grade) {
		if (!(grade instanceof WeightingGrade)) {
			return grade;
		}

		final WeightingGrade w = (WeightingGrade) grade;
		final ImmutableSet<CriterionGradeWeight> subGrades = w.getSubGradesAsSet().stream()
				.filter(g -> g.getWeight() != 0d)
				.map(g -> CriterionGradeWeight.from(g.getCriterion(), nonZero(g.getGrade()), g.getWeight()))
				.collect(ImmutableSet.toImmutableSet());
		verify(!subGrades.isEmpty());
		if (subGrades.size() == 1) {
			final IGrade subGradesOnlyGrade = Iterables.getOnlyElement(subGrades).getGrade();
			LOGGER.info("Instead of {}, returning {}.", w, subGradesOnlyGrade);
			return subGradesOnlyGrade;
		}
		return w;
	}

	public static IGrade withTimePenalty(IGrade grade) {
		if (!(grade instanceof WeightingGrade)) {
			return grade;
		}

		/**
		 * More general: should observe that the small trees (a/b/c) are included in the
		 * large trees (g/a/b/c + t) and align them with zero or one weight grades
		 */
		final WeightingGrade w = (WeightingGrade) grade;
		if (w.getSubGrades().keySet().contains(Criterion.given("Time penalty"))) {
			return w;
		}
		return WeightingGrade.from(ImmutableSet.of(CriterionGradeWeight.from(Criterion.given("grade"), grade, 1d),
				CriterionGradeWeight.from(Criterion.given("Time penalty"), Mark.one("No time penalty"), 0d)));
	}
}
