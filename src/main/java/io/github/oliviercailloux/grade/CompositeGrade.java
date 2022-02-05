package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.github.oliviercailloux.grade.IGrade.GradePath;
import java.util.Map;

class CompositeGrade implements Grade {

	public static CompositeGrade givenGrades(Map<Criterion, Grade> subGrades) {
		return new CompositeGrade(subGrades.keySet().stream()
				.collect(ImmutableMap.toImmutableMap(c -> c, c -> SubGrade.given(c, subGrades.get(c)))));
	}

	public static CompositeGrade givenSubGrades(Map<Criterion, SubGrade> subGrades) {
		return new CompositeGrade(subGrades);
	}

	/**
	 * values are either CompositGrade or Mark instances
	 */
	private final ImmutableMap<Criterion, SubGrade> subGrades;

	private CompositeGrade(Map<Criterion, SubGrade> subGrades) {
		this.subGrades = ImmutableMap.copyOf(subGrades);
		checkArgument(!subGrades.isEmpty());
	}

	@Override
	public boolean isMark() {
		return false;
	}

	@Override
	public boolean isComposite() {
		return true;
	}

	@Override
	public ImmutableSet<Criterion> getCriteria() {
		return subGrades.keySet();
	}

	@Override
	public Grade getGrade(Criterion criterion) {
		return getSubGrade(criterion).getGrade();
	}

	@Override
	public SubGrade getSubGrade(Criterion criterion) {
		checkArgument(subGrades.containsKey(criterion));
		return subGrades.get(criterion);
	}

	@Override
	public ImmutableSet<GradePath> getPathsToMarks() {
		final ImmutableSet.Builder<GradePath> builder = ImmutableSet.builder();
		for (Criterion criterion : subGrades.keySet()) {
			getGrade(criterion).getPathsToMarks().stream().map(p -> p.withPrefix(criterion)).forEach(builder::add);
		}
		return builder.build();
	}

	@Override
	public Grade getGrade(GradePath path) {
		if (path.isRoot()) {
			return this;
		}
		return getGrade(path.getHead()).getGrade(path.withoutHead());
	}

	@Override
	public Mark getMark(GradePath path) {
		checkArgument(!path.isRoot());
		return getGrade(path.getHead()).getMark(path.withoutHead());
	}

}
