package io.github.oliviercailloux.java_grade.utils;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Verify.verify;

import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.MoreCollectors;
import com.google.common.collect.Multisets;
import com.google.common.collect.Sets;
import com.google.common.collect.Streams;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.ImmutableGraph;
import io.github.oliviercailloux.git.git_hub.model.GitHubUsername;
import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinates;
import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.CriterionGradeWeight;
import io.github.oliviercailloux.grade.GradeStructure;
import io.github.oliviercailloux.grade.IGrade;
import io.github.oliviercailloux.grade.IGrade.GradePath;
import io.github.oliviercailloux.grade.Mark;
import io.github.oliviercailloux.grade.WeightingGrade;
import io.github.oliviercailloux.grade.WeightingGrade.PathGradeWeight;
import io.github.oliviercailloux.grade.comm.StudentOnGitHub;
import io.github.oliviercailloux.grade.comm.json.JsonStudentsReader;
import io.github.oliviercailloux.grade.format.CsvGrades;
import io.github.oliviercailloux.grade.format.HtmlGrades;
import io.github.oliviercailloux.grade.format.json.JsonGrade;
import io.github.oliviercailloux.grade.utils.Compressor;
import io.github.oliviercailloux.json.JsonbUtils;
import io.github.oliviercailloux.utils.Utils;
import io.github.oliviercailloux.xml.XmlUtils;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

public class Summarizer {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(Summarizer.class);

	public static void main(String[] args) throws Exception {
		new Summarizer().setPrefix("coffee")
//		.setPatched()
				// .restrictTo(ImmutableSet.of(GitHubUsername.given(""),
				// GitHubUsername.given("")))
				.summarize();
	}

	public static Summarizer create() {
		return new Summarizer();
	}

	private Predicate<PathGradeWeight> predicate;

	private Path gradesInputPath;
	private Path csvOutputPath;
	private Path htmlOutputPath;
	private GradeStructure model;

	private ImmutableSet<GitHubUsername> restrictTo;

	private Summarizer() {
		predicate = g -> g.getWeight() != 0d;
		gradesInputPath = Path.of("grades.json");
		csvOutputPath = Path.of("grades.csv");
		htmlOutputPath = Path.of("grades.html");
		model = null;
		restrictTo = null;
	}

	public Path getGradesInputPath() {
		return gradesInputPath;
	}

	public Summarizer setGradesInputPath(Path gradesInputPath) {
		this.gradesInputPath = checkNotNull(gradesInputPath);
		return this;
	}

	public Path getCsvOutputPath() {
		return csvOutputPath;
	}

	public Summarizer setCsvOutputPath(Path csvOutputPath) {
		this.csvOutputPath = checkNotNull(csvOutputPath);
		return this;
	}

	public Path getHtmlOutputPath() {
		return htmlOutputPath;
	}

	public Summarizer setHtmlOutputPath(Path htmlOutputPath) {
		this.htmlOutputPath = checkNotNull(htmlOutputPath);
		return this;
	}

	public Summarizer setPrefix(String prefix) {
		checkNotNull(prefix);
		gradesInputPath = Path.of("grades " + prefix + ".json");
		csvOutputPath = Path.of("grades " + prefix + ".csv");
		htmlOutputPath = Path.of("grades " + prefix + ".html");
		return this;
	}

	public Summarizer setPatched() {
		final String ext = com.google.common.io.Files.getFileExtension(gradesInputPath.toString());
		final String name = com.google.common.io.Files.getNameWithoutExtension(gradesInputPath.toString());
		final Path givenParent = gradesInputPath.getParent();
		final Path parent = givenParent == null ? Path.of("") : givenParent;
		gradesInputPath = parent.resolve(name + " patched" + "." + ext);
		return this;
	}

	public GradeStructure getModel() {
		return model;
	}

	public Summarizer setModel(GradeStructure model) {
		this.model = model;
		return this;
	}

	public Summarizer restrictTo(Set<GitHubUsername> restrict) {
		this.restrictTo = ImmutableSet.copyOf(restrict);
		return this;
	}

