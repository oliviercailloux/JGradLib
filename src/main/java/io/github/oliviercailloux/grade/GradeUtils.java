package io.github.oliviercailloux.grade;

import java.util.Map;
import java.util.Set;

import com.google.common.collect.ImmutableSet;
import com.google.common.graph.ValueGraph;

public class GradeUtils {

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
		final ImmutableSet<CriterionGradeWeight> cgws = successors.stream().map(
				c -> CriterionGradeWeight.from(c, toGrade(c, staticTree, leafGrades), staticTree.edgeValue(root, c).get()))
				.collect(ImmutableSet.toImmutableSet());
		return WeightingGrade.from(cgws);
	}

}
