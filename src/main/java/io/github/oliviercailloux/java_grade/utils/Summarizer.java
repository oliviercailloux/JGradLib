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
import com.google.common.collect.Multiset.Entry;
import com.google.common.collect.Multisets;
import com.google.common.collect.Sets;
import com.google.common.collect.Streams;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.ImmutableGraph;
import io.github.oliviercailloux.git.git_hub.model.GitHubUsername;
import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.CriterionGradeWeight;
import io.github.oliviercailloux.grade.IGrade;
import io.github.oliviercailloux.grade.IGrade.CriteriaPath;
import io.github.oliviercailloux.grade.WeightingGrade;
import io.github.oliviercailloux.grade.WeightingGrade.PathGradeWeight;
import io.github.oliviercailloux.grade.comm.InstitutionalStudent;
import io.github.oliviercailloux.grade.comm.json.JsonStudentsReader;
import io.github.oliviercailloux.grade.format.CsvGrades;
import io.github.oliviercailloux.grade.format.HtmlGrades;
import io.github.oliviercailloux.grade.old.GradeStructure;
import io.github.oliviercailloux.grade.old.Mark;
import io.github.oliviercailloux.grade.utils.Compressor;
import io.github.oliviercailloux.jaris.collections.CollectionUtils;
import io.github.oliviercailloux.utils.Utils;
import io.github.oliviercailloux.xml.XmlUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

public class Summarizer {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(Summarizer.class);

	public static void main(String[] args) throws Exception {
		final Summarizer summarizer = new Summarizer().setPrefix("UML")
				.setDissolveCriteria(ImmutableSet.of(Criterion.given("Warnings")));
//		.setPatched()
//		summarizer.getReader().restrictTo(ImmutableSet.of(GitHubUsername.given("…")));
		summarizer.summarize();
	}

	public static Summarizer create() {
		return new Summarizer();
	}

	private GradesByGitHubReader reader;

	private Predicate<PathGradeWeight> filter;

	private Predicate<PathGradeWeight> dissolve;

	private Path csvOutputPath;
	private Path htmlOutputPath;
	private GradeStructure model;

	private Summarizer() {
		filter = g -> g.getWeight() != 0d;
		reader = new GradesByGitHubReader(Path.of("grades.json"));
		csvOutputPath = Path.of("grades.csv");
		htmlOutputPath = Path.of("grades.html");
		model = null;
		dissolve = g -> false;
	}

	public Path getInputPath() {
		return reader.getGradesInputPath();
	}

	public Summarizer setInputPath(Path inputPath) {
		reader.setInputPath(checkNotNull(inputPath));
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
		reader.setInputPath(Path.of("grades " + prefix + ".json"));
		csvOutputPath = Path.of("grades " + prefix + ".csv");
		htmlOutputPath = Path.of("grades " + prefix + ".html");
		return this;
	}

	public GradeStructure getModel() {
		return model;
	}

	public Summarizer setModel(GradeStructure model) {
		this.model = model;
		LOGGER.info("Set model to {}.", model);
		return this;
	}

	public Summarizer setDissolve(Predicate<PathGradeWeight> dissolve) {
		this.dissolve = checkNotNull(dissolve);
		return this;
	}

	public Summarizer setDissolveCriteria(Set<Criterion> dissolve) {
		this.dissolve = g -> dissolve.stream()
				.anyMatch(c -> g.getPath().endsWith(c) && Iterables.frequency(g.getPath(), c) == 1);
		return this;
	}

