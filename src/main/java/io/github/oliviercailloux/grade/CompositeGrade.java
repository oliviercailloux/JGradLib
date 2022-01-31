package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.github.oliviercailloux.grade.IGrade.GradePath;
import java.util.Map;

class CompositeGrade implements Grade {

	public static CompositeGrade given(Map<Criterion, SubGrade> subGrades) {
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
		return getSubGrade(criterion).grade();
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
			getSubGrade(criterion).grade().getPathsToMarks().stream().map(p -> p.withPrefix(criterion))
					.forEach(builder::add);
		}
		return builder.build();
	}

	@Override
	public Grade getGrade(GradePath path) {
		if (path.isRoot()) {
			return this;
		}
		return getSubGrade(path.getHead()).grade().getGrade(path.withoutHead());
	}

	@Override
	public Mark getMark(GradePath path) {
		checkArgument(!path.isRoot());
		return getSubGrade(path.getHead()).grade().getMark(path.withoutHead());
	}

}
