package io.github.oliviercailloux.java_grade.utils;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Verify.verify;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
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

public class Summarize {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(Summarize.class);

	public static void main(String[] args) throws Exception {
		new Summarize().setPrefix("admin-manages-users").summarize();
	}

	private Predicate<PathGradeWeight> predicate;

	private Path gradesInputPath;
	private Path csvOutputPath;
	private Path htmlOutputPath;
	private GradeStructure model;

	private Summarize() {
		predicate = g -> g.getWeight() != 0d;
		gradesInputPath = Path.of("grades.json");
		csvOutputPath = Path.of("grades.csv");
		htmlOutputPath = Path.of("grades.html");
		model = null;
	}

	public Path getGradesInputPath() {
		return gradesInputPath;
	}

	public Summarize setGradesInputPath(Path gradesInputPath) {
		this.gradesInputPath = checkNotNull(gradesInputPath);
		return this;
	}

	public Path getCsvOutputPath() {
		return csvOutputPath;
	}

	public Summarize setCsvOutputPath(Path csvOutputPath) {
		this.csvOutputPath = checkNotNull(csvOutputPath);
		return this;
	}

	public Path getHtmlOutputPath() {
		return htmlOutputPath;
	}

	public Summarize setHtmlOutputPath(Path htmlOutputPath) {
		this.htmlOutputPath = checkNotNull(htmlOutputPath);
		return this;
	}

	public Summarize setPrefix(String prefix) {
		checkNotNull(prefix);
		gradesInputPath = Path.of("grades " + prefix + ".json");
		csvOutputPath = Path.of("grades " + prefix + ".csv");
		htmlOutputPath = Path.of("grades " + prefix + ".html");
		return this;
	}

	public GradeStructure getModel() {
		return model;
	}

	public Summarize setModel(GradeStructure model) {
		this.model = model;
		return this;
	}

	public void summarize() throws IOException {
		final Map<GitHubUsername, IGrade> grades = readGrades(gradesInputPath);
		final ImmutableMap<GitHubUsername, IGrade> filtered = Maps.toMap(grades.keySet(), u -> filter(grades.get(u)));
		final ImmutableMap<GitHubUsername, IGrade> modeled = Maps.toMap(grades.keySet(), u -> model(filtered.get(u)));

		final ImmutableBiMap<GitHubUsername, StudentOnGitHub> usernames = readUsernames();
		final ImmutableSet<GitHubUsername> missing = Sets.difference(usernames.keySet(), grades.keySet())
				.immutableCopy();
		final ImmutableSet<GitHubUsername> unknown = Sets.difference(grades.keySet(), usernames.keySet())
				.immutableCopy();
		checkState(unknown.isEmpty());
		if (!missing.isEmpty()) {
			LOGGER.warn("Missing: {}.", unknown);
		}

		final ImmutableMap<StudentOnGitHub, IGrade> completedGrades = Maps.toMap(usernames.values(),
				s -> modeled.getOrDefault(usernames.inverse().get(s), Mark.zero("No grade")));

		LOGGER.info("Writing grades CSV.");
		Files.writeString(csvOutputPath, CsvGrades.asCsv(completedGrades, 20d));

		LOGGER.info("Writing grades Html.");
		final ImmutableMap<String, IGrade> gradesByString = Maps.toMap(
				grades.keySet().stream().map(GitHubUsername::getUsername).collect(ImmutableSet.toImmutableSet()),
				u -> grades.get(GitHubUsername.given(u)));
		final Document doc = HtmlGrades.asHtml(gradesByString, "All grades", 20d);
		Files.writeString(htmlOutputPath, XmlUtils.asString(doc));
	}

	private static ImmutableBiMap<GitHubUsername, StudentOnGitHub> readUsernames() throws IOException {
		LOGGER.debug("Reading usernames.");
		final JsonStudentsReader students = JsonStudentsReader.from(Files.readString(Path.of("usernames.json")));
		final ImmutableBiMap<GitHubUsername, StudentOnGitHub> usernames = students.getStudentsByGitHubUsername();
		return usernames;
	}

