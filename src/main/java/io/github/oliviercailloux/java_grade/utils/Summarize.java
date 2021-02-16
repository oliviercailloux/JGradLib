package io.github.oliviercailloux.java_grade.utils;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Verify.verify;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

import com.google.common.base.VerifyException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.MoreCollectors;
import com.google.common.collect.Sets;
import com.google.common.graph.Graph;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.ImmutableGraph;
import com.google.common.graph.ImmutableValueGraph;
import com.google.common.graph.MutableGraph;
import com.google.common.graph.ValueGraph;
import com.google.common.graph.ValueGraphBuilder;

import io.github.oliviercailloux.git.git_hub.model.GitHubUsername;
import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinates;
import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.CriterionGradeWeight;
import io.github.oliviercailloux.grade.GradeStructure;
import io.github.oliviercailloux.grade.IGrade;
import io.github.oliviercailloux.grade.IGrade.GradePath;
import io.github.oliviercailloux.grade.Mark;
import io.github.oliviercailloux.grade.WeightingGrade;
import io.github.oliviercailloux.grade.comm.StudentOnGitHub;
import io.github.oliviercailloux.grade.comm.json.JsonStudentsReader;
import io.github.oliviercailloux.grade.format.CsvGrades;
import io.github.oliviercailloux.grade.format.HtmlGrades;
import io.github.oliviercailloux.grade.format.json.JsonGrade;
import io.github.oliviercailloux.json.JsonbUtils;
import io.github.oliviercailloux.utils.Utils;
import io.github.oliviercailloux.xml.XmlUtils;

public class Summarize {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(Summarize.class);

	private static final Path READ_DIR = Paths.get("");

	private static class CompressedGrade {
		private final GradeStructure model;
		private final IGrade original;
		private final MutableGraph<GradePath> compressedStructure;
		private final Map<GradePath, Set<GradePath>> compressedToOriginalPaths;

		private final Map<GradePath, Mark> leafMarks;

		private CompressedGrade(GradeStructure model, IGrade original) {
			this.model = checkNotNull(model);
			this.original = checkNotNull(original);
		}

		private IGrade compress() {
			final ImmutableSet<Criterion> nextLevel = model.getSuccessorCriteria(GradePath.ROOT);
			compressedStructure.addNode(GradePath.ROOT);
			final Queue<GradePath> toVisit = new ArrayDeque<>();
			toVisit.add(GradePath.ROOT);
			do {
				final GradePath current = toVisit.remove();
				final ImmutableSet<GradePath> originalPaths;
				if (current.isRoot()) {
					originalPaths = ImmutableSet.of(GradePath.ROOT);
				} else {
					final GradePath parent = Iterables.getOnlyElement(compressedStructure.predecessors(current));
					final Set<GradePath> originalParentPaths = compressedToOriginalPaths.get(parent);
					originalPaths = originalParentPaths.stream().map(p -> p.withSuffix(current.getTail()))
							.collect(ImmutableSet.toImmutableSet());
				}
				final ImmutableSet<GradePath> paths = findPaths(nextLevel, originalPaths, original.toTree());
				if (paths.isEmpty()) {
					leafMarks.put(current, toMark(original.getGrade(current).get()));
				} else {
					compressedToOriginalPaths.put(current, paths);
					for (Criterion criterion : nextLevel) {
						final boolean matchedCriterion = paths.stream()
								.anyMatch(p -> original.getGrade(p).get().getSubGrades().keySet().contains(criterion));
						if (matchedCriterion) {
							verify(paths.stream().allMatch(
									p -> original.getGrade(p).get().getSubGrades().keySet().contains(criterion)));
							toVisit.add(current.withSuffix(criterion));
						}
					}
				}
			} while (!toVisit.isEmpty());
		}
	}

	public static void main(String[] args) throws Exception {
		summarize("admin-manages-users", Paths.get(""), true);
	}

