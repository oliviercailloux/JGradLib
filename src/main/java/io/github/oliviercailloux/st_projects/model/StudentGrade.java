package io.github.oliviercailloux.st_projects.model;

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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableBiMap;

import io.github.oliviercailloux.mycourse.StudentOnGitHub;

@JsonbPropertyOrder({ "student", "marks" })
public class StudentGrade {
	private StudentGrade(StudentOnGitHub student, ImmutableBiMap<Criterion, Mark> marks) {
		this.student = requireNonNull(student);
		this.marks = requireNonNull(marks);
	}

	@JsonbCreator
	public static StudentGrade of(@JsonbProperty("student") StudentOnGitHub student,
			@JsonbProperty("marks") Set<Mark> marks) {
		LOGGER.debug("Building with {}, {}.", student, marks.iterator().next().getClass());
		final Collector<Mark, ?, ImmutableBiMap<Criterion, Mark>> toI = ImmutableBiMap
				.toImmutableBiMap((g) -> g.getCriterion(), (g) -> g);
		final ImmutableBiMap<Criterion, Mark> im = marks.stream().collect(toI);
		return new StudentGrade(student, im);
	}

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(StudentGrade.class);
	private StudentOnGitHub student;
	/**
	 * points ≤ maxPoints of the corresponding criterion.
	 */
	@JsonbTransient
	private final ImmutableBiMap<Criterion, Mark> marks;

	@Override
	public boolean equals(Object o2) {
		if (!(o2 instanceof StudentGrade)) {
			return false;
		}
		final StudentGrade g2 = (StudentGrade) o2;
		return student.equals(g2.student) && marks.equals(g2.marks);
	}

	@Override
	public int hashCode() {
		return Objects.hash(student, marks);
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this).add("student", student).add("grades", marks).toString();
	}

	public StudentOnGitHub getStudent() {
		return student;
	}

	@JsonbTransient
	public ImmutableBiMap<Criterion, Mark> getMarks() {
		return marks;
	}

	Set<Mark> getGradeValues() {
		return marks.values();
	}

	@JsonbTransient
	public String getAsMyCourseString() {
		final Stream<String> evaluations = marks.values().stream().map(this::getEvaluation);
		final String joined = evaluations.collect(Collectors.joining("</td></tr><tr><td>"));
		return "<p><table><tbody><tr><td>" + joined + "</td></tr></tbody></table></p><p>" + "Grade: " + getGrade() + "/"
				+ getMaxGrade() + ".</p>";
	}

	@JsonbTransient
	public double getGrade() {
		return marks.values().stream().collect(Collectors.summingDouble(Mark::getPoints));
	}

	@JsonbTransient
	public double getMaxGrade() {
		return marks.values().stream().collect(Collectors.summingDouble((g) -> g.getCriterion().getMaxPoints()));
	}

	private String getEvaluation(Mark grade) {
		final Criterion criterion = grade.getCriterion();

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

		return builder.toString();
	}
}
