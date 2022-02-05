package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.MoreObjects;
import java.util.Objects;

public class SubGrade {

	public static SubGrade given(Criterion criterion, Grade grade) {
		return new SubGrade(criterion, grade);
	}

	private final Criterion criterion;
	private final Grade grade;

	protected SubGrade(Criterion criterion, Grade grade) {
		this.criterion = checkNotNull(criterion);
		this.grade = checkNotNull(grade);
	}

	public Criterion getCriterion() {
		return criterion;
	}

	public Grade getGrade() {
		return grade;
	}

	@Override
	public boolean equals(Object o2) {
		if (!(o2 instanceof SubGrade)) {
			return false;
		}
		final SubGrade t2 = (SubGrade) o2;
		return criterion.equals(t2.criterion) && grade.equals(t2.grade);
	}

	@Override
	public int hashCode() {
		return Objects.hash(criterion, grade);
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this).add("criterion", criterion).add("grade", grade).toString();
	}
}
