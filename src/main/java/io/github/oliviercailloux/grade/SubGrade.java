package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkNotNull;

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
}
