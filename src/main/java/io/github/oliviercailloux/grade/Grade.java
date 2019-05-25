package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkArgument;
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
 * A grade is aggregated iff it contains sub-grades. Otherwise, it is also
 * called a mark. Comment on an aggregated grade serves typically to explain how
 * a grade has been obtained from its sub-grades (may be empty if obvious), and
 * is not supposed to be a concatenation of underlying comments: this comment is
 * not supposed to be redundant with sub-comments.
 *
 * @author Olivier Cailloux
 *
 */
@JsonbPropertyOrder({ "student", "marks" })
public class Grade implements AnonymousGrade {
	/**
	 * Constructs a Grade which is not a Mark.
	 */
	private Grade(StudentOnGitHub student, Criterion parent, double points, String comment,
			Set<? extends Grade> marks) {
		checkArgument(!marks.isEmpty());
		this.student = student;
		LOGGER.debug("Building with {}, {}.", student, marks.iterator().next().getClass());
		final Collector<Grade, ?, ImmutableBiMap<Criterion, Grade>> toI = ImmutableBiMap
				.toImmutableBiMap((g) -> g.getCriterion(), (g) -> g);
		this.marks = marks.stream().collect(toI);
		this.criterion = parent;
		this.points = points;
		this.comment = comment;
	}

	private final Criterion criterion;

	@Override
	public Criterion getCriterion() {
		return criterion;
	}

	@Override
	@JsonbTransient
	public double getPoints() {
		return points;
	}

	@Override
	public int hashCode() {
		return Objects.hash(criterion, points, comment, student, marks);
	}

	@Override
	public String getComment() {
		return comment;
	}

	/**
	 * May be negative (penalty); may be greater than maxPoints of the corresponding
	 * criterion (bonus).
	 */
	private final double points;
	private final String comment;

	/**
	 * Constructs a Mark.
	 */
	protected Grade(Criterion criterion, double points, String comment) {
		this.criterion = requireNonNull(criterion);
		checkArgument(Double.isFinite(points));
		this.points = points;
		this.comment = requireNonNull(comment);
		this.student = null;
		this.marks = ImmutableBiMap.of();
	}

	@JsonbCreator
	public static Grade of(@JsonbProperty("student") StudentOnGitHub student,
			@JsonbProperty("marks") Set<? extends Grade> marks) {
		return new Grade(requireNonNull(student), Criterion.ROOT_CRITERION,
				marks.stream().collect(Collectors.summingDouble(Grade::getPoints)), "", marks);
	}

	public static AnonymousGrade anonymous(Criterion parent, double points, String comment,
			Set<? extends Grade> subGrades) {
		return new Grade(null, parent, points, comment, subGrades);
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
	private final ImmutableBiMap<Criterion, Grade> marks;

	@Override
	public boolean equals(Object o2) {
		if (!(o2 instanceof Grade)) {
			return false;
		}
		final Grade g2 = (Grade) o2;
		return criterion.equals(g2.criterion) && points == g2.points && comment.equals(g2.comment)
				&& Objects.equals(student, g2.student) && marks.equals(g2.marks);
	}

	@Override
	public String toString() {
		final ToStringHelper helper = MoreObjects.toStringHelper(this);
		if (student != null) {
			helper.add("student", student);
		}
		return helper.add("criterion", criterion).add("points", points).add("comment", comment).add("grades", marks)
				.toString();
	}

	public StudentOnGitHub getStudent() {
		checkState(student != null);
		return student;
	}

	@Override
	@JsonbTransient
	public ImmutableBiMap<Criterion, Grade> getMarks() {
		return marks;
	}

	@JsonbTransient
	public String getAsMyCourseString(double scaleMax) {
		final Stream<String> evaluations = marks.values().stream().map(Grade::getEvaluation);
		final String joined = evaluations.collect(Collectors.joining("</td></tr><tr><td>"));
		return "<p><table><tbody><tr><td>" + joined + "</td></tr></tbody></table></p><p>" + "Grade: "
				+ getScaledGrade(scaleMax) + "/" + scaleMax + ".</p>";
	}

	public double getScaledGrade(double scaleMax) {
		return Math.max(getPoints() / getMaxGrade() * scaleMax, 0d);
	}

	@Override
	@JsonbTransient
	public double getMaxGrade() {
		return marks.values().stream().collect(Collectors.summingDouble((g) -> g.getCriterion().getMaxPoints()));
	}

	public static AnonymousGrade anonymous(Set<? extends Grade> marks) {
		/** FIXME */
		return new Grade(null, Criterion.ROOT_CRITERION,
				marks.stream().collect(Collectors.summingDouble(Grade::getPoints)), "", marks);
	}

	private static String getEvaluation(Grade grade) {
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
