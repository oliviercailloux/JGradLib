package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkState;
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
import com.google.common.base.MoreObjects.ToStringHelper;
import com.google.common.collect.ImmutableBiMap;

import io.github.oliviercailloux.grade.mycourse.StudentOnGitHub;

/**
 *
 * A grade knows the relative weights of the criteria (or, TODO, more generally,
 * the sub-grades) that it is composed of. But it does not know which fraction
 * it should preferably use for its own display: this is known by the user of
 * the grade at display time. It relates to display and not to grade information
 * per se.
 *
 * TODO get rid of student and of {@link AnonymousGrade}: use Map<Student,
 * Grade> or Map<URL, Grade> and so on.
 *
 * @author Olivier Cailloux
 *
 */
@JsonbPropertyOrder({ "student", "marks" })
public class Grade implements AnonymousGrade {
	private Grade(StudentOnGitHub student, Set<Mark> marks) {
		this.student = student;
		LOGGER.debug("Building with {}, {}.", student, marks.iterator().next().getClass());
		final Collector<Mark, ?, ImmutableBiMap<Criterion, Mark>> toI = ImmutableBiMap
				.toImmutableBiMap((g) -> g.getCriterion(), (g) -> g);
		this.marks = marks.stream().collect(toI);
	}

	@JsonbCreator
	public static Grade of(@JsonbProperty("student") StudentOnGitHub student, @JsonbProperty("marks") Set<Mark> marks) {
		return new Grade(requireNonNull(student), marks);
	}

	public static AnonymousGrade anonymous(Set<Mark> marks) {
		return new Grade(null, marks);
	}

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(Grade.class);
	/**
	 * <code>null</code> iff this object is an {@link AnonymousGrade}.
	 */
	private StudentOnGitHub student;
	/**
	 * points ≤ maxPoints of the corresponding criterion.
	 */
	@JsonbTransient
	private final ImmutableBiMap<Criterion, Mark> marks;

	@Override
	public boolean equals(Object o2) {
		if (!(o2 instanceof Grade)) {
			return false;
		}
		final Grade g2 = (Grade) o2;
		return Objects.equals(student, g2.student) && marks.equals(g2.marks);
	}

	@Override
	public int hashCode() {
		return Objects.hash(student, marks);
	}

	@Override
	public String toString() {
		final ToStringHelper helper = MoreObjects.toStringHelper(this);
		if (student != null) {
			helper.add("student", student);
		}
		return helper.add("grades", marks).toString();
	}

	public StudentOnGitHub getStudent() {
		checkState(student != null);
		return student;
	}

	@Override
	@JsonbTransient
	public ImmutableBiMap<Criterion, Mark> getMarks() {
		return marks;
	}

	@JsonbTransient
	public String getAsMyCourseString(double scaleMax) {
		final Stream<String> evaluations = marks.values().stream().map(this::getEvaluation);
		final String joined = evaluations.collect(Collectors.joining("</td></tr><tr><td>"));
		return "<p><table><tbody><tr><td>" + joined + "</td></tr></tbody></table></p><p>" + "Grade: "
				+ getScaledGrade(scaleMax) + "/" + scaleMax + ".</p>";
	}

	public double getScaledGrade(double scaleMax) {
		return Math.max(getGrade() / getMaxGrade() * scaleMax, 0d);
	}

	@Override
	@JsonbTransient
	public double getGrade() {
		return marks.values().stream().collect(Collectors.summingDouble(Mark::getPoints));
	}

	@Override
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
		LOGGER.debug("Evaluation for {}: max={}, points={}.", grade, criterion.getMaxPoints(), grade.getPoints());
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
