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

import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.Grade;
import io.github.oliviercailloux.grade.json.JsonGrade;
import io.github.oliviercailloux.grade.mycourse.StudentOnGitHub;
import io.github.oliviercailloux.java_grade.ex_junit.ExJUnitCriterion;

public class MergeJson {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(JsonToCsvs.class);

	public static void main(String[] args) throws Exception {
		final String workName = "junit";
		final Path srcDir = Paths.get("../../Java L3/");
		final String namePrefix = "all grades ";
		final ImmutableSet<Grade> grades = JsonGrade
				.asGrades(Files.readString(srcDir.resolve(namePrefix + workName + ".json")));
		final ImmutableSet<Grade> gradesOverride = JsonGrade
				.asGrades(Files.readString(srcDir.resolve(namePrefix + workName + " override" + ".json")));
		final ImmutableMap<StudentOnGitHub, Grade> gradesOverrideByStudent = gradesOverride.stream()
				.collect(ImmutableMap.toImmutableMap(Grade::getStudent, Functions.identity()));

		final ImmutableSet<Grade> gradesMerged = grades.stream()
				.map((g) -> merged(g, gradesOverrideByStudent.get(g.getStudent())))
				.collect(ImmutableSet.toImmutableSet());
		Files.writeString(srcDir.resolve(namePrefix + workName + " manual merged.json"),
				JsonGrade.asJsonArray(gradesMerged).toString());
//		LOGGER.info("First grade: {}.", grades.stream().findFirst());
	}

	private static Grade merged(Grade gradeBase, Grade gradeOverride) {
		checkArgument(gradeBase.getStudent().equals(gradeOverride.getStudent()));
		final ImmutableBiMap<Criterion, Grade> baseMarks = gradeBase.getMarks().values().stream()
				.filter((g) -> !gradeOverride.getMarks().keySet().contains(g.getCriterion()))
				.collect(ImmutableBiMap.toImmutableBiMap(Grade::getCriterion, Functions.identity()));
		return Grade.of(gradeBase.getStudent(), ImmutableBiMap.<Criterion, Grade>builder().putAll(baseMarks)
				.putAll(gradeOverride.getMarks()).build().values());
	}

	static Grade asFiltered(Grade grade) {
		final ImmutableSet<Grade> startValues = grade.getMarks().values();
		final ImmutableSet<Grade> subMarks = startValues.stream()
				.filter((m) -> m.getCriterion().equals(ExJUnitCriterion.TEST_TESTS))
				.collect(ImmutableSet.toImmutableSet());
		LOGGER.info("Start values: {}, then: {}.", startValues, subMarks);
		return Grade.of(grade.getStudent(), subMarks);
	}
}