	public void summarize() throws IOException {
		final ImmutableMap<GitHubUsername, IGrade> grades = reader.readGrades();
		LOGGER.debug("Grades: {}.",
				grades.values().stream().map(g -> g.limitedDepth(1)).collect(ImmutableList.toImmutableList()));

//		final ImmutableMap<GitHubUsername, IGrade> dissolved = Maps.toMap(grades.keySet(), u -> dissolveInto(
//				dissolve(dissolveTimePenalty(grades.get(u)), Criterion.given("Penalty: commit by GitHub"))));
//		LOGGER.debug("Dissolved: {}.",
//				dissolved.values().stream().map(g -> g.toTree()).collect(ImmutableList.toImmutableList()));

		final ImmutableMap<GitHubUsername, IGrade> filtered = Maps.toMap(grades.keySet(),
//				u -> filter(dissolved.get(u)));
				u -> nonZero(grades.get(u)));
		LOGGER.info("Filtered: {}.",
				filtered.values().stream().map(g -> g.limitedDepth(1)).collect(ImmutableList.toImmutableList()));

//		if (model == null) {
//			final GradeStructure struct;
//			if (filtered.values().size() > 1) {
//				struct = getAutoModel(ImmutableSet.copyOf(filtered.values()));
//				struct = getMajoritarianModel(ImmutableSet.copyOf(filtered.values()));
//			} else {
//				struct = Iterables.getOnlyElement(filtered.values()).toTree();
//			}
//			setModel(struct);
//		}
		final ImmutableMap<GitHubUsername, IGrade> modeled = Maps.toMap(grades.keySet(), u -> model(filtered.get(u)));
		LOGGER.debug("Modeled: {}.",
				modeled.values().stream().map(g -> g.limitedDepth(1)).collect(ImmutableList.toImmutableList()));

		final ImmutableBiMap<GitHubUsername, InstitutionalStudent> usernames = readKnownUsernames();
		final ImmutableSet<GitHubUsername> missing = Sets.difference(usernames.keySet(), grades.keySet())
				.immutableCopy();
		final ImmutableSet<GitHubUsername> unknown = Sets.difference(grades.keySet(), usernames.keySet())
				.immutableCopy();
//		checkState(unknown.isEmpty(), unknown);
		if ((!missing.isEmpty()) || (!unknown.isEmpty())) {
			LOGGER.warn("Missing: {}; unknown: {}.", missing, unknown);
		}

		/* NB we want to iterate using the reading order. */
		final ImmutableSet<GitHubUsername> allUsernames = Streams
				.concat(grades.keySet().stream(), usernames.keySet().stream()).collect(ImmutableSet.toImmutableSet());
		final ImmutableMap<GitHubUsername, IGrade> completedGrades = Maps.toMap(allUsernames,
				s -> modeled.getOrDefault(s, Mark.zero("No grade")));
//		final ImmutableMap<StudentOnGitHub, IGrade> completedGradesByStudent = allUsernames.stream()
//				.collect(ImmutableMap.toImmutableMap(usernames::get, completedGrades::get));

		LOGGER.info("Writing grades Html.");
		final ImmutableMap<String, IGrade> gradesByString = CollectionUtils.transformKeys(grades,
				GitHubUsername::getUsername);
		final Document doc = HtmlGrades.asHtml(gradesByString, "All grades", 20d);
		Files.writeString(htmlOutputPath, XmlUtils.asString(doc));

		LOGGER.info("Writing grades CSV.");
		final Function<GitHubUsername, Map<String, String>> identityFunction = u -> ImmutableMap.of("Name",
				Optional.ofNullable(usernames.get(u)).map(InstitutionalStudent::getLastName).orElse(""),
				"GitHub username", u.getUsername());
		Files.writeString(csvOutputPath, CsvGrades.newInstance(identityFunction, 20d).toCsv(completedGrades));
	}

	private static IGrade dissolveTimePenalty(IGrade grade) {
		final Criterion toDissolve = Criterion.given("Time penalty");
		return dissolve(grade, toDissolve);
	}

	private static IGrade dissolve(IGrade grade, Criterion toDissolve) {
		final ImmutableSet<CriteriaPath> penaltyPaths = grade.toTree().getPaths().stream()
				.filter(p -> !p.isRoot() && p.getTail().equals(toDissolve)).collect(ImmutableSet.toImmutableSet());
		LOGGER.debug("Found penalty paths: {}.", penaltyPaths);

		final boolean allLeaves = penaltyPaths.isEmpty()
				|| GradeStructure.given(penaltyPaths).getLeaves().equals(penaltyPaths);
		checkArgument(allLeaves, penaltyPaths);

		IGrade result = grade;
		for (CriteriaPath penaltyPath : penaltyPaths) {
			result = dissolve(result, penaltyPath);
		}

		return result;
	}

	public static GradeStructure getMajoritarianModel(Set<IGrade> grades) {
		checkArgument(!grades.isEmpty());

		final Criterion userName = Criterion.given("user.name");
		final Criterion main = Criterion.given("main");
//		final ImmutableSet<GradeStructure> templates = grades.stream()
//				.filter(g -> g.getSubGrades().keySet().equals(ImmutableSet.of(userName, main))).map(IGrade::toTree)
//				.map(t -> t.getStructure(main)).collect(ImmutableSet.toImmutableSet());

		final Stream<GradeStructure> templates = grades.stream()
				.flatMap(g -> Streams.concat(Stream.of(g), g.getSubGrades().values().stream()))
				.filter(g -> g.getSubGrades().keySet().equals(ImmutableSet.of(userName, main))).map(IGrade::toTree)
				.filter(s -> !s.getPaths().contains(CriteriaPath.from("main/Code/")));
//		final Stream<GradeStructure> templates = grades.stream().map(IGrade::toTree)
//				.flatMap(s -> s.getPaths().stream().map(s::getStructure));
		final ImmutableMultiset<GradeStructure> trees = templates.collect(ImmutableMultiset.toImmutableMultiset());
		final Entry<GradeStructure> highestCountEntry = Multisets.copyHighestCountFirst(trees).entrySet().iterator()
				.next();
		final int majCount = highestCountEntry.getCount();
		LOGGER.info("Maj count: {}, among {}.", majCount, trees);
		final GradeStructure structure = trees.entrySet().stream().filter(e -> e.getCount() == majCount)
				.map(Entry::getElement).distinct().collect(MoreCollectors.onlyElement());
		LOGGER.info("Structure: {}.", structure);
		return structure;
	}

