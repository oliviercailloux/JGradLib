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
import com.google.common.primitives.Booleans;

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
	private Grade(StudentOnGitHub student, Set<? extends Grade> marks) {
		this.student = student;
		LOGGER.debug("Building with {}, {}.", student, marks.iterator().next().getClass());
		final Collector<Grade, ?, ImmutableBiMap<Criterion, Grade>> toI = ImmutableBiMap
				.toImmutableBiMap((g) -> g.getCriterion(), (g) -> g);
		this.marks = marks.stream().collect(toI);
		/** FIXME */
		this.criterion = null;
		this.points = getGrade();
		this.comment = "";
	}

	private final Criterion criterion;

	@Override
	public Criterion getCriterion() {
		return criterion;
	}

	@Override
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

	public static Grade proportional(Criterion criterion, boolean firstTest, boolean... tests) {
		final int nbTests = 1 + tests.length;
		final int nbOk = (firstTest ? 1 : 0) + Booleans.countTrue(tests);
		return proportional(criterion, nbOk, nbTests);
	}

	public static Grade proportional(Criterion criterion, int nbOk, int nbTests) {
		return proportional(criterion, nbOk, nbTests, "nbOk (" + nbOk + ") / nbTests (" + nbTests + ")");
	}

	public static Grade proportional(Criterion criterion, int nbOk, int nbTests, String comment) {
		final double weightOk = (double) nbOk / (double) nbTests;
		final double weightKo = 1d - weightOk;
		final double pts = criterion.getMinPoints() * weightKo + criterion.getMaxPoints() * weightOk;
		LOGGER.debug("For {}, obtained proportion of {} right and {} wrong, points are {}.", criterion, weightOk,
				weightKo, pts);
		return Grade.of(criterion, pts, comment);
	}

	public static Grade min(Criterion criterion) {
		return new Grade(criterion, criterion.getMinPoints(), "");
	}

	@JsonbCreator
	public static Grade of(@JsonbProperty("criterion") Criterion criterion, @JsonbProperty("points") double points,
			@JsonbProperty("comment") String comment) {
		final Grade g = new Grade(criterion, points, comment);
		return g;
	}

	public static Grade binary(Criterion criterion, boolean conditionForPoints) {
		return new Grade(criterion, conditionForPoints ? criterion.getMaxPoints() : criterion.getMinPoints(), "");
	}

	/**
	 * May be negative (penalty); may be greater than maxPoints of the corresponding
	 * criterion (bonus).
	 */
	private final double points;
	private final String comment;

	Grade(Criterion criterion, double points, String comment) {
		this.criterion = requireNonNull(criterion);
		checkArgument(Double.isFinite(points));
		this.points = points;
		this.comment = requireNonNull(comment);
		this.student = null;
		this.marks = ImmutableBiMap.of();
	}

	public static Grade min(Criterion criterion, String comment) {
		return new Grade(criterion, criterion.getMinPoints(), comment);
	}

	public static Grade max(Criterion criterion) {
		return new Grade(criterion, criterion.getMaxPoints(), "");
	}

	@JsonbCreator
	public static Grade of(@JsonbProperty("student") StudentOnGitHub student,
			@JsonbProperty("marks") Set<? extends Grade> marks) {
		return new Grade(requireNonNull(student), marks);
	}

	public static AnonymousGrade anonymous(Set<? extends Grade> marks) {
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

	@JsonbTransient
	public ImmutableBiMap<Criterion, Grade> getGrades() {
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
		return Math.max(getGrade() / getMaxGrade() * scaleMax, 0d);
	}

	@Override
	@JsonbTransient
	public double getGrade() {
		return marks.values().stream().collect(Collectors.summingDouble(Grade::getPoints));
	}

	@Override
	@JsonbTransient
	public double getMaxGrade() {
		return marks.values().stream().collect(Collectors.summingDouble((g) -> g.getCriterion().getMaxPoints()));
	}

	@Override
	@JsonbTransient
	public ImmutableBiMap<Criterion, Mark> getMarks() {
		return marks.entrySet().stream()
				.collect(ImmutableBiMap.toImmutableBiMap((e) -> e.getKey(), (e) -> (Mark) e.getValue()));
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
