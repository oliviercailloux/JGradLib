package io.github.oliviercailloux.grade;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import io.github.oliviercailloux.grade.IGrade.CriteriaPath;
import java.util.Map;

public class WeightingGradeAggregator extends GradeAggregator {

	static final WeightingGradeAggregator TRIVIAL_WEIGHTING = trivial();

	static WeightingGradeAggregator trivial() {
		return new WeightingGradeAggregator(VoidAggregator.INSTANCE, ImmutableMap.of(), null);
	}

	static final WeightingGradeAggregator ABSOLUTE_WEIGHTING = new WeightingGradeAggregator(AbsoluteAggregator.INSTANCE,
			ImmutableMap.of(), TRIVIAL_WEIGHTING);

	public static WeightingGradeAggregator weightingAbsolute(Map<Criterion, WeightingGradeAggregator> subs,
			WeightingGradeAggregator defaultSubAggregator) {
		return new WeightingGradeAggregator(AbsoluteAggregator.INSTANCE, subs, defaultSubAggregator);
	}

	public static WeightingGradeAggregator absolute(WeightingGradeAggregator defaultSubAggregator) {
		return new WeightingGradeAggregator(AbsoluteAggregator.INSTANCE, ImmutableMap.of(), defaultSubAggregator);
	}

	public static WeightingGradeAggregator weightingStaticAggregator(Map<Criterion, Double> weights,
			Map<Criterion, WeightingGradeAggregator> subs) {
		return new WeightingGradeAggregator(new StaticWeighter(weights), subs, TRIVIAL_WEIGHTING);
	}

	public static WeightingGradeAggregator given(PerCriterionWeighter markAggregator,
			Map<Criterion, WeightingGradeAggregator> subs, WeightingGradeAggregator defaultSubAggregator) {
		return new WeightingGradeAggregator(markAggregator, subs, defaultSubAggregator);
	}

	private WeightingGradeAggregator(PerCriterionWeighter markAggregator, Map<Criterion, WeightingGradeAggregator> subs,
			WeightingGradeAggregator defaultSubAggregator) {
		super(markAggregator, subs, defaultSubAggregator);
	}

	/**
	 * @throws AggregatorException iff the given criterion is rejected.
	 */
	public double weight(CriteriaPath path) throws AggregatorException {
		if (path.isRoot()) {
			return 1d;
		}
		return getMarkAggregator().weight(path.getHead())
				* getGradeAggregator(path.getHead()).weight(path.withoutHead());
	}

	@Override
	public PerCriterionWeighter getMarkAggregator() {
		return (PerCriterionWeighter) super.getMarkAggregator();
	}

	@Override
	public WeightingGradeAggregator getGradeAggregator(Criterion criterion) throws AggregatorException {
		return (WeightingGradeAggregator) super.getGradeAggregator(criterion);
	}

	@Override
	public WeightingGradeAggregator getGradeAggregator(CriteriaPath path) throws AggregatorException {
		return (WeightingGradeAggregator) super.getGradeAggregator(path);
	}

	@Override
	public ImmutableMap<Criterion, WeightingGradeAggregator> getSpecialSubAggregators() {
		return ImmutableMap
				.copyOf(Maps.transformValues(super.getSpecialSubAggregators(), a -> (WeightingGradeAggregator) a));
	}

	@Override
	public WeightingGradeAggregator getDefaultSubAggregator() {
		return (WeightingGradeAggregator) super.getDefaultSubAggregator();
	}
}
