package io.github.oliviercailloux.grade.utils;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Verify.verify;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.GradeStructure;
import io.github.oliviercailloux.grade.IGrade;
import io.github.oliviercailloux.grade.IGrade.GradePath;
import io.github.oliviercailloux.grade.Mark;
import io.github.oliviercailloux.grade.WeightingGrade.WeightedGrade;
import io.github.oliviercailloux.grade.WeightingGrade.WeightedMark;
import io.github.oliviercailloux.utils.Utils;

public class Compressor {

	public static IGrade compress(IGrade grade, GradeStructure model) {
		return compress(ImmutableSet.of(GradePath.ROOT), grade, model).getGrade();
	}

	private static WeightedGrade compress(Set<GradePath> originalPaths, IGrade grade, GradeStructure model) {
		final ImmutableSet<Criterion> nextLevel = model.getSuccessorCriteria(GradePath.ROOT);

		final ImmutableSet<GradePath> paths;
		if (nextLevel.isEmpty()) {
			paths = ImmutableSet.of();
			// or are leaves
		} else {
			paths = findPaths(nextLevel, originalPaths, grade.toTree());
			// if empty: or must each be compressed.
		}

		final ImmutableSet<Criterion> nextCriteria = paths.stream().map(p -> grade.toTree().getSuccessorCriteria(p))
				.distinct().collect(Utils.singleOrEmpty()).orElse(ImmutableSet.of());
		verify(nextLevel.containsAll(nextCriteria));

		final WeightedGrade compressed;
		if (nextCriteria.isEmpty()) {
			compressed = compressMarks(Maps.toMap(originalPaths, p -> toWeightedMark(grade.getWeightedGrade(p))));
		} else {
			final ImmutableMap.Builder<Criterion, WeightedGrade> builder = ImmutableMap.builder();
			for (Criterion criterion : nextCriteria) {
				final GradeStructure sub = model.getStructure(criterion);
				final Set<GradePath> subPaths = originalPaths.stream().map(p -> p.withSuffix(criterion))
						.collect(ImmutableSet.toImmutableSet());
				final WeightedGrade subGrade = compress(subPaths, grade, sub);
				builder.put(criterion, subGrade);
			}
			compressed = WeightedGrade.given(builder.build());
		}

		return compressed;
	}

	public static WeightedMark toWeightedMark(WeightedGrade weightedGrade) {
		final GradeStructure structure = weightedGrade.getGrade().toTree();
		final ImmutableMap<GradePath, WeightedMark> leaveMarks = Maps.toMap(structure.getLeaves(),
				weightedGrade::getWeightedMark);
		return compressMarks(leaveMarks);
	}

	/**
	 * @param marks the keys are used only as part of the comment of the resulting
	 *              grade
	 * @return a mark that aggregates all the given grades (with weighted sum of the
	 *         points) and with a weight of the sum of the given weights.
	 */
	static WeightedMark compressMarks(Map<GradePath, WeightedMark> marks) {
		final double sumOfAbsolutePoints = marks.values().stream().mapToDouble(WeightedGrade::getAbsolutePoints).sum();
		final String comment = marks.keySet().stream()
				.map(l -> l.toSimpleString() + " – " + marks.get(l).getGrade().getComment())
				.collect(Collectors.joining("\n"));

		final Mark mark = Mark.given(sumOfAbsolutePoints, comment);
		final double weight = marks.values().stream().mapToDouble(WeightedGrade::getWeight).sum();
		return WeightedMark.given(mark, weight);
	}

	private static ImmutableSet<GradePath> findPaths(Set<Criterion> nodes, Set<GradePath> startingPaths,
			GradeStructure tree) {
		final Set<Set<Criterion>> nodeSets = Sets.powerSet(nodes);
		final Iterable<Set<Criterion>> wholeFirst = Iterables.concat(ImmutableSet.of(nodes), nodeSets);
		for (Set<Criterion> nodeSubset : wholeFirst) {
			final ImmutableSet<GradePath> found = findConformingPaths(nodeSubset, startingPaths, tree);
			if (!found.isEmpty()) {
				return found;
			}
		}
		return ImmutableSet.of();
	}

	/**
	 * @param nodes        the criteria that must be children of all paths, non
	 *                     empty
	 * @param startingPath the starting path in the tree
	 * @param tree         containing the paths among which to search
	 * @return a set of paths that cover a pruned version of the tree, such that all
	 *         paths end with the given set of nodes; an empty set iff such a cover
	 *         does not exist
	 */
	private static ImmutableSet<GradePath> findConformingPaths(Set<Criterion> nodes, GradePath startingPath,
			GradeStructure tree) {
		checkArgument(!nodes.isEmpty());
		final ArrayDeque<GradePath> toCheck = new ArrayDeque<>();
		toCheck.add(startingPath);
		final ImmutableSet.Builder<GradePath> builder = ImmutableSet.builder();
		boolean failed = false;
		do {
			final GradePath current = toCheck.pop();
			final ImmutableSet<Criterion> succ = tree.getSuccessorCriteria(current);
			if (succ.isEmpty()) {
				failed = true;
				break;
			}
			final ImmutableSet<Criterion> toVisit;
			if (succ.containsAll(nodes)) {
				builder.add(current);
				toVisit = Sets.difference(succ, nodes).immutableCopy();
			} else {
				toVisit = succ;
			}
			toCheck.addAll(toVisit.stream().map(current::withSuffix).collect(ImmutableSet.toImmutableSet()));
		} while (!toCheck.isEmpty());

		if (failed) {
			return ImmutableSet.of();
		}
		return builder.build();
	}

	private static ImmutableSet<GradePath> findConformingPaths(Set<Criterion> nodes, Set<GradePath> startingPaths,
			GradeStructure tree) {
		final ImmutableSet.Builder<GradePath> builder = ImmutableSet.builder();
		for (GradePath startingPath : startingPaths) {
			final ImmutableSet<GradePath> found = findConformingPaths(nodes, startingPath, tree);
			if (found.isEmpty()) {
				return ImmutableSet.of();
			}
			builder.addAll(found);
		}
		return builder.build();
	}

}
