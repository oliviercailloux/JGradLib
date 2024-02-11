package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Verify.verify;

import com.google.common.base.MoreObjects;
import com.google.common.base.VerifyException;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.MoreCollectors;
import com.google.common.math.DoubleMath;
import io.github.oliviercailloux.grade.IGrade.CriteriaPath;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A tree with marks at each node, plus, for non-leaf nodes, the ability to explain the aggregation
 * strategy used to obtain them.
 * <p>
 * Equivalent to a single mark iff there are no children in the (root of the) tree.
 * </p>
 */
public class Grade {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(Grade.class);

	public static Grade transformToPerCriterionWeighting(Grade original) {
		/*
		 * We want to transform aggregator and marks tree so that the aggregator still accepts every
		 * tree it originally accepted and the resulting mark is the same, while losing as few
		 * information as possible. We want the transformed aggregation to have fixed weights, in the
		 * sense that it gives to every node a weight that is independent of the marks tree (i.e., a
		 * weight that depends only on the aggregator).
		 *
		 * Unfortunately, it is not sufficient to transform a grade into a grade by visiting
		 * simultaneously the marks tree and the aggregator tree: this might transform aggregators
		 * differently (from one user to another, even if they start with the same aggregator). That is
		 * because different trees stop at different places due to their limited depths, so this method
		 * would not ensure exploration of the whole aggregator.
		 */
		final WeightingGradeAggregator transformedAggregator =
				transformToPerCriterionWeighting(original.toAggregator());
		final MarksTree transformedMarks = adaptMarksForPerCriterionWeighting(original);
		final Grade transformed = Grade.given(transformedAggregator, transformedMarks);
		verify(
				DoubleMath.fuzzyEquals(original.mark().getPoints(), transformed.mark().getPoints(), 1e-6d));
		return transformed;
	}

	public static WeightingGradeAggregator
			transformToPerCriterionWeighting(GradeAggregator original) {
		if (original instanceof WeightingGradeAggregator w) {
			return w;
		}
		final MarkAggregator a = original.getMarkAggregator();

		if ((a instanceof MaxAggregator || a instanceof MinAggregator)
				&& original.getSpecialSubAggregators().isEmpty()) {
			return transformToPerCriterionWeighting(original.getDefaultSubAggregator());
		}

		/*
		 * Note that switching, for example, from ParametricWeighter to AbsoluteAggregator extends the
		 * admissible sub-trees.
		 */
		final ImmutableMap<Criterion, ? extends GradeAggregator> specialSubs =
				original.getSpecialSubAggregators();
		/*
		 * Some further pruning is possible (and might be welcome) as some of these sub-aggregators will
		 * never be used: in parametric nodes, the weighting branch will not be used as the
		 * corresponding marks tree is reduced to an absolute mark; and max nodes treated here become
		 * terminal nodes in marks trees.
		 */
		final Map<Criterion, WeightingGradeAggregator> transformedSubs =
				Maps.transformValues(specialSubs, Grade::transformToPerCriterionWeighting);
		final PerCriterionWeighter newAggregator =
				(a instanceof PerCriterionWeighter c) ? c : AbsoluteAggregator.INSTANCE;
		return WeightingGradeAggregator.given(newAggregator, transformedSubs,
				transformToPerCriterionWeighting(original.getDefaultSubAggregator()));

		// return switch(original.getMarkAggregator()) {
		// case ParametricWeighter w -> AbsoluteAggregator.INSTANCE;
		// default -> throw new IllegalArgumentException("Unexpected value: " +
		// original.getMarkAggregator());
		// }
	}

	public static GradeAggregator integrateMaxesTODO(GradeAggregator original) {
		if (original instanceof WeightingGradeAggregator w) {
			return w;
		}
		final MarkAggregator a = original.getMarkAggregator();

		if (a instanceof OwaWeighter && original.getSpecialSubAggregators().isEmpty()) {
			return transformToPerCriterionWeighting(original.getDefaultSubAggregator());
		}

		/*
		 * Note that switching, for example, from ParametricWeighter to AbsoluteAggregator extends the
		 * admissible sub-trees.
		 */
		final ImmutableMap<Criterion, ? extends GradeAggregator> specialSubs =
				original.getSpecialSubAggregators();
		/*
		 * Some further pruning is possible (and might be welcome) as some of these sub-aggregators will
		 * never be used: in parametric nodes, the weighting branch will not be used as the
		 * corresponding marks tree is reduced to an absolute mark; and max nodes treated here become
		 * terminal nodes in marks trees.
		 */
		final Map<Criterion, WeightingGradeAggregator> transformedSubs =
				Maps.transformValues(specialSubs, Grade::transformToPerCriterionWeighting);
		final PerCriterionWeighter newAggregator =
				(a instanceof PerCriterionWeighter c) ? c : AbsoluteAggregator.INSTANCE;
		return WeightingGradeAggregator.given(newAggregator, transformedSubs,
				transformToPerCriterionWeighting(original.getDefaultSubAggregator()));

		// return switch(original.getMarkAggregator()) {
		// case ParametricWeighter w -> AbsoluteAggregator.INSTANCE;
		// default -> throw new IllegalArgumentException("Unexpected value: " +
		// original.getMarkAggregator());
		// }
	}