	private static Mark toMark(IGrade grade, GradePath starting) {
		final ImmutableSet<GradePath> leaves = grade.toTree().asGraph().nodes().stream()
				.filter(p -> p.startsWith(starting) && grade.getGrade(p).get().getSubGrades().isEmpty())
				.collect(ImmutableSet.toImmutableSet());
	
		final ImmutableList.Builder<String> commentsBuilder = ImmutableList.builder();
		double sumOfAbsolutePoints = 0d;
		for (GradePath leaf : leaves) {
			final double absolutePoints = grade.getWeight(leaf) * grade.getGrade(leaf).get().getPoints();
			final IGrade leafGrade = grade.getGrade(leaf).get();
			final StringBuilder commentBuilder = new StringBuilder();
			sumOfAbsolutePoints += absolutePoints;
			final String comment = leafGrade.getComment();
			commentsBuilder.add(leaf.subList(starting.size(), leaf.size() - starting.size()).stream()
					.map(Criterion::getName).collect(Collectors.joining("/")) + " – " + comment);
		}
		return Mark.given(sumOfAbsolutePoints / grade.getWeight(starting),
				commentsBuilder.build().stream().collect(Collectors.joining("\n")));
	}

	private static Mark toMark(IGrade grade) {
		final ImmutableSet<GradePath> leaves = grade.toTree().asGraph().nodes().stream()
				.filter(p -> grade.getGrade(p).get().getSubGrades().isEmpty())
				.collect(ImmutableSet.toImmutableSet());
	
		final ImmutableList.Builder<String> commentsBuilder = ImmutableList.builder();
		double sumOfAbsolutePoints = 0d;
		for (GradePath leaf : leaves) {
			final double absolutePoints = grade.getWeight(leaf) * grade.getGrade(leaf).get().getPoints();
			final IGrade leafGrade = grade.getGrade(leaf).get();
			final StringBuilder commentBuilder = new StringBuilder();
			sumOfAbsolutePoints += absolutePoints;
			final String comment = leafGrade.getComment();
			commentsBuilder
					.add(leaf.stream().map(Criterion::getName).collect(Collectors.joining("/")) + " – " + comment);
		}
		return Mark.given(sumOfAbsolutePoints, commentsBuilder.build().stream().collect(Collectors.joining("\n")));
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

	private static ImmutableSet<GradePath> findPaths(Set<Criterion> nodes, Set<GradePath> startingPaths,
			GradeStructure tree) {
		final Set<Set<Criterion>> nodeSets = Sets.powerSet(nodes);
		for (Set<Criterion> nodeSubset : nodeSets) {
			final ImmutableSet<GradePath> found = findConformingPaths(nodeSubset, startingPaths, tree);
			if (!found.isEmpty()) {
				return found;
			}
		}
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

	public static void summarize(String prefix, Path outDir, boolean ignoreZeroWeights) throws IOException {
		@SuppressWarnings("all")
		final Type type = new LinkedHashMap<RepositoryCoordinates, IGrade>() {
		}.getClass().getGenericSuperclass();

		LOGGER.debug("Reading grades.");
		final String sourceGrades = Files.readString(READ_DIR.resolve("grades " + prefix + ".json"));
		final Map<String, IGrade> grades = JsonbUtils.fromJson(sourceGrades, type, JsonGrade.asAdapter());
		LOGGER.debug("Read {}, keys: {}.", sourceGrades, grades.keySet());

		final ImmutableMap<String, IGrade> transformed;
		if (ignoreZeroWeights) {
			transformed = grades.entrySet().stream().collect(
					ImmutableMap.toImmutableMap(Map.Entry::getKey, e -> withTimePenalty(nonZero(e.getValue()))));
		} else {
			transformed = ImmutableMap.copyOf(grades);
		}
		final ImmutableSet<GitHubUsername> gradesUsernames = transformed.keySet().stream().map(GitHubUsername::given)
				.collect(ImmutableSet.toImmutableSet());
		LOGGER.debug("Reading usernames.");
		final JsonStudentsReader students = JsonStudentsReader
				.from(Files.readString(READ_DIR.resolve("usernames.json")));
		final ImmutableMap<GitHubUsername, StudentOnGitHub> usernames = students.getStudentsByGitHubUsername();
		final ImmutableSet<GitHubUsername> missing = Sets.difference(gradesUsernames, usernames.keySet())
				.immutableCopy();
		if (!missing.isEmpty()) {
			LOGGER.warn("Missing: {}.", missing);
		}
//
		final ImmutableMap<StudentOnGitHub, IGrade> byStudent = transformed.entrySet().stream().collect(
				ImmutableMap.toImmutableMap(e -> usernames.get(GitHubUsername.given(e.getKey())), Map.Entry::getValue));
//		final ImmutableMap<StudentOnGitHub, IGrade> byStudent = transformed.entrySet().stream()
//				.collect(ImmutableMap.toImmutableMap(e -> StudentOnGitHub.with(e.getKey()), Map.Entry::getValue));
		LOGGER.debug("Grades keys: {}.", byStudent.keySet());

		final ImmutableMap<StudentOnGitHub, IGrade> byStudentCompressed = Maps.toMap(byStudent.keySet(),
				s -> Summarize.compress((WeightingGrade) byStudent.get(s)));

		LOGGER.info("Writing grades CSV.");
		Files.writeString(outDir.resolve("grades " + prefix + ".csv"), CsvGrades.asCsv(byStudentCompressed, 20d));

		LOGGER.info("Writing grades Html.");
		final Document doc = HtmlGrades.asHtml(transformed, "All grades " + prefix, 20d);
		Files.writeString(outDir.resolve("grades " + prefix + ".html"), XmlUtils.asString(doc));
	}

	public static IGrade nonZero(IGrade grade) {
		if (!(grade instanceof WeightingGrade)) {
			return grade;
		}

		final WeightingGrade w = (WeightingGrade) grade;
		final ImmutableSet<CriterionGradeWeight> subGrades = w.getSubGradesAsSet().stream()
				.filter(g -> g.getWeight() != 0d)
				.map(g -> CriterionGradeWeight.from(g.getCriterion(), nonZero(g.getGrade()), g.getWeight()))
				.collect(ImmutableSet.toImmutableSet());
		verify(!subGrades.isEmpty());
		if (subGrades.size() == 1) {
			final IGrade subGradesOnlyGrade = Iterables.getOnlyElement(subGrades).getGrade();
			LOGGER.info("Instead of {}, returning {}.", w, subGradesOnlyGrade);
			return subGradesOnlyGrade;
		}
		return w;
	}

	public static IGrade withTimePenalty(IGrade grade) {
		if (!(grade instanceof WeightingGrade)) {
			return grade;
		}

		/**
		 * More general: should observe that the small trees (a/b/c) are included in the
		 * large trees (g/a/b/c + t) and align them with zero or one weight grades
		 */
		final WeightingGrade w = (WeightingGrade) grade;
		if (w.getSubGrades().keySet().contains(Criterion.given("Time penalty"))) {
			return w;
		}
		return WeightingGrade.from(ImmutableSet.of(CriterionGradeWeight.from(Criterion.given("grade"), grade, 1d),
				CriterionGradeWeight.from(Criterion.given("Time penalty"), Mark.one("No time penalty"), 0d)));
	}

	private static Optional<ImmutableGraph<ImmutableList<Criterion>>> getCommonTree(Set<IGrade> grades) {
		checkArgument(!grades.isEmpty());

		final Optional<Set<Criterion>> commonChildrenOpt = grades.stream().map(IGrade::getSubGrades).map(Map::keySet)
				.collect(Utils.singleOrEmpty());
		final Optional<ImmutableGraph<ImmutableList<Criterion>>> common;
		if (commonChildrenOpt.isEmpty()) {
			/** ≠ children? intersect all possibilities until empty or finished. */
			Set<ImmutableGraph<ImmutableList<Criterion>>> remainingIntersection = null;
			for (IGrade grade : grades) {
				final ImmutableGraph<ImmutableList<Criterion>> basicTree = toTree(grade);
				final Optional<ImmutableGraph<ImmutableList<Criterion>>> commonSubTree = grade.getSubGrades().values()
						.stream().map(Summarize::toTree).collect(Utils.singleOrEmpty());
				final ImmutableSet.Builder<ImmutableGraph<ImmutableList<Criterion>>> builder = ImmutableSet.builder();
				builder.add(basicTree);
				if (commonSubTree.isPresent()) {
					builder.add(commonSubTree.get());
				}
				final ImmutableSet<ImmutableGraph<ImmutableList<Criterion>>> current = builder.build();
				if (remainingIntersection == null) {
					remainingIntersection = current;
				} else {
					remainingIntersection = Sets.intersection(remainingIntersection, current);
				}
				if (remainingIntersection.isEmpty()) {
					break;
				}
			}
			assert remainingIntersection != null;
			verify(remainingIntersection.size() <= 1);
			if (remainingIntersection.isEmpty()) {
				common = Optional.empty();
			} else {
				common = Optional.of(Iterables.getOnlyElement(remainingIntersection));
			}
		} else {
			/** same children? investigate each child. */
			final Set<Criterion> commonChildren = commonChildrenOpt.get();
			final ImmutableMap<Criterion, Optional<ImmutableGraph<ImmutableList<Criterion>>>> treesOptPerCriterion = Maps
					.toMap(commonChildren, c -> getCommonTree(
							grades.stream().map(g -> g.getSubGrades().get(c)).collect(ImmutableSet.toImmutableSet())));
			if (treesOptPerCriterion.values().stream().anyMatch(Optional::isEmpty)) {
				common = Optional.empty();
			} else {
				final ImmutableMap<Criterion, ImmutableGraph<ImmutableList<Criterion>>> treesPerCriterion = Maps
						.toMap(treesOptPerCriterion.keySet(), c -> treesOptPerCriterion.get(c).get());
				final ImmutableMap<Criterion, ImmutableGraph<ImmutableList<Criterion>>> rightlyRootedTrees = Maps
						.toMap(treesPerCriterion.keySet(), c -> embedInto(c, treesPerCriterion.get(c)));
				final ImmutableGraph.Builder<ImmutableList<Criterion>> builder = GraphBuilder.directed().immutable();
				for (Criterion criterion : rightlyRootedTrees.keySet()) {
					final ImmutableGraph<ImmutableList<Criterion>> tree = rightlyRootedTrees.get(criterion);
					builder.putEdge(ImmutableList.of(), ImmutableList.of(criterion));
					tree.edges().stream().forEach(builder::putEdge);
				}
				common = Optional.of(builder.build());
			}
		}
		return common;

	}

	/**
	 * @return the same tree but rooted as given and with all its paths shifted
	 *         correspondingly.
	 */
	private static ImmutableGraph<ImmutableList<Criterion>> embedInto(Criterion root,
			Graph<ImmutableList<Criterion>> tree) {
		return Utils.asImmutableGraph(tree, p -> ImmutableList.<Criterion>builderWithExpectedSize(p.size()).add(root)
				.addAll(p.subList(1, p.size() - 1)).build());
	}

	/**
	 * @param grade has all the same trees of criteria as subgrades
	 * @return the same tree of criteria, with weighted average grades. Comments are
	 *         concatenated.
	 */
	public static IGrade compress(WeightingGrade grade) {
		final ImmutableSet<ImmutableValueGraph<ImmutableList<Criterion>, Double>> valueTrees = grade.getSubGrades()
				.values().stream().map(IGrade::toValueTree).collect(ImmutableSet.toImmutableSet());
		final ImmutableSet<ImmutableGraph<ImmutableList<Criterion>>> trees = valueTrees.stream()
				.map(ImmutableValueGraph::asGraph).collect(ImmutableSet.toImmutableSet());
		checkArgument(trees.stream().distinct().count() == 1);
		final ImmutableGraph<ImmutableList<Criterion>> tree = trees.stream().distinct()
				.collect(MoreCollectors.onlyElement());
		final ImmutableValueGraph.Builder<ImmutableList<Criterion>, Double> newValueTreeBuilder = ValueGraphBuilder
				.directed().allowsSelfLoops(false).immutable();
		for (ImmutableList<Criterion> path : tree.nodes()) {
			final ImmutableSet<ImmutableList<Criterion>> pathsRooted = grade.getSubGrades().keySet().stream().map(
					c -> ImmutableList.<Criterion>builderWithExpectedSize(path.size() + 1).add(c).addAll(path).build())
					.collect(ImmutableSet.toImmutableSet());
			final double totalWeight = pathsRooted.stream().mapToDouble(p -> grade.getWeight(p)).sum();
			if (!path.isEmpty()) {
				verify(totalWeight < 1d + 1e-6d, String.format("Path: %s, total weight: %s", path, totalWeight));
				final ImmutableList<Criterion> parentPath = path.subList(0, path.size() - 1);
				newValueTreeBuilder.putEdgeValue(parentPath, path, totalWeight);
			}
		}
		final ImmutableValueGraph<ImmutableList<Criterion>, Double> newValueTree = newValueTreeBuilder.build();
		final ImmutableMap<ImmutableList<Criterion>, Mark> leafMarks = Maps.toMap(getLeafPaths(tree),
				l -> getAverageMark(grade.getSubGradesAsSet(), l, getPathWeight(newValueTree, l)));
		return toGrade(newValueTree, leafMarks);
	}

	/**
	 * @param valueTree for each path, the absolute weight of this path.
	 * @param leafMarks one mark for each leaf path
	 */
	private static IGrade toGrade(ValueGraph<ImmutableList<Criterion>, Double> valueTree,
			Map<ImmutableList<Criterion>, Mark> leafMarks) {
		final ImmutableList<Criterion> root = ImmutableList.of();
		return toGrade(valueTree, leafMarks, root);
	}

	private static IGrade toGrade(ValueGraph<ImmutableList<Criterion>, Double> valueTree,
			Map<ImmutableList<Criterion>, Mark> leafMarks, ImmutableList<Criterion> level) {
		checkArgument(valueTree.nodes().contains(level));
		/** Ether a leaf or has successors. */
		final boolean isLeaf = leafMarks.keySet().contains(level);
		final Set<ImmutableList<Criterion>> children = valueTree.successors(level);
		final boolean hasChildren = !children.isEmpty();
		verify(isLeaf != hasChildren);
		final IGrade newGrade;
		if (isLeaf) {
			newGrade = leafMarks.get(level);
		} else {
			final ImmutableSet.Builder<CriterionGradeWeight> subBuilder = ImmutableSet.builder();
			for (ImmutableList<Criterion> child : children) {
				final ImmutableList<Criterion> prefix = child.subList(0, child.size() - 1);
				verify(prefix.equals(level));
				final Criterion newSegment = child.get(child.size() - 1);
				final double totalWeight;
				if (level.isEmpty()) {
					totalWeight = 1d;
				} else {
					final ImmutableList<Criterion> previous = level.subList(0, level.size() - 1);
					totalWeight = valueTree.edgeValue(previous, level).orElseThrow(VerifyException::new);
				}
				final double childWeight = valueTree.edgeValue(level, child).orElseThrow(VerifyException::new);
				final double childRelativeWeight = childWeight / totalWeight;
				final IGrade childGrade = toGrade(valueTree, leafMarks, child);
				subBuilder.add(CriterionGradeWeight.from(newSegment, childGrade, childRelativeWeight));
			}
			newGrade = WeightingGrade.from(subBuilder.build());
		}
		return newGrade;
	}

	private static Mark getAverageMark(ImmutableSet<CriterionGradeWeight> grades, List<Criterion> leafPath,
			double weight) {
		double sumOfAbsolutePoints = 0d;
		final ImmutableList.Builder<String> commentsBuilder = ImmutableList.builder();
		for (CriterionGradeWeight sub : grades) {
			final IGrade subGrade = sub.getGrade();
			final ImmutableValueGraph<ImmutableList<Criterion>, Double> subValueTree = subGrade.toValueTree();
			final double pathWeight = sub.getWeight() * getPathWeight(subValueTree, leafPath);
			final IGrade leafGrade = subGrade.getGrade(leafPath).orElseThrow(VerifyException::new);
			final double absolutePoints = leafGrade.getPoints() * pathWeight;
			LOGGER.info("For {}, in {}, given {} weight, got {} absolute points.", leafPath, sub.getCriterion(),
					pathWeight, absolutePoints);
			sumOfAbsolutePoints += absolutePoints;
			final String comment = leafGrade.getComment();
			commentsBuilder.add(sub.getCriterion().getName() + " – " + comment);
		}
		return Mark.given(sumOfAbsolutePoints / weight,
				commentsBuilder.build().stream().collect(Collectors.joining("\n")));
	}

	private static ImmutableSet<ImmutableList<Criterion>> getLeafPaths(Graph<ImmutableList<Criterion>> tree) {
		final ImmutableList<Criterion> root = ImmutableList.of();
		checkArgument(tree.nodes().contains(root));
		checkArgument(tree.predecessors(root).isEmpty());
		return getLeafSubPaths(tree, root);
	}

	private static ImmutableSet<ImmutableList<Criterion>> getLeafSubPaths(Graph<ImmutableList<Criterion>> tree,
			ImmutableList<Criterion> path) {
		final Set<ImmutableList<Criterion>> successors = tree.successors(path);
		if (successors.isEmpty()) {
			return ImmutableSet.of(path);
		}
		final ImmutableSet.Builder<ImmutableList<Criterion>> builder = ImmutableSet.builder();
		for (ImmutableList<Criterion> subPath : successors) {
			builder.addAll(getLeafSubPaths(tree, subPath));
		}
		return builder.build();
	}
}