	private static ImmutableMap<GitHubUsername, IGrade> readGrades(Path input) throws IOException {
		@SuppressWarnings("all")
		final Type type = new LinkedHashMap<RepositoryCoordinates, IGrade>() {
		}.getClass().getGenericSuperclass();

		LOGGER.debug("Reading grades.");
		final String sourceGrades = Files.readString(input);
		final Map<String, IGrade> grades = JsonbUtils.fromJson(sourceGrades, type, JsonGrade.asAdapter());
		LOGGER.debug("Read {}, keys: {}.", sourceGrades, grades.keySet());
		final ImmutableMap<GitHubUsername, IGrade> gradesByUsername = Maps.toMap(
				grades.keySet().stream().map(GitHubUsername::given).collect(ImmutableSet.toImmutableSet()),
				u -> grades.get(u.getUsername()));
		return gradesByUsername;
	}

	public IGrade filter(IGrade grade) {
		final Optional<CriterionGradeWeight> filtered = filter(GradePath.ROOT, grade);
		checkState(!filtered.isEmpty());
		final CriterionGradeWeight result = filtered.get();
		verify(result.getCriterion().getName().isEmpty());
		verify(result.getWeight() == 1d);
		return result.getGrade();
	}

	public IGrade model(IGrade grade) {
		if (model == null) {
			return grade;
		}
		return Compressor.compress(grade, model);
	}

	/**
	 * The predicate must ensure that we are not left with only subgrades with a
	 * zero weight. (TODO consider allowing only zero weight children with an
	 * arbitrary points of one)
	 */
	private Optional<CriterionGradeWeight> filter(GradePath context, IGrade grade) {
		final IGrade result;
		if (!(grade instanceof WeightingGrade)) {
			result = grade;
		} else {
			final ImmutableSet<CriterionGradeWeight> subGrades = grade.toTree().getSuccessorPaths(context).stream()
					.map(grade::getPathGradeWeight).filter(predicate).map(g -> filter(g.getPath(), grade))
					.flatMap(Optional::stream).collect(ImmutableSet.toImmutableSet());
			if (subGrades.isEmpty()) {
				result = null;
			} else {
//		if (subGrades.size() == 1) {
//			final IGrade subGradesOnlyGrade = Iterables.getOnlyElement(subGrades).getGrade();
//			LOGGER.info("Instead of {}, returning {}.", w, subGradesOnlyGrade);
//			return subGradesOnlyGrade;
//		}
				result = WeightingGrade.from(subGrades);
			}
		}
		if (result == null) {
			return Optional.empty();
		}
		final Criterion criterion = context.isRoot() ? Criterion.given("") : context.getTail();
		return Optional.of(CriterionGradeWeight.from(criterion, result, grade.getLocalWeight(context)));
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

	private static Optional<GradeStructure> getCommonTree(Set<IGrade> grades) {
		checkArgument(!grades.isEmpty());

		final Optional<Set<Criterion>> commonChildrenOpt = grades.stream().map(IGrade::getSubGrades).map(Map::keySet)
				.collect(Utils.singleOrEmpty());
		final Optional<GradeStructure> common;
		if (commonChildrenOpt.isEmpty()) {
			/** ≠ children? intersect all possibilities until empty or finished. */
			Set<GradeStructure> remainingIntersection = null;
			for (IGrade grade : grades) {
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
				final ImmutableGraph.Builder<ImmutableList<Criterion>> builder = GraphBuilder.directed().immutable();
				for (Criterion criterion : rightlyRootedTrees.keySet()) {
					final GradeStructure tree = rightlyRootedTrees.get(criterion);
					builder.putEdge(ImmutableList.of(), ImmutableList.of(criterion));
					tree.edges().stream().forEach(builder::putEdge);
				}
				common = Optional.of(builder.build());
			}
		}
		return common;

	}
}