	public static MarksTree adaptMarksForPerCriterionWeighting(Grade original) {
		if (original.getWeightedSubMarks().isEmpty()) {
			return original.toMarksTree();
		}

		final GradeAggregator originalAggregator = original.toAggregator();
		final MarksTree originalMarks = original.toMarksTree();
		final MarkAggregator a = originalAggregator.getMarkAggregator();
		final boolean parametricWeightedSum =
				(a instanceof ParametricWeighter p) && originalMarks.getCriteria().size() == 3;
		// final boolean owaAndMultipleSubs = (a instanceof OwaWeighter)
		// && !originalAggregator.getSpecialSubAggregators().isEmpty();
		// final boolean reducibleOwa = (a instanceof OwaAggregator o)
		// && o.weights().stream().filter(w -> w != 0d).count() == 1
		// && originalAggregator.getSpecialSubAggregators().isEmpty();
		final boolean reducibleOwa = (a instanceof MaxAggregator || a instanceof MinAggregator)
				&& originalAggregator.getSpecialSubAggregators().isEmpty();
		final boolean nonReducibleOwa = (a instanceof OwaWeighter) && !reducibleOwa;

		if (nonReducibleOwa || parametricWeightedSum || (a instanceof NormalizingStaticWeighter)) {
			/*
			 * All these criteria are associated to dynamic weights (weights that depend on the marks
			 * tree), that we thus can’t integrate into a static structure (an aggregator that would not
			 * depend on the tree), so we must prune the tree.
			 *
			 * TODO include here the case of OWA with a non-unique non-zero value. And consider keeping
			 * more of the structure by flattening the sub-criteria and averaging them, if it makes sense.
			 */
			final ImmutableMap<SubMark, Double> weightedSubMarks = original.getWeightedSubMarks();
			final ImmutableMap<Criterion,
					Mark> absoluteMarks = weightedSubMarks.keySet().stream()
							.collect(ImmutableMap.toImmutableMap(SubMark::getCriterion,
									s -> Mark.given(s.getPoints() * weightedSubMarks.get(s), s.comment())));
			LOGGER.debug("Reduced to absolute: from {} to {}.", weightedSubMarks, absoluteMarks);
			return MarksTree.composite(absoluteMarks);
		}
		if ((a instanceof ParametricWeighter p) && originalMarks.getCriteria().size() == 2) {
			final ImmutableMap<SubMark, Double> weightedSubMarks =
					p.weightsWithPenalty(original.subMarks());
			LOGGER.debug("Given {}, points: {}.", weightedSubMarks,
					weightedSubMarks.keySet().stream().collect(ImmutableMap.toImmutableMap(
							SubMark::getCriterion, s -> s.getPoints() * weightedSubMarks.get(s))));
			final ImmutableMap<Criterion,
					Mark> absoluteMarks = weightedSubMarks.keySet().stream()
							.collect(ImmutableMap.toImmutableMap(SubMark::getCriterion,
									s -> Mark.given(s.getPoints() * weightedSubMarks.get(s), s.comment())));
			verify(absoluteMarks.size() == 2);
			final Criterion multipliedCriterion = p.multipliedCriterion();
			verify(absoluteMarks.keySet().contains(multipliedCriterion));
			final LinkedHashMap<Criterion, MarksTree> newMarks = new LinkedHashMap<>(absoluteMarks);
			newMarks.put(multipliedCriterion,
					adaptMarksForPerCriterionWeighting(original.getGrade(multipliedCriterion)));
			return MarksTree.composite(newMarks);
		}
		if (reducibleOwa) {
			final ImmutableMap<SubMark, Double> weightedSubMarks = original.getWeightedSubMarks();
			final Criterion criterionWithAllWeight =
					Maps.filterEntries(weightedSubMarks, e -> e.getValue() != 0d).keySet().stream()
							.collect(MoreCollectors.onlyElement()).getCriterion();
			/*
			 * TODO this forgets the criterion, I suppose, therefore inducing a loss of information.
			 */
			return adaptMarksForPerCriterionWeighting(original.getGrade(criterionWithAllWeight));
		}
		if (a instanceof CriteriaWeighter) {
			final ImmutableSet<Criterion> criteria = original.toMarksTree().getCriteria();
			final ImmutableMap<Criterion, MarksTree> subTrees = criteria.stream().collect(ImmutableMap
					.toImmutableMap(c -> c, c -> adaptMarksForPerCriterionWeighting(original.getGrade(c))));
			return MarksTree.composite(subTrees);
		}
		throw new VerifyException(a.toString());
	}

