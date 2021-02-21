package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.List;
import java.util.Optional;
import java.util.RandomAccess;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.collect.ForwardingList;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.graph.ImmutableValueGraph;
import com.google.common.graph.ValueGraphBuilder;

import io.github.oliviercailloux.grade.WeightingGrade.WeightedGrade;

/**
 *
 * Grade (interface): {points generally in [0, 1], comment, subGrades:
 * ImmutableMap<CriterionAndPoints, Grade> which may be empty}. A mark is a
 * grade that has no sub-grades. A composite grade is a grade that has at least
 * one sub-grade. Interfaces to distinguish marks from composite grades do not
 * exist: it would raise complexity and not bring much benefit.
 *
 *
 * A grade knows the criteria and sub-grades that it is composed of. But it does
 * not know which fraction it should preferably use for its own display: this is
 * known by the user of the grade at display time. It relates to display and not
 * to grade information per se.
 *
 * TODO get rid of default methods.
 *
 * This should be: either a mark, or a weighting grade. A min, an And, a Time
 * penalty, all of this can be transformed (with loss of information) to a
 * weighting grade. The various subclasses would serve to remember more
 * information. Still unclear whether a weighting grade with weight on the min
 * points should equal a MinGrade, however. I’d say no: a weighting grade should
 * equal a weighting grade (thus equality after transforming the Mingrade to a
 * Wgrade).
 *
 * BUT this is not necessarily a tree of criteria with weights: the weights
 * might be known only at grade time (in case of MIN, e.g.).
 *
 */
public interface IGrade {

	public static class GradePath extends ForwardingList<Criterion> implements List<Criterion>, RandomAccess {
		public static final GradePath ROOT = new GradePath(ImmutableList.of());

		public static GradePath from(List<Criterion> list) {
			if (list instanceof GradePath) {
				final GradePath path = (GradePath) list;
				return path;
			}
			return new GradePath(list);
		}

		private final ImmutableList<Criterion> list;

		private GradePath(List<Criterion> list) {
			this.list = ImmutableList.copyOf(list);
		}

		@Override
		protected ImmutableList<Criterion> delegate() {
			return list;
		}

		public boolean isRoot() {
			return isEmpty();
		}

		public Criterion getHead() {
			return list.get(0);
		}

		public Criterion getTail() {
			return list.get(list.size() - 1);
		}

		public GradePath withPrefix(Criterion root) {
			return new GradePath(
					ImmutableList.<Criterion>builderWithExpectedSize(size() + 1).add(root).addAll(list).build());
		}

		public GradePath withSuffix(Criterion terminal) {
			return new GradePath(
					ImmutableList.<Criterion>builderWithExpectedSize(size() + 1).addAll(list).add(terminal).build());
		}

		/**
		 * @throws IndexOutOfBoundsException
		 */
		public GradePath withoutHead() {
			return new GradePath(ImmutableList.<Criterion>builderWithExpectedSize(size() - 1)
					.addAll(list.subList(1, list.size())).build());
		}

		/**
		 * @throws IndexOutOfBoundsException
		 */
		public GradePath withoutTail() {
			return new GradePath(ImmutableList.<Criterion>builderWithExpectedSize(size() - 1)
					.addAll(list.subList(0, list.size() - 1)).build());
		}

		public boolean startsWith(GradePath starting) {
			final List<Criterion> startingList = starting;
			return list.subList(0, starting.size()).equals(startingList);
		}

		public ImmutableList<Criterion> asImmutableList() {
			return list;
		}

		/**
		 * @return a possibly ambiguous but simple string
		 */
		public String toSimpleString() {
			return list.stream().map(Criterion::getName).collect(Collectors.joining("/"));
		}
	}

	/**
	 * Returns the points. It is not mandatory that the points on a composite grade
	 * be a deterministic function of the points on the sub-grades: manual
	 * correction may intervene in between.
	 *
	 * @return the points.
	 */
	public double getPoints();

	/**
	 * Returns the comment about the points. Comment on a composite grade serves to
	 * explain how a grade has been obtained from its sub-grades (may be empty if
	 * obvious), and is not supposed to be a concatenation of sub-comments: this
	 * comment is not supposed to be redundant with sub-comments.
	 *
	 * @return the comment.
	 */
	public String getComment();

