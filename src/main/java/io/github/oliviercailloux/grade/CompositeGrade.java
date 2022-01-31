package io.github.oliviercailloux.grade;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.github.oliviercailloux.grade.IGrade.GradePath;

public class CompositeGrade implements Grade {

	/**
	 * values are either CompositGrade or Mark instances
	 */
	private ImmutableMap<Criterion, SubGrade> subGrades;

	@Override
	public boolean isMark() {
		TODO();
		return false;
	}

	@Override
	public boolean isComposite() {
		TODO();
		return false;
	}

	@Override
	public Grade getGrade(GradePath path) {
		TODO();
		return null;
	}

	@Override
	public Mark getMark(GradePath path) {
		TODO();
		return null;
	}

	@Override
	public ImmutableSet<GradePath> getPathsToMarks() {
		TODO();
		return null;
	}

}
