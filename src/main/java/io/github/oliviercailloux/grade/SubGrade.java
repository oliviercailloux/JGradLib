package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkNotNull;

public record SubGrade(Criterion criterion, Grade grade) {
	public static SubGrade given(Criterion criterion, Grade grade) {
		return new SubGrade(criterion, grade);
	}

	public SubGrade {
		checkNotNull(criterion);
		checkNotNull(grade);
	}
}
