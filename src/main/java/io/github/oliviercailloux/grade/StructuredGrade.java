package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Verify.verify;

public class StructuredGrade {
	public static StructuredGrade given(Grade grade, GradeStructure structure) {
		return new StructuredGrade(grade, structure);
	}

	private static void checkCanAggregate(Grade grade, GradeStructure structure) {
		checkArgument(structure.getOrderedCriteria().containsAll(grade.getCriteria()));
		for (Criterion criterion : grade.getCriteria()) {
			final Grade subGrade = grade.getGrade(criterion);
			final GradeStructure subStructure = structure.getStructure(criterion);
			checkCanAggregate(subGrade, subStructure);
		}

		/*
		 * This could be relaxed by using zero for missing absolute values,
		 * renormalizing weights, and extending owaStrs.
		 */
		checkArgument(grade.getCriteria().containsAll(structure.getOrderedCriteria()));
	}

	private final Grade grade;
	private final GradeStructure structure;

	private StructuredGrade(Grade grade, GradeStructure structure) {
		this.grade = checkNotNull(grade);
		this.structure = checkNotNull(structure);
		checkCanAggregate(grade, structure);
	}

	public boolean isAbsolute(Criterion criterion) {
		return structure.isAbsolute(criterion);
	}

	/**
	 * @return one if absolute
	 */
	public double getWeight(Criterion criterion) {
		if (isAbsolute(criterion)) {
			return 1d;
		}

		if(structure.hasWeight(criterion)) {
			return structure.getWeight(criterion);
		}

		verify(structure.isInOwa(criterion));
		final OwaStructure owaStructure = structure.getOwaStructure(criterion);
		owa
	}

	public StructuredGrade getStructuredGrade(Criterion criterion) {

	}

	/**
	 * @return the points at the root
	 */
	public Mark getRootMark() {

	}

}