	public void summarize() throws IOException {
		final ImmutableMap<GitHubUsername, IGrade> grades = readGrades(gradesInputPath);
		LOGGER.debug("Grades: {}.",
				grades.values().stream().map(g -> g.limitedDepth(1)).collect(ImmutableList.toImmutableList()));

		final ImmutableMap<GitHubUsername, IGrade> dissolved = Maps.toMap(grades.keySet(),
				u -> dissolveTimePenalty(grades.get(u)));
		LOGGER.debug("Dissolved: {}.",
				dissolved.values().stream().map(g -> g.limitedDepth(1)).collect(ImmutableList.toImmutableList()));

		final ImmutableMap<GitHubUsername, IGrade> filtered = Maps.toMap(grades.keySet(),
				u -> filter(dissolved.get(u)));
		LOGGER.debug("Filtered: {}.",
				filtered.values().stream().map(g -> g.limitedDepth(1)).collect(ImmutableList.toImmutableList()));

		if (model == null) {
//			final GradeStructure struct = getAutoModel(ImmutableSet.copyOf(filtered.values()));
			final GradeStructure struct = getMajoritarianModel(ImmutableSet.copyOf(filtered.values()));
			setModel(struct);
		}
		final ImmutableMap<GitHubUsername, IGrade> modeled = Maps.toMap(grades.keySet(), u -> model(filtered.get(u)));
		LOGGER.debug("Modeled: {}.",
				modeled.values().stream().map(g -> g.limitedDepth(1)).collect(ImmutableList.toImmutableList()));

		final ImmutableBiMap<GitHubUsername, StudentOnGitHub> usernames = readUsernames();
		final ImmutableSet<GitHubUsername> missing = Sets.difference(usernames.keySet(), grades.keySet())
				.immutableCopy();
		final ImmutableSet<GitHubUsername> unknown = Sets.difference(grades.keySet(), usernames.keySet())
				.immutableCopy();
		checkState(unknown.isEmpty());
		if (!missing.isEmpty()) {
			LOGGER.warn("Missing: {}.", missing);
		}

		/* NB we want to iterate using the reading order. */
		final ImmutableSet<GitHubUsername> allUsernames = Streams.concat(grades.keySet().stream(), missing.stream())
				.collect(ImmutableSet.toImmutableSet());
		final ImmutableMap<GitHubUsername, IGrade> completedGrades = Maps.toMap(allUsernames,
				s -> modeled.getOrDefault(s, Mark.zero("No grade")));
		final ImmutableMap<StudentOnGitHub, IGrade> completedGradesByStudent = allUsernames.stream()
				.collect(ImmutableMap.toImmutableMap(usernames::get, completedGrades::get));

		LOGGER.info("Writing grades Html.");
		final ImmutableMap<String, IGrade> gradesByString = Maps.toMap(
				grades.keySet().stream().map(GitHubUsername::getUsername).collect(ImmutableSet.toImmutableSet()),
				u -> grades.get(GitHubUsername.given(u)));
		final Document doc = HtmlGrades.asHtml(gradesByString, "All grades", 20d);
		Files.writeString(htmlOutputPath, XmlUtils.asString(doc));

		LOGGER.info("Writing grades CSV.");
		Files.writeString(csvOutputPath, CsvGrades.asCsv(completedGradesByStudent, 20d));
	}

	private static IGrade dissolveTimePenalty(IGrade grade) {
		final Criterion toDissolve = Criterion.given("Time penalty");
		final ImmutableSet<GradePath> penaltyPaths = grade.toTree().getPaths().stream()
				.filter(p -> !p.isRoot() && p.getTail().equals(toDissolve)).collect(ImmutableSet.toImmutableSet());
		LOGGER.debug("Found penalty paths: {}.", penaltyPaths);

		final boolean allLeaves = penaltyPaths.isEmpty()
				|| GradeStructure.given(penaltyPaths).getLeaves().equals(penaltyPaths);
		checkArgument(allLeaves, penaltyPaths);

		IGrade result = grade;
		for (GradePath penaltyPath : penaltyPaths) {
			final GradePath penaltyParentPath = penaltyPath.withoutTail();
			final IGrade dissolved = result.getGrade(penaltyParentPath).get().withDissolved(toDissolve);
			LOGGER.debug("Got dissolved: {}.", dissolved);
			result = result.withSubGrade(penaltyParentPath, dissolved);
			LOGGER.debug("Got intermediary: {}.", result);
		}

		return result;
	}

