package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Verify.verify;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

class FixedWeightsGradeStructure implements GradeStructure {

	private final ImmutableMap<Criterion, Double> weights;
	private final ImmutableMap<Criterion, GradeStructure> subStructures;

	FixedWeightsGradeStructure(Map<Criterion, Double> weights, Map<Criterion, GradeStructure> subStructures) {
		this.weights = ImmutableMap.copyOf(weights);
		this.subStructures = ImmutableMap.copyOf(subStructures);
	}

	@Override
	public boolean isAbsolute(Criterion criterion) {
		return !weights.containsKey(criterion);
	}

	@Override
	public GradeStructure getStructure(Criterion criterion) {
		checkArgument(subStructures.containsKey(criterion));
		return subStructures.get(criterion);
	}

	@Override
	public ImmutableMap<Criterion, Double> getFixedWeights() {
		return weights;
	}

	@Override
	public ImmutableMap<SubMark, Double> getWeights(Set<SubMark> subMarks) {
		final ImmutableSet<Criterion> criteria = subMarks.stream().map(SubMark::getCriterion)
				.collect(ImmutableSet.toImmutableSet());
		checkArgument(subMarks.size() == criteria.size());
		checkArgument(criteria.stream().noneMatch(this::isAbsolute));
		verify(weights.keySet().containsAll(criteria));
		return subMarks.stream()
				.collect(ImmutableMap.toImmutableMap(Function.identity(), s -> weights.get(s.getCriterion())));
	}

	@Override
	public Mark getMark(Set<SubMark> subMarks) {
		return StructuredGrade.getMark(this, subMarks);
	}

}
