package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static io.github.oliviercailloux.grade.MarkAggregator.checkCanAggregate;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import io.github.oliviercailloux.grade.IGrade.CriteriaPath;
import java.util.Map;
import java.util.Optional;

/**
 * A tree of MarkAggregator instances. Has information about how to aggregate
 * some mark trees. The other ones are said to have an incompatible structure.
 * Compatibility depends only on the structure of the mark tree (i.e., the
 * criteria paths), not on the marks it contains.
 * </p>
 * <p>
 * A grade aggregator at a given node accepts some sets of criteria and rejects
 * others. The compatible trees are those such that the set of child criteria at
 * each node is accepted.
 * </p>
 * <p>
 * A grade aggregator is static iff it is bound to a static weighting mark
 * aggregator; dynamic otherwise.
 * </p>
 * <p>
 * A grade aggregator also has sub-aggregators, associated each to a criterion,
 * the set of which is called its sub-criteria. A static grade aggregator
 * sub-criteria equals the set of criteria known to its mark aggregator. A
 * dynamic grade aggregator sub-criteria is the set of all possible criteria.
 * </p>
 * <p>
 * A grade aggregator is trivial iff it is bound to a void mark aggregator,
 * which implies that it is static, and that it has no sub-aggregators.
 * </p>
 * <p>
 * Grade aggregators reject, at a given level, the sets of criteria rejected by
 * their underlying mark aggregators (for example, a static grade aggregator
 * rejects every criteria unknown to its static mark aggregator; a dynamic grade
 * aggregator bound to a ParametricWeighter rejects sets of unsuitable size or
 * content…).
 */
public abstract sealed class GradeAggregator permits GradeAggregator.StaticGradeAggregator,GradeAggregator.DynamicGradeAggregator {
	public static GradeAggregator max(Map<Criterion, GradeAggregator> subs, GradeAggregator defaultSubAggregator) {
		return new GradeAggregator(new MaxAggregator(), subs, Optional.of(defaultSubAggregator));
	}

	public static GradeAggregator max(Map<Criterion, GradeAggregator> subs) {
		return new GradeAggregator(new MaxAggregator(), subs, Optional.empty());
	}

	public static GradeAggregator max(GradeAggregator defaultSubAggregator) {
		return new GradeAggregator(new MaxAggregator(), ImmutableMap.of(), Optional.of(defaultSubAggregator));
	}

	public static GradeAggregator max() {
		return new GradeAggregator(new MaxAggregator(), ImmutableMap.of(), Optional.empty());
	}

	public static GradeAggregator parametric(Criterion multiplied, Criterion weighting,
			Map<Criterion, GradeAggregator> subs) {
		return new GradeAggregator(new ParametricWeighter(multiplied, weighting), subs, Optional.empty());
	}

	public static GradeAggregator parametric(Criterion multiplied, Criterion weighting,
			GradeAggregator multipliedAggregator) {
		return new GradeAggregator(new ParametricWeighter(multiplied, weighting),
				ImmutableMap.of(multiplied, multipliedAggregator), Optional.empty());
	}

	public static GradeAggregator staticAggregator(Map<Criterion, Double> weights,
			Map<Criterion, GradeAggregator> subs) {
		return new GradeAggregator(new StaticWeighter(weights), subs, Optional.empty());
	}

	public static GradeAggregator given(MarkAggregator markAggregator, Map<Criterion, GradeAggregator> subs,
			Optional<GradeAggregator> defaultSubAggregator) {
		return new GradeAggregator(markAggregator, subs, defaultSubAggregator);
	}

	public static final class StaticGradeAggregator extends GradeAggregator {

		private StaticGradeAggregator(StaticWeighter staticWeighter, Map<Criterion, GradeAggregator> subs) {
			super(staticWeighter, subs);
		}

		@Override
		public Optional<GradeAggregator> getDefaultSubAggregatorForSerialization() {
			return Optional.empty();
		}

	}

	public static final class DynamicGradeAggregator extends GradeAggregator {

		private DynamicGradeAggregator(MarkAggregator markAggregator, Map<Criterion, GradeAggregator> subs,
				GradeAggregator defaultSubAggregator) {
			super(markAggregator, Maps.filterValues(subs, a -> !a.equals(defaultSubAggregator)), defaultSubAggregator);
		}

		@Override
		public Optional<GradeAggregator> getDefaultSubAggregatorForSerialization() {
			return defaultSubAggregator;
		}

	}

	private final MarkAggregator markAggregator;
	/**
	 * Every key must be part of some set of criteria accepted by the mark
	 * aggregator. No value equal to the default one.
	 */
	private final ImmutableMap<Criterion, GradeAggregator> subs;
	private final GradeAggregator defaultSubAggregator;

	protected GradeAggregator(MarkAggregator markAggregator, Map<Criterion, GradeAggregator> subs,
			GradeAggregator defaultSubAggregator) {
		this.markAggregator = checkNotNull(markAggregator);
		this.subs = ImmutableMap.copyOf(Maps.filterValues(subs, a -> !a.equals(defaultSubAggregator)));
		this.defaultSubAggregator = checkNotNull(defaultSubAggregator);
		if (markAggregator instanceof StaticWeighter) {
			final StaticWeighter staticWeighter = (StaticWeighter) markAggregator;
			checkArgument(staticWeighter.weights().keySet().containsAll(subs.keySet()));
		}
	}

	public MarkAggregator getMarkAggregator() {
		return markAggregator;
	}

	/**
	 * This method may fail to return an aggregator (because none is defined) even
	 * if this aggregator would anyway accept the criterion when it is associated to
	 * a simple mark.
	 *
	 * @throws AggregatorException iff this aggregator rejects this criterion
	 *                             systematically (meaning, whatever set it is part
	 *                             of); equivalently, iff this aggregator is a
	 *                             static aggregator and the criterion is unknown to
	 *                             its static wegighter; implying that for trivial
	 *                             aggregators, his method throws whatever its
	 *                             argument.
	 */
	public GradeAggregator getGradeAggregator(Criterion criterion) throws AggregatorException {
		if (markAggregator instanceof StaticWeighter) {
			final StaticWeighter staticWeighter = (StaticWeighter) markAggregator;
			checkCanAggregate(staticWeighter.weights().containsKey(criterion), "Unknown criterion");
		}
		return subs.getOrDefault(criterion, defaultSubAggregator);
	}

	public GradeAggregator getGradeAggregator(CriteriaPath path) {
		if (path.isRoot()) {
			return this;
		}
		return getGradeAggregator(path.getHead()).getGradeAggregator(path.withoutHead());
	}

	public ImmutableMap<Criterion, GradeAggregator> getSubAggregatorsForSerialization() {
		return subs;
	}

	public GradeAggregator getDefaultSubAggregatorForSerialization() {
		return defaultSubAggregator;
	}
}
