package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Verify.verify;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.graph.ValueGraph;
import io.github.oliviercailloux.grade.IGrade.GradePath;
import io.github.oliviercailloux.grade.WeightingGrade.WeightedGrade;
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

		final ImmutableSortedSet<GradePath> increasingPaths = ImmutableSortedSet.copyOf(
				Comparator.comparing((GradePath p) -> grade.getMark(p).getPoints()).thenComparing(p -> p.toString()),
				tree.getLeaves());
		LOGGER.debug("Increasing: {}.", increasingPaths);
		verify(increasingPaths.size() == increasingWeights.size(), grade.toString());

		final ImmutableMap.Builder<GradePath, WeightedGrade> builder = ImmutableMap.builder();
		final Iterator<GradePath> pathsIterator = increasingPaths.iterator();
		final Iterator<Double> weightsIterator = increasingWeights.iterator();
		while (pathsIterator.hasNext() || weightsIterator.hasNext()) {
			final GradePath path = pathsIterator.next();
			builder.put(path, WeightedGrade.given(grade.getMark(path), weightsIterator.next()));
		}
		return WeightingGrade.from(builder.build()).withComment(grade.getComment());
	}

}