	public static Grade given(GradeAggregator aggregator, MarksTree marks) {
		return new Grade(aggregator, marks);
	}

	private final GradeAggregator aggregator;
	private final MarksTree marks;
	private final ImmutableMap<SubMark, Double> weightedSubMarks;
	private final Mark mark;
	private final ImmutableMap<Criterion, Grade> subGrades;

	private Grade(GradeAggregator aggregator, MarksTree marks) throws AggregatorException {
		this.aggregator = checkNotNull(aggregator);
		this.marks = checkNotNull(marks);
		/* To check that it is able to compute it. */
		subGrades = marks.getCriteria().stream()
				.collect(ImmutableMap.toImmutableMap(c -> c, this::computeGrade));
		weightedSubMarks = computeWeightedSubMarks();
		mark = computeMark();
	}

	public Mark mark() {
		return mark;
	}

	public Mark mark(Criterion criterion) {
		return getGrade(criterion).mark();
	}

	public Mark mark(CriteriaPath path) {
		return getGrade(path).mark();
	}

	private Mark computeMark() {
		if (marks.isMark()) {
			return marks.getMark(CriteriaPath.ROOT);
		}
		final double weightedSum = weightedSubMarks.keySet().stream()
				.mapToDouble(s -> weightedSubMarks.get(s) * s.getPoints()).sum();
		return Mark.given(Double.min(1d, Double.max(weightedSum, 0d)), "");
	}

	/**
	 * Returns a mark aggregator able to aggregate the children of this grade node (and possibly other
	 * ones).
	 */
	public MarkAggregator getMarkAggregator() {
		return aggregator.getMarkAggregator();
	}

	public MarkAggregator getMarkAggregator(Criterion criterion) {
		return aggregator.getGradeAggregator(criterion).getMarkAggregator();
	}

	public MarkAggregator getMarkAggregator(CriteriaPath path) {
		return aggregator.getGradeAggregator(path).getMarkAggregator();
	}

	/**
	 * @throws NoSuchElementException iff the given criterion is not in this tree.
	 */
	public double getWeight(Criterion criterion) throws NoSuchElementException {
		return weightedSubMarks.keySet().stream().filter(s -> s.getCriterion().equals(criterion))
				.map(weightedSubMarks::get).collect(MoreCollectors.onlyElement());
	}

	public double getWeight(CriteriaPath path) throws NoSuchElementException {
		if (path.isRoot()) {
			return 1d;
		}
		return getWeight(path.getHead()) * getGrade(path.getHead()).getWeight(path.withoutHead());
	}

	/**
	 * @return the criteria in the key set equal the child criteria at the root of this tree.
	 */
	public ImmutableMap<SubMark, Double> getWeightedSubMarks() {
		return weightedSubMarks;
	}

	private ImmutableMap<SubMark, Double> computeWeightedSubMarks() throws AggregatorException {
		final ImmutableSet<SubMark> subMarks = subMarks();
		final MarkAggregator markAggregator = aggregator.getMarkAggregator();
		final ImmutableMap<SubMark, Double> weights = markAggregator.weights(subMarks);
		LOGGER.debug("Obtained via {}, from {}: {}.", markAggregator, subMarks, weights);
		verify(subMarks.size() == weights.size());
		return weights;
	}

	private ImmutableSet<SubMark> subMarks() {
		return marks.getCriteria().stream().map(c -> SubMark.given(c, getGrade(c).mark()))
				.collect(ImmutableSet.toImmutableSet());
	}

	/**
	 * @throws NoSuchElementException iff the given criterion is not in this tree.
	 */
	public Grade getGrade(Criterion criterion) throws NoSuchElementException {
		return Optional.ofNullable(subGrades.get(criterion))
				.orElseThrow(() -> new NoSuchElementException(criterion.getName()));
	}

	private Grade computeGrade(Criterion criterion)
			throws NoSuchElementException, AggregatorException {
		/*
		 * Note that performance would be much better by returning a Grade that delegates to this object
		 * with a shifted root (the shift being a CriteriaPath).
		 */
		final MarksTree subMarks = marks.getTree(criterion);
		final GradeAggregator subAggregator = aggregator.getGradeAggregator(criterion);
		return given(subAggregator, subMarks);
	}

	/**
	 * @throws NoSuchElementException iff the given criterion path is not in this tree.
	 */
	public Grade getGrade(CriteriaPath path) throws NoSuchElementException {
		if (path.isRoot()) {
			return this;
		}
		return getGrade(path.getHead()).getGrade(path.withoutHead());
	}

	public MarksTree toMarksTree() {
		return marks;
	}

	/**
	 * Returns the aggregator associated to this grade (able to aggregate the marks tree associated to
	 * this grade and possibly other ones)
	 */
	public GradeAggregator toAggregator() {
		return aggregator;
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this).add("Aggregator", aggregator).add("Marks", marks)
				.toString();
	}
}