	/**
	 * Returns the sub-grades (with the key set iterating in order of the
	 * sub-grades), empty iff this grade is a mark, non-empty iff this grade is a
	 * composite grade.
	 *
	 * @return the sub grades.
	 */
	public ImmutableMap<Criterion, IGrade> getSubGrades();

	/**
	 * @return iterates in the order of the sub-grades.
	 */
	ImmutableSet<CriterionGradeWeight> getSubGradesAsSet();

	/**
	 * @return the weights, such that the positive weights sum to one, and not
	 *         empty. Iterates in the order of the sub-grades.
	 */
	ImmutableMap<Criterion, Double> getWeights();

	public default double getAbsolutePoints(GradePath path) {
		return getWeight(path) * getGrade(path).get().getPoints();
	}

	public default GradeStructure toTree() {
		return GradeStructure.given(toValueTree().asGraph());
	}

	/**
	 * @return from any node p, the sum of weights to direct children is one.
	 */
	public default ImmutableValueGraph<GradePath, Double> toValueTree() {
		final ImmutableValueGraph.Builder<GradePath, Double> builder = ValueGraphBuilder.directed()
				.allowsSelfLoops(false).immutable();
		builder.addNode(GradePath.ROOT);
		putValuedChildren(builder, GradePath.ROOT);
		return builder.build();
	}

	private void putValuedChildren(ImmutableValueGraph.Builder<GradePath, Double> builder, GradePath root) {
		for (Criterion newChild : getSubGrades().keySet()) {
			final GradePath childPath = root.withSuffix(newChild);
			builder.putEdgeValue(root, childPath, getWeights().get(newChild));
			final IGrade subGrade = getSubGrades().get(newChild);
			subGrade.putValuedChildren(builder, childPath);
		}
	}

	/**
	 * @param depth ≥0; 0 for a mark.
	 */
	public IGrade limitedDepth(int depth);

	public IGrade withComment(String newComment);

	/**
	 * If the given criterion exists among the sub grades contained in this grade,
	 * returns a new grade identical to this one except that the corresponding sub
	 * grade is replaced. If the criterion does not exist, then either returns a
	 * grade identical to this one with a new sub grade, or throws an
	 * {@link IllegalArgumentException}.
	 *
	 */
	public IGrade withSubGrade(Criterion criterion, IGrade newSubGrade);

	public default Optional<IGrade> getGrade(GradePath path) {
		if (path.isRoot()) {
			return Optional.of(this);
		}
		final Criterion criterion = path.getHead();
		if (!getSubGrades().containsKey(criterion)) {
			return Optional.empty();
		}
		return getSubGrades().get(criterion).getGrade(path.withoutHead());
	}

	public default WeightedGrade getWeightedGrade(GradePath path) {
		checkArgument(toTree().asGraph().nodes().contains(path));
		return WeightedGrade.given(getGrade(path).get(), getWeight(path));
	}

	public default double getWeight(GradePath path) {
		if (path.isRoot()) {
			return 1d;
		}
		final ImmutableMap<Criterion, IGrade> subGrades = getSubGrades();
		final Criterion head = path.getHead();
		final IGrade first = Optional.ofNullable(subGrades.get(head)).orElseThrow(IllegalArgumentException::new);
		final double local = getWeights().get(head);
		return local == 0d ? local : local * first.getWeight(path.withoutHead());
	}

	public default IGrade withPatch(Patch patch) {
		final ImmutableList<Criterion> path = patch.getPath();
		if (path.isEmpty()) {
			return patch.getGrade();
		}
		final Criterion criterion = path.get(0);
		final IGrade subPatched = getSubGrades().get(criterion)
				.withPatch(Patch.create(path.subList(1, path.size()), patch.getGrade()));
		return withSubGrade(criterion, subPatched);
	}

	public default IGrade withPatches(Set<Patch> patches) {
		IGrade current = this;
		for (Patch patch : patches) {
			current = current.withPatch(patch);
		}
		return current;
	}

	/**
	 * Two {@link IGrade} objects are equal iff they have the same points, comment,
	 * and sub grades (irrespective of the order of the sub grades).
	 *
	 * TODO this is wrong: two weighting grades with the same criteria and different
	 * weights may be equal, according to this definition, but should not be.
	 */
	@Override
	public boolean equals(Object o2);
}
