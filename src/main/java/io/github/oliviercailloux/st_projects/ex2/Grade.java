package io.github.oliviercailloux.st_projects.ex2;

import static java.util.Objects.requireNonNull;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.json.bind.annotation.JsonbCreator;
import javax.json.bind.annotation.JsonbProperty;
import javax.json.bind.annotation.JsonbPropertyOrder;
import javax.json.bind.annotation.JsonbTransient;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import io.github.oliviercailloux.st_projects.model.StudentOnGitHub;

@JsonbPropertyOrder({ "student", "gradeValues" })
public class Grade {
	private Grade(StudentOnGitHub student, ImmutableMap<GradeCriterion, SingleGrade> grades) {
		this.student = requireNonNull(student);
		this.grades = requireNonNull(grades);
	}

	public static Grade of(StudentOnGitHub student, ImmutableMap<GradeCriterion, SingleGrade> grades) {
		return new Grade(student, grades);
	}

	@JsonbCreator
	public static Grade of(@JsonbProperty("student") StudentOnGitHub student,
			@JsonbProperty("gradeValues") Set<SingleGrade> grades) {
		final Collector<SingleGrade, ?, ImmutableMap<GradeCriterion, SingleGrade>> toI = ImmutableMap
				.toImmutableMap((g) -> g.getCriterion(), (g) -> g);
		grades.stream().forEach((g) -> System.out.println(g));
		final ImmutableMap<GradeCriterion, SingleGrade> im = grades.stream().collect(toI);
		return new Grade(student, im);
	}

	private StudentOnGitHub student;
	/**
	 * points ≤ maxPoints of the corresponding criterion.
	 */
	private final ImmutableMap<GradeCriterion, SingleGrade> grades;

	@Override
	public boolean equals(Object o2) {
		if (!(o2 instanceof Grade)) {
			return false;
		}
		final Grade g2 = (Grade) o2;
		return student.equals(g2.student) && grades.equals(g2.grades);
	}

	@Override
	public int hashCode() {
		return Objects.hash(student, grades);
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this).add("student", student).add("grades", grades).toString();
	}

	public StudentOnGitHub getStudent() {
		return student;
	}

	@JsonbTransient
	public ImmutableMap<GradeCriterion, SingleGrade> getGrades() {
		return grades;
	}

	public ImmutableSet<SingleGrade> getGradeValues() {
		return ImmutableSet.copyOf(grades.values());
	}

	public String getAsMyCourseString() {
		final Stream<String> evaluations = grades.values().stream().map(this::getEvaluation);
		final String joined = evaluations.collect(Collectors.joining("</td></tr><tr><td>"));
		return "<p><table><tbody><tr><td>" + joined + "</td></tr></tbody></table></p><p>" + "Grade: " + getGrade() + "/"
				+ getMaxGrade() + ".</p>";
	}

	public double getGrade() {
		return grades.values().stream().collect(Collectors.summingDouble(SingleGrade::getPoints));
	}

	public double getMaxGrade() {
		return grades.values().stream().collect(Collectors.summingDouble((g) -> g.getCriterion().getMaxPoints()));
	}

	private String getEvaluation(SingleGrade grade) {
		final GradeCriterion criterion = grade.getCriterion();

		final StringBuilder builder = new StringBuilder();
		builder.append(criterion.toString());
		builder.append(" ");
		builder.append("(");
		builder.append(criterion.getRequirement());
		builder.append(")");
		builder.append("</td><td>");
		if (criterion.getMaxPoints() == 0d && grade.getPoints() == 0d) {
			builder.append("OK");
		} else {
			builder.append(grade.getPoints());
			if (criterion.getMaxPoints() != 0d) {
				builder.append("/");
				builder.append(criterion.getMaxPoints());
			}
		}
		if (!grade.getComment().isEmpty()) {
			builder.append("</td><td>");
			builder.append(grade.getComment());
		}
		builder.append(".");

		return builder.toString();
	}
}