	public static GradeStructure getMajoritarianModel(Set<IGrade> grades) {
		checkArgument(!grades.isEmpty());

		final ImmutableMultiset<GradeStructure> trees = grades.stream().map(IGrade::toTree)
				.collect(ImmutableMultiset.toImmutableMultiset());
		return Multisets.copyHighestCountFirst(trees).iterator().next();
	}

	public static GradeStructure getAutoModel(Set<IGrade> grades) {
		final Criterion userName = Criterion.given("user.name");
		final Criterion main = Criterion.given("main");
		final ImmutableSet<GradeStructure> templates = grades.stream()
				.filter(g -> g.getSubGrades().keySet().equals(ImmutableSet.of(userName, main))).map(IGrade::toTree)
				.map(t -> t.getStructure(main)).collect(ImmutableSet.toImmutableSet());
		return templates.stream().map(Summarizer::getAutoModel).distinct().collect(MoreCollectors.onlyElement());
	}

	private static GradeStructure getAutoModel(GradeStructure main) {
		final ImmutableSet<Criterion> mainSubs = main.getSuccessorCriteria(GradePath.ROOT);
		return mainSubs.stream().map(main::getStructure).map(Summarizer::getAutoModelFromMainSubTree).distinct()
				.collect(MoreCollectors.onlyElement());
	}

	private static GradeStructure getAutoModelFromMainSubTree(GradeStructure mainSubTree) {
		final Criterion userName = Criterion.given("user.name");
		final Criterion main = Criterion.given("main");
		final GradeStructure sU = GradeStructure.given(ImmutableSet.of(GradePath.ROOT.withSuffix(userName)));
		LOGGER.debug("mainSubTree: {}.", mainSubTree);
		final GradeStructure mainEmbedded = GradeStructure.toTree(ImmutableMap.of(main, mainSubTree));
		final GradeStructure merged = GradeStructure.merge(ImmutableSet.of(sU, mainEmbedded));
		LOGGER.debug("Auto model: {}.", merged);
		return merged;
	}

	private static ImmutableBiMap<GitHubUsername, StudentOnGitHub> readUsernames() throws IOException {
		LOGGER.debug("Reading usernames.");
		final JsonStudentsReader students = JsonStudentsReader.from(Files.readString(Path.of("usernames.json")));
		final ImmutableBiMap<GitHubUsername, StudentOnGitHub> usernames = students.getStudentsByGitHubUsername();
		return usernames;
	}

	private ImmutableMap<GitHubUsername, IGrade> readGrades(Path input) throws IOException {
		@SuppressWarnings("all")
		final Type type = new LinkedHashMap<RepositoryCoordinates, IGrade>() {
		}.getClass().getGenericSuperclass();

		LOGGER.debug("Reading grades.");
		final String sourceGrades = Files.readString(input);
		final Map<String, IGrade> grades = JsonbUtils.fromJson(sourceGrades, type, JsonGrade.asAdapter());
		LOGGER.debug("Read keys: {}.", grades.keySet());
		final ImmutableMap<GitHubUsername, IGrade> gradesByUsername = Maps.toMap(
				grades.keySet().stream().map(GitHubUsername::given).collect(ImmutableSet.toImmutableSet()),
				u -> grades.get(u.getUsername()));

		final ImmutableMap<GitHubUsername, IGrade> restricted;
		if (restrictTo == null) {
			restricted = gradesByUsername;
		} else {
			restricted = ImmutableMap.copyOf(Maps.filterKeys(gradesByUsername, restrictTo::contains));
		}
		return restricted;
	}

	public IGrade filter(IGrade grade) {
		final Optional<CriterionGradeWeight> filtered = filter(GradePath.ROOT, grade);
		checkState(!filtered.isEmpty(), grade);
		final CriterionGradeWeight result = filtered.get();
		verify(result.getCriterion().getName().isEmpty());
		verify(result.getWeight() == 1d);
		return result.getGrade();
	}

