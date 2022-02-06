package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Verify.verify;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Maps;
import com.google.common.collect.MoreCollectors;
import com.google.common.graph.ValueGraph;
import io.github.oliviercailloux.grade.IGrade.CriteriaPath;
import io.github.oliviercailloux.grade.WeightingGrade.WeightedGrade;
import io.github.oliviercailloux.grade.old.GradeStructure;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GradeUtils {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GradeUtils.class);

	/**
	 * Is a weighting grade iff the given root has at least one successor in the
	 * tree or the grade for the root is a weighting grade.
	 */
	public static IGrade toGrade(Criterion root, ValueGraph<Criterion, Double> staticTree,
			Map<Criterion, IGrade> leafGrades) {
		final Set<Criterion> successors = staticTree.successors(root);
		if (successors.isEmpty()) {
			return leafGrades.get(root);
		}
		final ImmutableSet<CriterionGradeWeight> cgws = successors.stream().map(c -> CriterionGradeWeight.from(c,
				toGrade(c, staticTree, leafGrades), staticTree.edgeValue(root, c).get()))
				.collect(ImmutableSet.toImmutableSet());
		return WeightingGrade.from(cgws);
	}

	static ImmutableMap<Criterion, IGrade> withUpdatedEntry(Map<Criterion, IGrade> subGrades, Criterion criterion,
			IGrade newSubGrade) {
		checkArgument(subGrades.keySet().contains(criterion));

		return subGrades.entrySet().stream().collect(ImmutableMap.toImmutableMap(Map.Entry::getKey,
				e -> e.getKey().equals(criterion) ? newSubGrade : e.getValue()));
	}

	public static IGrade toOwa(IGrade grade, List<Double> increasingWeights) {
		if (grade.getPoints() == 0d) {
			return grade;
		}

		final GradeStructure tree = grade.toTree();

		final ImmutableSortedSet<CriteriaPath> increasingPaths = ImmutableSortedSet.copyOf(
				Comparator.comparing((CriteriaPath p) -> grade.getMark(p).getPoints()).thenComparing(p -> p.toString()),
				tree.getLeaves());
		LOGGER.debug("Increasing: {}.", increasingPaths);
		verify(increasingPaths.size() == increasingWeights.size(), grade.toString());

		final ImmutableMap.Builder<CriteriaPath, WeightedGrade> builder = ImmutableMap.builder();
		final Iterator<CriteriaPath> pathsIterator = increasingPaths.iterator();
		final Iterator<Double> weightsIterator = increasingWeights.iterator();
		while (pathsIterator.hasNext() || weightsIterator.hasNext()) {
			final CriteriaPath path = pathsIterator.next();
			builder.put(path, WeightedGrade.given(grade.getMark(path), weightsIterator.next()));
		}
		return WeightingGrade.from(builder.build()).withComment(grade.getComment());
	}

	public static Exam toCriteriaWeighter(Exam exam) {
	}

	private static Grade newGrade(Grade original) {
		final MarkAggregator markAggregator = original.getAggregator().getMarkAggregator();
		final Grade newGradeBasis;
		final MarksTree newTreeBasis;
		if (markAggregator instanceof ParametricWeighter) {
			if (original.getMarksTree().isMark()) {
				newTreeBasis = original.getMarksTree();
			} else {
				final ParametricWeighter p = (ParametricWeighter) markAggregator;
				p.multipliedCriterion();
				if (original.getMarksTree().getCriteria().size() != 2) {
					throw new UnsupportedOperationException();
				}
				final Mark multipliedMark = original.getGrade(p.multipliedCriterion()).getMark();
				final Mark weightingMark = original.getGrade(p.weightingCriterion()).getMark();
				final double absoluteModification = multipliedMark.getPoints() * (1d - weightingMark.getPoints());
				newTreeBasis = MarksTree.composite(ImmutableMap.of(p.multipliedCriterion(), multipliedMark,
						Criterion.given(p.weightingCriterion().getName() + ", penalty"),
						Mark.given(absoluteModification, weightingMark.getComment())));
			}

			final AbsoluteAggregator newMarkAggregator = AbsoluteAggregator.instance();
			final GradeAggregator newAggregatorBasis = GradeAggregator.given(newMarkAggregator,
					original.getAggregator().getSubAggregators(), original.getAggregator().getDefaultSubAggregator());
			newGradeBasis = Grade.given(newAggregatorBasis, newTreeBasis);
		} else if (markAggregator instanceof OwaWeighter) {
			if (original.getMarksTree().isMark()) {
				newTreeBasis = original.getMarksTree();
			} else {
				final ImmutableMap<SubMark, Double> weightedSubMarks = original.getWeightedSubMarks();
				final ImmutableMultiset<Double> weights = weightedSubMarks.values().stream()
						.collect(ImmutableMultiset.toImmutableMultiset());
				if (weights.count(1d) != 1 || weights.count(0d) != weights.size() - 1) {
					/*
					 * Could probably transform to absolutes, that’s the worst case that always
					 * works…
					 */
					throw new UnsupportedOperationException("Owa but not MAX, can’t do");
				}
				final SubMark relevantMark = weightedSubMarks.keySet().stream()
						.filter(s -> weightedSubMarks.get(s) == 1d).collect(MoreCollectors.onlyElement());
				newTreeBasis = relevantMark.getMarksTree();
			}

			final ImmutableSet.Builder<GradeAggregator> builder = ImmutableSet.builder();
			builder.addAll(original.getAggregator().getSubAggregators().values());
			original.getAggregator().getDefaultSubAggregator().ifPresent(builder::add);
			final ImmutableSet<GradeAggregator> subAggregators = builder.build();
			if (subAggregators.size() >= 2) {
				throw new UnsupportedOperationException("Subs might vary per individual, can’t eliminate.");
			}
			final GradeAggregator subAggregator = subAggregators.stream().collect(MoreCollectors.onlyElement());
			newGradeBasis = Grade.given(subAggregator, newTreeBasis);
		} else {
			newTreeBasis = original.getMarksTree();
			newGradeBasis = original;
		}

		if (newGradeBasis.getAggregator().getSubAggregators().isEmpty() && newGradeBasis.getAggregator().getDefaultSubAggregator().isEmpty()) {
			return newGradeBasis;
		}

		final ImmutableSet.Builder<Criterion> builder = ImmutableSet.builder();
		builder.addAll(newGradeBasis.getAggregator().getSubAggregators().keySet());
		builder.addAll(newGradeBasis.getMarksTree().getCriteria());
		final ImmutableSet<Criterion> subCriteria = builder.build();

		final ImmutableMap<Criterion, Grade> newWholeGrade = subCriteria.stream()
				.collect(ImmutableMap.toImmutableMap(c -> c, c -> newGrade(newGradeBasis.getGrade(c))));

		final Map<Criterion, MarksTree> newWholeTree = Maps.transformValues(newWholeGrade, Grade::getMarksTree);
		final MarksTree newMarksTree = MarksTree.composite(newWholeTree);

		GradeAggregator.given(newGradeBasis.getAggregator().getMarkAggregator(), )
		return newMarksTree;
	}

}
