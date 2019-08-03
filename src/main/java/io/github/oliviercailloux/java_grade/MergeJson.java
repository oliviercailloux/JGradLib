package io.github.oliviercailloux.java_grade;

import static com.google.common.base.Preconditions.checkArgument;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Functions;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import io.github.oliviercailloux.grade.CriterionAndPoints;
import io.github.oliviercailloux.grade.GradeWithStudentAndCriterion;
import io.github.oliviercailloux.grade.comm.StudentOnGitHub;
import io.github.oliviercailloux.grade.format.json.JsonGradeWithStudentAndCriterion;
import io.github.oliviercailloux.java_grade.ex_junit.ExJUnitCriterion;

public class MergeJson {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(JsonToCsvs.class);

	public static void main(String[] args) throws Exception {
		final String workName = "junit";
		final Path srcDir = Paths.get("../../Java L3/");
		final String namePrefix = "all grades ";
		final ImmutableSet<GradeWithStudentAndCriterion> grades = JsonGradeWithStudentAndCriterion
				.asGrades(Files.readString(srcDir.resolve(namePrefix + workName + ".json")));
		final ImmutableSet<GradeWithStudentAndCriterion> gradesOverride = JsonGradeWithStudentAndCriterion
				.asGrades(Files.readString(srcDir.resolve(namePrefix + workName + " override" + ".json")));
		final ImmutableMap<StudentOnGitHub, GradeWithStudentAndCriterion> gradesOverrideByStudent = gradesOverride.stream()
				.collect(ImmutableMap.toImmutableMap(GradeWithStudentAndCriterion::getStudent, Functions.identity()));

		final ImmutableSet<GradeWithStudentAndCriterion> gradesMerged = grades.stream()
				.map((g) -> merged(g, gradesOverrideByStudent.get(g.getStudent())))
				.collect(ImmutableSet.toImmutableSet());
		Files.writeString(srcDir.resolve(namePrefix + workName + " manual merged.json"),
				JsonGradeWithStudentAndCriterion.asJsonArray(gradesMerged).toString());
//		LOGGER.info("First grade: {}.", grades.stream().findFirst());
	}

	private static GradeWithStudentAndCriterion merged(GradeWithStudentAndCriterion gradeBase, GradeWithStudentAndCriterion gradeOverride) {
		checkArgument(gradeBase.getStudent().equals(gradeOverride.getStudent()));
		final ImmutableBiMap<CriterionAndPoints, GradeWithStudentAndCriterion> baseMarks = gradeBase.getMarks().values().stream()
				.filter((g) -> !gradeOverride.getMarks().keySet().contains(g.getCriterion()))
				.collect(ImmutableBiMap.toImmutableBiMap(GradeWithStudentAndCriterion::getCriterion, Functions.identity()));
		return GradeWithStudentAndCriterion.of(gradeBase.getStudent(), ImmutableBiMap.<CriterionAndPoints, GradeWithStudentAndCriterion>builder().putAll(baseMarks)
				.putAll(gradeOverride.getMarks()).build().values());
	}

	static GradeWithStudentAndCriterion asFiltered(GradeWithStudentAndCriterion grade) {
		final ImmutableSet<GradeWithStudentAndCriterion> startValues = grade.getMarks().values();
		final ImmutableSet<GradeWithStudentAndCriterion> subMarks = startValues.stream()
				.filter((m) -> m.getCriterion().equals(ExJUnitCriterion.TEST_TESTS))
				.collect(ImmutableSet.toImmutableSet());
		LOGGER.info("Start values: {}, then: {}.", startValues, subMarks);
		return GradeWithStudentAndCriterion.of(grade.getStudent(), subMarks);
	}
}
