package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Verify.verify;
import static io.github.oliviercailloux.grade.MarkAggregator.checkCanAggregate;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import io.github.oliviercailloux.grade.IGrade.CriteriaPath;
import java.util.Map;
import java.util.Objects;
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
public class GradeAggregator {
	public static final GradeAggregator TRIVIAL = new GradeAggregator(VoidAggregator.INSTANCE, ImmutableMap.of(), null);

	public static GradeAggregator max(Map<Criterion, GradeAggregator> subs) {
		return new GradeAggregator(new MaxAggregator(), subs, TRIVIAL);
	}

	public static GradeAggregator max(Map<Criterion, GradeAggregator> subs, GradeAggregator defaultSubAggregator) {
		return new GradeAggregator(new MaxAggregator(), subs, defaultSubAggregator);
	}

	public static GradeAggregator max() {
		return new GradeAggregator(new MaxAggregator(), ImmutableMap.of(), TRIVIAL);
	}

	public static GradeAggregator max(GradeAggregator defaultSubAggregator) {
		return new GradeAggregator(new MaxAggregator(), ImmutableMap.of(), defaultSubAggregator);
	}

	public static GradeAggregator parametric(Criterion multiplied, Criterion weighting,
			Map<Criterion, GradeAggregator> subs) {
		return new GradeAggregator(new ParametricWeighter(multiplied, weighting), subs, TRIVIAL);
	}

	public static GradeAggregator parametric(Criterion multiplied, Criterion weighting,
			GradeAggregator multipliedAggregator) {
		return new GradeAggregator(new ParametricWeighter(multiplied, weighting),
				ImmutableMap.of(multiplied, multipliedAggregator), TRIVIAL);
	}

	public static GradeAggregator staticAggregator(Map<Criterion, Double> weights,
			Map<Criterion, GradeAggregator> subs) {
		return new GradeAggregator(new StaticWeighter(weights), subs, TRIVIAL);
	}

	public static GradeAggregator given(MarkAggregator markAggregator, Map<Criterion, GradeAggregator> subs,
			GradeAggregator defaultSubAggregator) {
		return new GradeAggregator(markAggregator, subs, defaultSubAggregator);
	}

	private final MarkAggregator markAggregator;
	/**
	 * Every key must be part of some set of criteria accepted by the mark
	 * aggregator. No value equal to the default one.
	 */
	private final ImmutableMap<Criterion, GradeAggregator> subs;
	/**
	 * {@code null} iff this instance is trivial
	 */
	private final GradeAggregator defaultSubAggregator;

	private GradeAggregator(MarkAggregator markAggregator, Map<Criterion, GradeAggregator> subs,
			GradeAggregator defaultSubAggregator) {
		this.markAggregator = checkNotNull(markAggregator);
		this.subs = ImmutableMap.copyOf(Maps.filterValues(subs, a -> !a.equals(defaultSubAggregator)));

		this.defaultSubAggregator = defaultSubAggregator;
		checkArgument((defaultSubAggregator == null) == (markAggregator instanceof VoidAggregator));

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
		checkNotNull(criterion);
		if (markAggregator instanceof StaticWeighter) {
			final StaticWeighter staticWeighter = (StaticWeighter) markAggregator;
			checkCanAggregate(staticWeighter.weights().containsKey(criterion), "Unknown criterion");
		}
		verify(defaultSubAggregator != null);
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

	public Optional<GradeAggregator> getDefaultSubAggregatorForSerialization() {
		return Optional.ofNullable(defaultSubAggregator);
	}

	/**
	 * Returns {@code true} iff the given object is an aggregator that accepts the
	 * same trees and aggregates them all in the same way as this object.
	 */
	@Override
	public boolean equals(Object o2) {
		if (!(o2 instanceof GradeAggregator)) {
			return false;
		}
		final GradeAggregator t2 = (GradeAggregator) o2;
		return markAggregator.equals(t2.markAggregator) && subs.equals(t2.subs)
				&& Objects.equals(defaultSubAggregator, t2.defaultSubAggregator);
	}

	@Override
	public int hashCode() {
		return Objects.hash(markAggregator, subs, defaultSubAggregator);
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this).add("Mark aggregator", markAggregator).add("Subs", subs)
				.add("default", defaultSubAggregator).toString();
	}
}
