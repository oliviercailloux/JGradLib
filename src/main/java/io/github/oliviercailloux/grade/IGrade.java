package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Verify.verify;

import com.google.common.collect.ForwardingList;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.graph.ImmutableValueGraph;
import com.google.common.graph.ValueGraphBuilder;
import com.google.common.math.DoubleMath;
import io.github.oliviercailloux.grade.WeightingGrade.PathGradeWeight;
import io.github.oliviercailloux.grade.WeightingGrade.WeightedGrade;
import io.github.oliviercailloux.grade.WeightingGrade.WeightedMark;
import io.github.oliviercailloux.grade.old.GradeStructure;
import io.github.oliviercailloux.grade.old.Mark;
import java.util.List;
import java.util.Optional;
import java.util.RandomAccess;
import java.util.Set;
import java.util.stream.Collectors;

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

		public static GradePath from(String pathString) {
			return GradePath.from(ImmutableList.copyOf(pathString.split("/")).stream().map(Criterion::given)
					.collect(ImmutableList.toImmutableList()));
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

		public GradePath withPrefix(GradePath prefix) {
			return new GradePath(ImmutableList.<Criterion>builderWithExpectedSize(size() + prefix.size()).addAll(prefix)
					.addAll(list).build());
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

		public boolean startsWith(Criterion criterion) {
			return !isRoot() && getHead().equals(criterion);
		}

		public boolean startsWith(GradePath subPath) {
			if (list.size() < subPath.size()) {
				return false;
			}
			final List<Criterion> startingList = subPath;
			return list.subList(0, subPath.size()).equals(startingList);
		}

		public boolean endsWith(GradePath subPath) {
			if (list.size() < subPath.size()) {
				return false;
			}
			final List<Criterion> subList = subPath;
			return list.subList(list.size() - subPath.size(), list.size()).equals(subList);
		}

		public boolean endsWith(Criterion criterion) {
			return !isRoot() && getTail().equals(criterion);
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

	public default IGrade withSubGrade(GradePath path, IGrade grade) {
		if (path.isRoot()) {
			return grade;
		}
		final Criterion criterion = path.getHead();
		checkArgument(getSubGrades().containsKey(criterion), criterion);
		final IGrade subPatched = getSubGrades().get(criterion).withSubGrade(path.withoutHead(), grade);
		return withSubGrade(criterion, subPatched);
	}

	public default IGrade withPatch(Patch patch) {
		return withSubGrade(patch.getPath(), patch.getGrade());
	}

	public default IGrade withDissolved(Criterion criterion) {
		/**
		 * Considers each other branches b and each leaf in branch b. The leaf is
		 * replaced by a weighting grade of wofbranch b * originalleaf + wofdissolved *
		 * dissolved.
		 */
		final ImmutableSet<Criterion> criteria = getSubGrades().keySet();
		checkArgument(criteria.contains(criterion));
		checkArgument(criteria.size() >= 2);
		final PathGradeWeight toDissolve = getPathGradeWeight(GradePath.ROOT.withSuffix(criterion));
		final ImmutableMap<GradePath, WeightedGrade> newGrades = toTree().getLeaves().stream()
				.filter(l -> !l.getHead().equals(criterion)).map(this::getPathGradeWeight)
				.collect(ImmutableMap.toImmutableMap(g -> g.getPath(), g -> integrate(g, toDissolve)));
		final IGrade dissolved = WeightingGrade.withZeroesRectified(newGrades);
		verify(DoubleMath.fuzzyEquals(dissolved.getPoints(), getPoints(), 1e-6d));
		return dissolved;
	}

	private WeightedGrade integrate(PathGradeWeight remaining, PathGradeWeight toDissolve) {
		final Criterion remainingBranch = remaining.getPath().getHead();
		final Double weightRemaining = getWeights().get(remainingBranch);
		final CriterionGradeWeight gRemaining = CriterionGradeWeight.from(remaining.getPath().getTail(),
				remaining.getGrade(), weightRemaining);

		final Criterion toDissolveCriterion = toDissolve.getPath().getHead();
		final Double toDissolveWeight = getWeights().get(toDissolveCriterion);
		final CriterionGradeWeight gDissolved = CriterionGradeWeight.from(toDissolveCriterion, toDissolve.getGrade(),
				toDissolveWeight);

		final WeightingGrade integrated = WeightingGrade.from(ImmutableSet.of(gRemaining, gDissolved));
		return WeightedGrade.given(integrated, getWeight(remaining.getPath()));
	}

	public default Mark getMark(GradePath path) {
		if (path.isRoot()) {
			return (Mark) this;
		}
		final Criterion criterion = path.getHead();
		checkArgument(getSubGrades().containsKey(criterion));
		return getSubGrades().get(criterion).getMark(path.withoutHead());
	}

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

	public default WeightedMark getWeightedMark(GradePath path) {
		checkArgument(toTree().asGraph().nodes().contains(path));
		return WeightedMark.given(getMark(path), getWeight(path));
	}

	public default WeightedGrade getWeightedGrade(GradePath path) {
		checkArgument(toTree().asGraph().nodes().contains(path));
		return WeightedGrade.given(getGrade(path).get(), getWeight(path));
	}

	public default PathGradeWeight getPathGradeWeight(GradePath path) {
		checkArgument(toTree().asGraph().nodes().contains(path));
		return PathGradeWeight.given(path, getGrade(path).get(), getWeight(path));
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

	public default double getLocalWeight(GradePath path) {
		if (path.isRoot()) {
			return 1d;
		}
		final GradePath parent = path.withoutTail();
		return getGrade(parent).get().getWeights().get(path.getTail());
	}

	public default IGrade withPatches(Set<Patch> patches) {
		IGrade current = this;
		for (Patch patch : patches) {
			current = current.withPatch(patch);
		}
		return current;
	}

	public default IGrade withoutTopLayer() {
		final ImmutableSet<CriterionGradeWeight> subGradesAsSet = getSubGradesAsSet();
		final ImmutableSet<CriterionGradeWeight> grandChildren = subGradesAsSet.stream()
				.flatMap(c -> getSubGrades().get(c.getCriterion()).getSubGradesAsSet().stream().map(
						s -> CriterionGradeWeight.from(s.getCriterion(), s.getGrade(), c.getWeight() * s.getWeight())))
				.collect(ImmutableSet.toImmutableSet());
		return WeightingGrade.from(grandChildren);
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
