package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableMap;
import io.github.oliviercailloux.grade.IGrade.CriteriaPath;
import java.util.Map;
import java.util.Optional;

/**
 * A tree of MarkAggregator instances. Has information about how to aggregate
 * some marks trees. The other ones (corresponding to those that are rejected)
 * are said to have an incompatible structure. Compatibility depends only on the
 * structure of the marks tree, (i.e., the criteria paths), not on the marks it
 * contains.
 * <p>
 * A grade aggregator at a given node accepts some sets of criteria and rejects
 * others (as determined by its embedded mark aggregator and supplementary
 * constraints set when building the instance). In some cases (static
 * weighting), there is a fixed, finite set of criteria that are accepted, and
 * every other ones are rejected. In that case, the sub-aggregators must be
 * within those known criteria (it is pointless to bind sub-aggregators to the
 * other ones).
 * </p>
 * <p>
 * In every case, it is permitted to give a default sub-aggregator.
 * </p>
 * <p>
 * This aggregator will further reject trees that contain sub-trees at some
 * criterion for which no sub-aggregators are defined.
 * </p>
 * <p>
 * It is permitted to give a default sub-aggregator and to specify
 * sub-aggregators to every accepted criteria (in the case of static weighter),
 * this has the same effect as not giving a default sub-aggregator from the pov
 * of the trees that this aggregator accepts. This class may reorganize things
 * internally so as to use a default sub-aggregator anyway (but still respecting
 * the rejection of trees as mandated).
 * </p>
 */
public class GradeAggregator {
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
		checkArgument(weights.keySet().containsAll(subs.keySet()));
		return new GradeAggregator(new StaticWeighter(weights), subs, Optional.empty());
	}

	private final MarkAggregator markAggregator;
	/**
	 * Every key must be part of some set of criteria accepted by the mark
	 * aggregator. No value equal to the default one.
	 */
	private final ImmutableMap<Criterion, GradeAggregator> subs;
	/**
	 * Empty implies that the criteria sets that are not subset of the subs key set
	 * are rejected.
	 */
	private final Optional<GradeAggregator> defaultSubAggregator;

	private GradeAggregator(MarkAggregator markAggregator, Map<Criterion, GradeAggregator> subs,
			Optional<GradeAggregator> defaultSubAggregator) {
		this.markAggregator = checkNotNull(markAggregator);
		this.subs = defaultSubAggregator.map(d -> subs.keySet().stream().filter(c -> !subs.get(c).equals(d))
				.collect(ImmutableMap.toImmutableMap(c -> c, subs::get))).orElse(ImmutableMap.copyOf(subs));
		this.defaultSubAggregator = checkNotNull(defaultSubAggregator);
	}

	public MarkAggregator getMarkAggregator() {
		return markAggregator;
	}

	/**
	 * This method may return a value for a criterion even though the criterion is
	 * systematically (meaning, whatever set it is part of) rejected during
	 * aggregation.
	 * <p>
	 * Conversely, this method may fail to return an aggregator (because none is
	 * defined) but still accept the criterion when it is associated to a simple
	 * mark.
	 *
	 * @throws IllegalArgumentException only if this aggregator rejects this
	 *                                  criterion systematically (meaning, whatever
	 *                                  set it is part of) or it has no
	 *                                  sub-aggregator defined for the given
	 *                                  criterion.
	 */
	public GradeAggregator getGradeAggregator(Criterion criterion) throws IllegalArgumentException {
		return subs.getOrDefault(criterion, defaultSubAggregator.orElseThrow(IllegalArgumentException::new));
	}

	/**
	 * This method may return a value for a criterion even though the criterion is
	 * systematically (meaning, whatever set it is part of) rejected during
	 * aggregation.
	 *
	 * @throws IllegalArgumentException only if this aggregator rejects some
	 *                                  criterion of the path at the corresponding
	 *                                  node systematically (meaning, whatever set
	 *                                  it is part of)
	 */
	public GradeAggregator getGradeAggregator(CriteriaPath path) {
		if (path.isRoot()) {
			return this;
		}
		return getGradeAggregator(path.getHead()).getGradeAggregator(path.withoutHead());
	}
}