	/**
	 * The predicate must ensure that we are not left with only subgrades with a
	 * zero weight. (TODO consider allowing only zero weight children with an
	 * arbitrary points of one – NO should still aggregate using the sub points, eg
	 * at root with single child w = 0 and 0 points would be strange to aggregate as
	 * 1 point! So better aggregate using equal weights. But then while we’re at it
	 * we should also advertise equal weights! Therefore, guarantee weights do not
	 * sum to zero is good. However we could ease construction.)
	 */
	private Optional<CriterionGradeWeight> filter(GradePath context, IGrade grade) {
		final IGrade result;
		final IGrade thisGrade = grade.getGrade(context).get();
		final GradeStructure tree = grade.toTree();
		final ImmutableSet<GradePath> successorPaths = tree.getSuccessorPaths(context);
		LOGGER.debug("Considering {} and successors {}.", context, successorPaths);
		verify((thisGrade instanceof Mark) == successorPaths.isEmpty());
		if (successorPaths.isEmpty()) {
			result = thisGrade;
		} else {
			final ImmutableSet<CriterionGradeWeight> subGrades = successorPaths.stream().map(grade::getPathGradeWeight)
					.filter(predicate).map(g -> filter(g.getPath(), grade)).flatMap(Optional::stream)
					.collect(ImmutableSet.toImmutableSet());
			LOGGER.debug("Filtering {}, obtained {}.", context, subGrades);
			if (subGrades.isEmpty()) {
				result = null;
			} else {
				// if (subGrades.size() == 1) {
				// final IGrade subGradesOnlyGrade =
				// Iterables.getOnlyElement(subGrades).getGrade();
				// LOGGER.info("Instead of {}, returning {}.", w, subGradesOnlyGrade);
				// return subGradesOnlyGrade;
				// }
				result = WeightingGrade.from(subGrades);
			}
		}
		LOGGER.debug("Filtering {}, returning {}.", context, result);
		if (result == null) {
			return Optional.empty();
		}
		final Criterion criterion = context.isRoot() ? Criterion.given("") : context.getTail();
		return Optional.of(CriterionGradeWeight.from(criterion, result, grade.getLocalWeight(context)));
	}

	public IGrade model(IGrade grade) {
		if (model == null) {
			return grade;
		}
		return Compressor.compress(grade, model);
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

	public static Optional<GradeStructure> getCommonTree(Set<IGrade> grades) {
		checkArgument(!grades.isEmpty());

		final Optional<Set<Criterion>> commonChildrenOpt = grades.stream().map(IGrade::getSubGrades).map(Map::keySet)
				.collect(Utils.singleOrEmpty());
		final Optional<GradeStructure> common;
		if (commonChildrenOpt.isEmpty()) {
			/** ≠ children? intersect all possibilities until empty or finished. */
			Set<GradeStructure> remainingIntersection = null;
			for (IGrade grade : grades) {
				LOGGER.info("Remaining intersection: {}.", remainingIntersection);
				final GradeStructure basicTree = grade.toTree();
				final Optional<GradeStructure> commonSubTree = grade.getSubGrades().values().stream()
						.map(IGrade::toTree).collect(Utils.singleOrEmpty());
				final ImmutableSet.Builder<GradeStructure> builder = ImmutableSet.builder();
				builder.add(basicTree);
				if (commonSubTree.isPresent()) {
					builder.add(commonSubTree.get());
				}
				final ImmutableSet<GradeStructure> current = builder.build();
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
			final ImmutableMap<Criterion, Optional<GradeStructure>> treesOptPerCriterion = Maps.toMap(commonChildren,
					c -> getCommonTree(
							grades.stream().map(g -> g.getSubGrades().get(c)).collect(ImmutableSet.toImmutableSet())));
			if (treesOptPerCriterion.values().stream().anyMatch(Optional::isEmpty)) {
				common = Optional.empty();
			} else {
				final ImmutableMap<Criterion, GradeStructure> treesPerCriterion = Maps
						.toMap(treesOptPerCriterion.keySet(), c -> treesOptPerCriterion.get(c).get());
				final ImmutableMap<Criterion, GradeStructure> rightlyRootedTrees = Maps
						.toMap(treesPerCriterion.keySet(), c -> treesPerCriterion.get(c).getStructure(c));
				final ImmutableGraph.Builder<GradePath> builder = GraphBuilder.directed().immutable();
				for (Criterion criterion : rightlyRootedTrees.keySet()) {
					final GradeStructure tree = rightlyRootedTrees.get(criterion);
					builder.putEdge(GradePath.ROOT, GradePath.ROOT.withSuffix(criterion));
					tree.asGraph().edges().stream().forEach(builder::putEdge);
				}
				common = Optional.of(GradeStructure.given(builder.build()));
			}
		}
		return common;

	}
}
