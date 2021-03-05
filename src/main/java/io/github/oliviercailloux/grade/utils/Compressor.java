package io.github.oliviercailloux.grade.utils;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Verify.verify;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.graph.ImmutableGraph;
import com.google.common.math.DoubleMath;

import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.GradeStructure;
import io.github.oliviercailloux.grade.IGrade;
import io.github.oliviercailloux.grade.IGrade.GradePath;
import io.github.oliviercailloux.grade.Mark;
import io.github.oliviercailloux.grade.WeightingGrade.WeightedGrade;
import io.github.oliviercailloux.grade.WeightingGrade.WeightedMark;
import io.github.oliviercailloux.utils.Utils;

public class Compressor {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(Compressor.class);

	public static IGrade compress(IGrade grade, GradeStructure model) {
		final WeightedGrade compressed = compress(ImmutableSet.of(GradePath.ROOT), grade, model);
		verify(DoubleMath.fuzzyEquals(compressed.getWeight(), 1d, 1e-6d));
		final IGrade compressedGrade = compressed.getGrade();
		verify(DoubleMath.fuzzyEquals(compressedGrade.getPoints(), grade.getPoints(), 1e-6d));
		verify(model.getSuccessorCriteria(GradePath.ROOT).containsAll(compressedGrade.getSubGrades().keySet()));
		verify(model.asGraph().edges().containsAll(compressedGrade.toTree().asGraph().edges()));
		return compressedGrade;
	}

	private static WeightedGrade compress(Set<GradePath> originalPaths, IGrade grade, GradeStructure model) {
		LOGGER.debug("Compressing from {} targetting {}.", originalPaths, model);
		checkArgument(!originalPaths.isEmpty());
		/**
		 * It would be cleaner to build a tree of grades: at model path [a], a grade
		 * [x/a/blah, x/a/blih, y/a/stuff, …]; given such a grade and a model (the part
		 * following [a]), return for the sub-model-branch [a/c] the grade [x/a/blah/c,
		 * x/a/blih/c, …]. When can’t match more sub-model-branches, we’re done for that
		 * branch (compress to a mark). Thus, suffices to return one WeightedGrade for
		 * each model branch.
		 */
		final ImmutableSet<Criterion> nextLevel = model.getSuccessorCriteria(GradePath.ROOT);
		final GradeStructure tree = grade.toTree();
		final ImmutableSet<GradePath> allPaths = tree.getPaths();
		checkArgument(originalPaths.stream().allMatch(allPaths::contains),
				originalPaths.stream().filter(p -> !allPaths.contains(p)).collect(ImmutableSet.toImmutableSet()));
		final ImmutableGraph<GradePath> graph = tree.asGraph();

		final ImmutableSet<GradePath> paths;
		if (nextLevel.isEmpty()) {
			paths = ImmutableSet.of();
		} else {
			paths = findPaths(nextLevel, originalPaths, tree);
		}

		final ImmutableSet<Criterion> nextCriteria = paths.stream().map(p -> tree.getSuccessorCriteria(p)).distinct()
				.collect(Utils.singleOrEmpty()).orElse(ImmutableSet.of());
		LOGGER.debug("Registering next criteria: {}.", nextCriteria);
		verify(nextLevel.containsAll(nextCriteria));

		final WeightedGrade compressed;
		if (nextCriteria.isEmpty()) {
			final ImmutableSet<GradePath> leaves = originalPaths.stream().flatMap(
					p -> graph.nodes().stream().filter(p2 -> p2.startsWith(p) && graph.successors(p2).isEmpty()))
					.collect(ImmutableSet.toImmutableSet());
			verify(!leaves.isEmpty());
			compressed = compressMarks(Maps.toMap(leaves, l -> grade.getWeightedMark(l)));
		} else {
			final ImmutableMap.Builder<Criterion, WeightedGrade> builder = ImmutableMap.builder();
			for (Criterion criterion : nextCriteria) {
				final GradeStructure sub = model.getStructure(criterion);
				final Set<GradePath> subPaths = paths.stream().map(p -> p.withSuffix(criterion))
						.collect(ImmutableSet.toImmutableSet());
				final WeightedGrade subGrade = compress(subPaths, grade, sub);
				builder.put(criterion, subGrade);
			}
			final ImmutableMap<Criterion, WeightedGrade> subGrades = builder.build();
			verify(subGrades.values().stream().anyMatch(g -> g.getWeight() > 0d), subGrades.toString());
			compressed = WeightedGrade.given(subGrades);
		}

		return compressed;
	}

	public static Mark toMark(IGrade grade) {
		return (Mark) compress(grade, GradeStructure.given(ImmutableSet.of(GradePath.ROOT)));
	}

	/**
	 * @param marks the keys are used only as part of the comment of the resulting
	 *              grade
	 * @return a mark that aggregates all the given grades (with weighted sum of the
	 *         points) and with a weight of the sum of the given weights.
	 */
	static WeightedMark compressMarks(Map<GradePath, WeightedMark> marks) {
		checkArgument(!marks.isEmpty());
		if (marks.size() == 1) {
			return Iterables.getOnlyElement(marks.values());
		}

		final double sumOfAbsolutePoints = marks.values().stream().mapToDouble(WeightedGrade::getAbsolutePoints).sum();
		final String comment = marks.keySet().stream()
				.map(l -> l.withoutTail().toSimpleString() + " – " + marks.get(l).getGrade().getComment())
				.collect(Collectors.joining("\n"));

		final double weight = marks.values().stream().mapToDouble(WeightedGrade::getWeight).sum();
		final double normalizedPoints = sumOfAbsolutePoints / weight;
		final Mark mark = Mark.given(normalizedPoints, comment);
		return WeightedMark.given(mark, weight);
	}

	private static ImmutableSet<GradePath> findPaths(Set<Criterion> nodes, Set<GradePath> startingPaths,
			GradeStructure tree) {
		checkArgument(!startingPaths.isEmpty());

		final Set<Set<Criterion>> nodeSets = Sets.powerSet(nodes);
		final Iterable<Set<Criterion>> wholeFirst = Iterables.concat(ImmutableSet.of(nodes), nodeSets);
		ImmutableSet<GradePath> result = null;
		for (Set<Criterion> nodeSubset : wholeFirst) {
			if (nodeSubset.isEmpty()) {
				continue;
			}
			final ImmutableSet<GradePath> found = findConformingPaths(nodeSubset, startingPaths, tree);
			if (!found.isEmpty()) {
				result = found;
				break;
			}
		}
		if (result == null) {
			result = ImmutableSet.of();
		}
		LOGGER.debug("Searching for {} starting from {} among {}, found {}.", nodes, startingPaths, tree, result);
		return result;
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