	public static GradeStructure getAutoModel(Set<IGrade> grades) {
		final Criterion userName = Criterion.given("user.name");
		final ImmutableSet<GradeStructure> templates = grades.stream()
				.filter(g -> g.getSubGrades().keySet().contains(userName)).map(IGrade::toTree)
				.collect(ImmutableSet.toImmutableSet());
		return templates.stream().map(Summarizer::getAutoModel).distinct().collect(MoreCollectors.onlyElement());
	}

	private static GradeStructure getAutoModel(GradeStructure main) {
		final ImmutableSet<Criterion> mainSubs = main.getSuccessorCriteria(CriteriaPath.ROOT);
		return mainSubs.stream().map(main::getStructure).map(Summarizer::getAutoModelFromMainSubTree).distinct()
				.collect(MoreCollectors.onlyElement());
	}

	private static GradeStructure getAutoModelFromMainSubTree(GradeStructure mainSubTree) {
		final Criterion userName = Criterion.given("user.name");
		final Criterion main = Criterion.given("main");
		final GradeStructure sU = GradeStructure.given(ImmutableSet.of(CriteriaPath.ROOT.withSuffix(userName)));
		LOGGER.debug("mainSubTree: {}.", mainSubTree);
		final GradeStructure mainEmbedded = GradeStructure.toTree(ImmutableMap.of(main, mainSubTree));
		final GradeStructure merged = GradeStructure.merge(ImmutableSet.of(sU, mainEmbedded));
		LOGGER.debug("Auto model: {}.", merged);
		return merged;
	}

	private static ImmutableBiMap<GitHubUsername, InstitutionalStudent> readKnownUsernames() throws IOException {
		LOGGER.debug("Reading usernames.");
		final JsonStudentsReader students = JsonStudentsReader.from(Files.readString(Path.of("usernames.json")));
		final ImmutableBiMap<GitHubUsername, InstitutionalStudent> usernames = students
				.getInstitutionalStudentsByGitHubUsername();
		return usernames;
	}

	public IGrade filter(IGrade grade) {
		final Optional<CriterionGradeWeight> filtered = filter(CriteriaPath.ROOT, grade);
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
	private Optional<CriterionGradeWeight> filter(CriteriaPath context, IGrade grade) {
		final IGrade result;
		final IGrade thisGrade = grade.getGrade(context).get();
		final GradeStructure tree = grade.toTree();
		final ImmutableSet<CriteriaPath> successorPaths = tree.getSuccessorPaths(context);
		LOGGER.debug("Considering {} and successors {}.", context, successorPaths);
		verify((thisGrade instanceof Mark) == successorPaths.isEmpty());
		if (successorPaths.isEmpty()) {
			result = thisGrade;
		} else {
			final ImmutableSet<CriterionGradeWeight> subGrades = successorPaths.stream().map(grade::getPathGradeWeight)
					.filter(filter).map(g -> filter(g.getPath(), grade)).flatMap(Optional::stream)
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

	public IGrade dissolveInto(IGrade grade) {
		final ImmutableSet<PathGradeWeight> pathsToDissolve = grade.toTree().getPaths().stream()
				.map(grade::getPathGradeWeight).filter(dissolve).collect(ImmutableSet.toImmutableSet());
		LOGGER.debug("Found paths to dissolve: {}.",
				pathsToDissolve.stream().map(PathGradeWeight::getPath).collect(ImmutableSet.toImmutableSet()));
		IGrade result = grade;
		for (PathGradeWeight toDissolve : pathsToDissolve) {
			result = dissolve(result, toDissolve.getPath());
		}
		return result;
	}

	private static IGrade dissolve(IGrade grade, CriteriaPath toDissolvePath) {
		checkArgument(grade.toTree().getPaths().contains(toDissolvePath));
		// TODO
//		checkArgument(grade.toTree().getSiblings(toDissolvePath).size() >= 2);
//		verify(!toDissolvePath.isRoot());
		checkArgument(!toDissolvePath.isRoot());

		LOGGER.debug("Dissolving {}.", toDissolvePath);
		final CriteriaPath parent = toDissolvePath.withoutTail();
		final Criterion toDissolveCriterion = toDissolvePath.getTail();
		/* Need to replace parent with parent-with-dissolved-child. */
		final IGrade parentGrade = grade.getGrade(parent).get();
		final IGrade newParent = parentGrade.withDissolved(toDissolveCriterion);
		final IGrade newG = grade.withSubGrade(parent, newParent);
		LOGGER.debug("Old str: {}. New str: {}.", grade.toTree(), newG.toTree());
		return newG;
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
				final ImmutableGraph.Builder<CriteriaPath> builder = GraphBuilder.directed().immutable();
				for (Criterion criterion : rightlyRootedTrees.keySet()) {
					final GradeStructure tree = rightlyRootedTrees.get(criterion);
					builder.putEdge(CriteriaPath.ROOT, CriteriaPath.ROOT.withSuffix(criterion));
					tree.asGraph().edges().stream().forEach(builder::putEdge);
				}
				common = Optional.of(GradeStructure.given(builder.build()));
			}
		}
		return common;

	}
}
