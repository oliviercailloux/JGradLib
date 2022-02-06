package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableSet;
import io.github.oliviercailloux.grade.IGrade.CriteriaPath;
import jakarta.json.bind.annotation.JsonbCreator;
import jakarta.json.bind.annotation.JsonbProperty;
import java.util.NoSuchElementException;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Points are in [−1, 1].
 */
public class Mark implements MarksTree {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(Mark.class);

	public static Mark zero() {
		return new Mark(0d, "");
	}

	public static Mark zero(String comment) {
		return new Mark(0d, comment);
	}

	public static Mark one() {
		return new Mark(1d, "");
	}

	public static Mark one(String comment) {
		return new Mark(1d, comment);
	}

	public static Mark binary(boolean condition) {
		return condition ? one() : zero();
	}

	public static Mark binary(boolean criterion, String okComment, String elseComment) {
		return criterion ? one(okComment) : zero(elseComment);
	}

	@JsonbCreator
	public static Mark given(@JsonbProperty("points") double points, @JsonbProperty("comment") String comment) {
		return new Mark(points, comment);
	}

	private final double points;
	private final String comment;

	public Mark(double points, String comment) {
		checkArgument(Double.isFinite(points));
		checkArgument(points >= -1);
		checkArgument(points <= 1);
		this.points = points;
		this.comment = checkNotNull(comment);
	}

	/**
	 * @return a value in [−1, 1]
	 */
	public double getPoints() {
		return points;
	}

	public String getComment() {
		return comment;
	}

	/**
	 * Returns {@code true}.
	 *
	 * @return {@code true}
	 */
	@Override
	public boolean isMark() {
		return true;
	}

	/**
	 * Returns {@code false}.
	 *
	 * @return {@code false}
	 */
	@Override
	public boolean isComposite() {
		return false;
	}

	/**
	 * Returns the empty set.
	 *
	 * @return the empty set.
	 */
	@Override
	public ImmutableSet<Criterion> getCriteria() {
		return ImmutableSet.of();
	}

	/**
	 * Throws {@code NoSuchElementException}.
	 *
	 * @throws NoSuchElementException always.
	 */
	@Override
	public MarksTree getTree(Criterion criterion) {
		throw new NoSuchElementException();
	}

	@Override
	public ImmutableSet<CriteriaPath> getPathsToMarks() {
		return ImmutableSet.of(CriteriaPath.ROOT);
	}

	/**
	 * Returns this instance.
	 *
	 * @throws NoSuchElementException iff the given path is not
	 *                                {@link CriteriaPath#ROOT}.
	 * @deprecated There is no reason to use this method
	 */
	@Override
	@Deprecated()
	public MarksTree getTree(CriteriaPath path) {
		return getMark(path);
	}

	/**
	 * Returns this instance.
	 *
	 * @throws NoSuchElementException iff the given path is not
	 *                                {@link CriteriaPath#ROOT}.
	 * @deprecated There is no reason to use this method
	 */
	@Override
	@Deprecated
	public Mark getMark(CriteriaPath path) {
		if (!path.isRoot()) {
			throw new NoSuchElementException();
		}
		return this;
	}

	@Override
	public boolean equals(Object o2) {
		if (!(o2 instanceof Mark)) {
			return false;
		}
		final Mark m2 = (Mark) o2;
		return getPoints() == m2.getPoints() && getComment().equals(m2.getComment());
	}

	@Override
	public int hashCode() {
		return Objects.hash(getPoints(), getComment());
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this).add("points", getPoints()).add("comment", getComment()).toString();
	}

}
