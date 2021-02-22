package io.github.oliviercailloux.java_grade.utils;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Verify.verify;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

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
import io.github.oliviercailloux.grade.WeightingGrade.WeightedGrade;
import io.github.oliviercailloux.grade.WeightingGrade.WeightedMark;
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

	public static void main(String[] args) throws Exception {
		summarize("admin-manages-users", Paths.get(""), true);
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
