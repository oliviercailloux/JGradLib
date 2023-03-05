package io.github.oliviercailloux.java_grade.utils;

import static com.google.common.base.Verify.verify;

import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Maps;
import com.google.common.collect.MoreCollectors;
import com.google.common.collect.Sets;
import com.google.common.graph.ImmutableValueGraph;
import com.google.common.graph.ValueGraphBuilder;
import com.google.common.math.DoubleMath;
import io.github.oliviercailloux.email.EmailAddress;
import io.github.oliviercailloux.git.git_hub.model.GitHubUsername;
import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.CriterionGradeWeight;
import io.github.oliviercailloux.grade.Exam;
import io.github.oliviercailloux.grade.Grade;
import io.github.oliviercailloux.grade.GradeAggregator;
import io.github.oliviercailloux.grade.GradeUtils;
import io.github.oliviercailloux.grade.IGrade;
import io.github.oliviercailloux.grade.Mark;
import io.github.oliviercailloux.grade.MarksTree;
import io.github.oliviercailloux.grade.SubMarksTree;
import io.github.oliviercailloux.grade.WeightingGrade;
import io.github.oliviercailloux.grade.comm.Emailer;
import io.github.oliviercailloux.grade.comm.EmailerDauphineHelper;
import io.github.oliviercailloux.grade.comm.GradesInEmails;
import io.github.oliviercailloux.grade.comm.StudentOnGitHubKnown;
import io.github.oliviercailloux.grade.comm.json.JsonStudents;
import io.github.oliviercailloux.grade.format.CsvGrades;
import io.github.oliviercailloux.grade.format.HtmlGrades;
import io.github.oliviercailloux.grade.format.json.JsonSimpleGrade;
import io.github.oliviercailloux.jaris.collections.CollectionUtils;
import io.github.oliviercailloux.xml.XmlUtils;
import jakarta.mail.Folder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

public class SummarizeEmails {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(SummarizeEmails.class);

	private static final Criterion C_CC = Criterion.given("CC");

	private static final Criterion C_CC_1 = Criterion.given("first");

	private static final Criterion C_CC_2 = Criterion.given("branching-bis");

	private static final Criterion C_PROJECT = Criterion.given("Project");

	private static final Criterion C_PROJECT_MAIN = Criterion.given("Project main");

	private static final Criterion C_PROJECT_1 = Criterion.given("Itération 1 UML");

	private static final Criterion C_PROJECT_2 = Criterion.given("Itération 2 UML");

	private static final Criterion C_PROJECT_3 = Criterion.given("Itération 3 UML");

	private static final Criterion C_PROJECT_PRES = Criterion.given("Présentation");

	private static final Criterion C_PROJECT_4 = Criterion.given("Itération 4 UML");

	public static void main(String[] args) throws Exception {
		final ImmutableMap<GitHubUsername, MarksTree> it4 = JsonSimpleGrade
				.asExam(Files.readString(Path.of("Bonus Itération 4 UML.json"))).grades();

		final JsonStudents students = JsonStudents.from(Files.readString(Path.of("usernames.json")));
		final ImmutableMap<EmailAddress, StudentOnGitHubKnown> usernames = students.getStudentsKnownByGitHubUsername()
				.values().stream().collect(ImmutableBiMap.toImmutableBiMap(s -> s.getEmail().getAddress(), s -> s));

		final ImmutableTable<EmailAddress, String, Grade> lastGrades;
		try (GradesInEmails gradesInEmails = GradesInEmails.newInstance()) {
			@SuppressWarnings("resource")
			final Emailer emailer = gradesInEmails.getEmailer();
			emailer.connectToStore(Emailer.getZohoImapSession(), EmailerDauphineHelper.USERNAME_OTHERS,
					EmailerDauphineHelper.getZohoToken());
			@SuppressWarnings("resource")
			final Folder folder = emailer.getFolderReadWrite("Grades");
			gradesInEmails.setFolder(folder);
//			gradesInEmails.filterSent(Range.atLeast(Instant.parse("2022-06-20T00:00:00.00Z")));
			lastGrades = gradesInEmails.getLastGrades();
			LOGGER.info("Got {} grades, keys: {}.", lastGrades.size(), lastGrades.columnKeySet());
		}

		final ImmutableMap.Builder<Criterion, Double> weightsCcBuilder = ImmutableMap.builder();
		weightsCcBuilder.put(C_CC_1, 1d);
		weightsCcBuilder.put(C_CC_2, 1d);
		weightsCcBuilder.put(C_PROJECT_1, 1d);
		weightsCcBuilder.put(C_PROJECT_2, 1d);
		final ImmutableMap<Criterion, Double> weightsCc = weightsCcBuilder.build();
		final ImmutableSet<Criterion> ccs = weightsCc.keySet();
//		final ImmutableMap<Criterion, Double> weightsProject = ImmutableMap.of(C_PROJECT_2, 1d, C_PROJECT_3, 1d);
		final ImmutableList<Double> owaWeightsProjectMain = ImmutableList.of(1d, 1d, 1d, 0.75d);
		final ImmutableSet<Criterion> critsProjectMain = ImmutableSet.of(C_PROJECT_1, C_PROJECT_2, C_PROJECT_3,
				C_PROJECT_PRES);

		final ImmutableSet<EmailAddress> addresses = lastGrades.rowKeySet();
		final ImmutableMap<EmailAddress, MarksTree> grades = addresses.stream()
				.collect(ImmutableMap.toImmutableMap(a -> a,
						a -> spread(
								Maps.transformValues(CollectionUtils.transformKeys(lastGrades.row(a), Criterion::given),
										Grade::toMarksTree),
								ccs, critsProjectMain,
								it4.getOrDefault(usernames.get(a).getGitHubUsername(), Mark.zero("No bonus")))));

		final ImmutableMap<Criterion, Set<GradeAggregator>> allAggregators = lastGrades.columnKeySet().stream()
				.collect(ImmutableMap.toImmutableMap(Criterion::given, s -> lastGrades.column(s).values().stream()
						.map(Grade::toAggregator).distinct().collect(ImmutableSet.toImmutableSet())));
		final ImmutableSet<Criterion> pbl = allAggregators.keySet().stream()
				.filter(c -> allAggregators.get(c).size() >= 2).collect(ImmutableSet.toImmutableSet());
		if (pbl.size() >= 1) {
			LOGGER.info("Pbl: {}.", pbl);
			final Criterion pblC = pbl.stream().findFirst().orElseThrow();
			final Set<GradeAggregator> tooManyA = allAggregators.get(pblC);
			Files.writeString(Path.of("Pbls.json"), JsonSimpleGrade.toJson(tooManyA));
			return;
		}

		final ImmutableMap<Criterion, GradeAggregator> aggregators = lastGrades.columnKeySet().stream()
				.collect(ImmutableMap.toImmutableMap(Criterion::given, s -> lastGrades.column(s).values().stream()
						.map(Grade::toAggregator).distinct().collect(MoreCollectors.onlyElement())));
//		final ImmutableMap<Criterion, Double> weights = lastGrades.columnKeySet().stream()
//				.collect(ImmutableMap.toImmutableMap(Criterion::given, s -> 1d));
//		final ImmutableMap<Criterion, GradeAggregator> aggregators = grades.keySet().stream()
//				.collect(ImmutableMap.toImmutableMap(Criterion::given, k -> grades.get(k).toAggregator()));
		final GradeAggregator ccAggregator = GradeAggregator.staticAggregator(weightsCc,
				Maps.filterKeys(aggregators, weightsCc::containsKey));
		final GradeAggregator prjMainAggregator = GradeAggregator.owa(owaWeightsProjectMain,
				Maps.filterKeys(aggregators, critsProjectMain::contains));
		final GradeAggregator prjAggregator = GradeAggregator
				.absolute(ImmutableMap.of(C_PROJECT_MAIN, prjMainAggregator), GradeAggregator.TRIVIAL);
		final GradeAggregator bigAggregator = GradeAggregator.staticAggregator(
				ImmutableMap.of(C_CC, 10d, C_PROJECT, 10d),
				ImmutableMap.of(C_CC, ccAggregator, C_PROJECT, prjAggregator));

		// final ImmutableMap<EmailAddress, IGrade> grades = Maps.toMap(addresses,
//				a -> getWeightingGradeUML(lastGrades.row(a), a));
//		final ImmutableMap<EmailAddress, Grade> grades = lastGrades.column("various");

		final ImmutableMap<String, MarksTree> treesByUsername = transformKeys(grades,
				a -> usernames.get(a).getGitHubUsername().getUsername());

//		final ImmutableSet<GradeAggregator> aggregators = gradesByUsername.values().stream().map(Grade::toAggregator)
//				.distinct().collect(ImmutableSet.toImmutableSet());

//		final GradeAggregator aggregator = gradesByUsername.values().stream().map(Grade::toAggregator).distinct()
//				.collect(MoreCollectors.onlyElement());

		final Map<String, Grade> gradesByUsername = Maps.transformValues(treesByUsername,
				t -> Grade.given(bigAggregator, t));

		Files.writeString(Path.of("grades UML.json"), JsonSimpleGrade.toJson(
				new Exam(bigAggregator, CollectionUtils.transformKeys(treesByUsername, GitHubUsername::given))));
		// JsonbUtils.toJsonValue(gradesByUsername, JsonGrade.asAdapter()).toString());
		final Document doc = HtmlGrades.asHtmlGrades(gradesByUsername, "All grades recap", 20d);
		Files.writeString(Path.of("grades UML.html"), XmlUtils.asString(doc));

		Files.writeString(Path.of("grades UML.csv"),
				CsvGrades.<String>newInstance(k -> ImmutableMap.of("name", k.toString()), CsvGrades.DEFAULT_DENOMINATOR)
						.gradesToCsv(bigAggregator, treesByUsername));
	}

	public static <K1, K2, V> ImmutableMap<K2, V> transformKeys(Map<K1, V> source, Function<K1, K2> keyTransformation) {
		final ImmutableBiMap<K1, K2> keys = source.keySet().stream()
				.collect(ImmutableBiMap.toImmutableBiMap(k -> k, keyTransformation));
		return transformKeys(source, keys);
	}

	public static <K1, K2, V> ImmutableMap<K2, V> transformKeys(Map<K1, V> source, BiMap<K1, K2> otherKeys) {
		return source.entrySet().stream()
				.collect(ImmutableMap.toImmutableMap(e -> otherKeys.get(e.getKey()), Map.Entry::getValue));
	}

	public static WeightingGrade getWeightingGradeOld(Map<String, IGrade> grades) {
		verify(!grades.containsKey("chess-both"));
		verify(grades.containsKey("chess"));
		verify(grades.containsKey("scorers"));
		final Criterion root = Criterion.given("ROOT");
		final Criterion g = Criterion.given("git-br");
		final Criterion p = Criterion.given("print-exec");
		final Criterion coffee = Criterion.given("coffee");
		final Criterion sfb = Criterion.given("string-files-both");
		final Criterion sf = Criterion.given("string-files");
		final Criterion sfh = Criterion.given("string-files-homework");
		final Criterion cb = Criterion.given("chess-both");
		final Criterion c = Criterion.given("chess");
		final Criterion ch = Criterion.given("chess-homework");
		final Criterion sc = Criterion.given("scorers");
		final ImmutableValueGraph.Builder<Criterion, Double> staticTree = ValueGraphBuilder.directed().immutable();
		staticTree.putEdgeValue(root, g, 20d);
		staticTree.putEdgeValue(root, p, 20d);
		staticTree.putEdgeValue(root, coffee, 20d);
		if (grades.containsKey("string-files")) {
			staticTree.putEdgeValue(root, sfb, 20d);
			staticTree.putEdgeValue(sfb, sf, 6d);
			staticTree.putEdgeValue(sfb, sfh, 14d);
		} else {
			staticTree.putEdgeValue(root, sfb, 14d);
			staticTree.putEdgeValue(sfb, sfh, 14d);
		}
		staticTree.putEdgeValue(root, cb, 20d);
		staticTree.putEdgeValue(cb, c, 6d);
		staticTree.putEdgeValue(cb, ch, 14d);
		staticTree.putEdgeValue(root, sc, 20d);
		final ImmutableMap<Criterion, IGrade> gradesByCriterion = grades.entrySet().stream().collect(
				ImmutableMap.toImmutableMap(e -> Criterion.given(e.getKey()), e -> e.getValue().limitedDepth(0)));
		return (WeightingGrade) GradeUtils.toGrade(root, staticTree.build(), gradesByCriterion);
	}

	public static WeightingGrade getWeightingGradeUML(Map<String, IGrade> grades, EmailAddress a) {
		LOGGER.info("Recap UML for {}.", a);

		final ImmutableSet<Criterion> cCStrictCriteria = ImmutableSet.of("commit", "admin-manages-users").stream()
				.filter(c -> grades.keySet().contains(c)).map(Criterion::given).collect(ImmutableSet.toImmutableSet());
		final ImmutableSet<CriterionGradeWeight> cCStrictCGWs = cCStrictCriteria.stream()
				.map(c -> CriterionGradeWeight.from(c, grades.get(c.getName()).limitedDepth(0), 1d))
				.collect(ImmutableSet.toImmutableSet());
		final WeightingGrade cCStrictGrade = WeightingGrade.from(cCStrictCGWs);

		final IGrade umlIts = grades.get("Projet UML").limitedDepth(1);
		final ImmutableSortedSet<Criterion> critsCompared = byPoints(umlIts, false);
		verify(critsCompared.size() >= 2);
		final ImmutableSet<Criterion> twoBest = critsCompared.descendingSet().stream().limit(2)
				.collect(ImmutableSet.toImmutableSet());
		verify(twoBest.size() == 2);
		final ImmutableSet<CriterionGradeWeight> cCUmlCGWs = twoBest.stream()
				.map(c -> CriterionGradeWeight.from(c, umlIts.getSubGrades().get(c).limitedDepth(0), 1d))
				.collect(ImmutableSet.toImmutableSet());
		final WeightingGrade cCLargeGrade = WeightingGrade.from(Sets.union(cCStrictCGWs, cCUmlCGWs));

		final WeightingGrade cCGrade = ImmutableSortedSet
				.orderedBy(Comparator.<WeightingGrade, Double>comparing(IGrade::getPoints)).add(cCStrictGrade)
				.add(cCLargeGrade).build().descendingIterator().next();

		final ImmutableSet<Criterion> worsts = byPoints(umlIts, true).stream().limit(1)
				.collect(ImmutableSet.toImmutableSet());
		verify(worsts.size() == 1);

		final ImmutableMap<Criterion, Double> weights = umlIts.getWeights();
		final double maxWeight = weights.values().stream().max(Comparator.naturalOrder()).orElseThrow();

		final ImmutableMap.Builder<Criterion, Double> newWeightsBuilder = ImmutableMap.builder();
		for (Criterion umlSub : umlIts.getSubGrades().keySet()) {
			final double weight = weights.get(umlSub);
			final boolean laterIteration = umlSub.getName().matches("Iteration [3-6]");
			final boolean aboutMax = DoubleMath.fuzzyEquals(weight, maxWeight, 1e-6d);
			final double newWeight;
			if (aboutMax && laterIteration) {
				newWeight = weight * 2d;
			} else if (worsts.contains(umlSub)) {
				newWeight = 0d;
			} else {
				newWeight = weight;
			}
			newWeightsBuilder.put(umlSub, newWeight);
		}

		final WeightingGrade umlGrade = WeightingGrade.from(umlIts.getSubGrades(), newWeightsBuilder.build());

		return WeightingGrade.from(ImmutableSet.of(CriterionGradeWeight.from(Criterion.given("CC"), cCGrade, 1d),
				CriterionGradeWeight.from(Criterion.given("Projet"), umlGrade, 1d)));
	}

	public static WeightingGrade getWeightingGradeJava(Map<String, IGrade> grades, EmailAddress a) {
		LOGGER.info("Recap Java for {}.", a);
		verify(grades.containsKey("workers homework"));

		final ImmutableMap<Criterion, Double> cCWeights = ImmutableMap.<String, Double>builder()
				.put("git-branching", 1d).put("eclipse", 1d).put("coffee", 1d).put("persons-manager", 3d)
				.put("workers", 1d).put("workers homework", 1d).build().entrySet().stream()
				.collect(ImmutableMap.toImmutableMap(e -> Criterion.given(e.getKey()), Map.Entry::getValue));
		final ImmutableSet<Criterion> cCCriteria = cCWeights.keySet().stream()
				.filter(c -> grades.keySet().contains(c.getName())).collect(ImmutableSet.toImmutableSet());
		final ImmutableSet<CriterionGradeWeight> cCCGWs = cCCriteria.stream().map(c -> CriterionGradeWeight.from(c,
				grades.get(c.getName()).limitedDepth(0).withComment(""), cCWeights.get(c)))
				.collect(ImmutableSet.toImmutableSet());
		final WeightingGrade cCGrade = WeightingGrade.from(cCCGWs);

		final IGrade currentJavaGrade = grades.get("Projet Java").limitedDepth(1);

		final ImmutableSet<Criterion> worsts = byPoints(currentJavaGrade, true).stream().limit(1)
				.collect(ImmutableSet.toImmutableSet());
		verify(worsts.size() == 1);

		final ImmutableMap<Criterion, Double> weights = currentJavaGrade.getWeights();
		final double maxWeight = weights.values().stream().max(Comparator.naturalOrder()).orElseThrow();

		final ImmutableMap.Builder<Criterion, Double> newWeightsBuilder = ImmutableMap.builder();
		for (Criterion criterion : currentJavaGrade.getSubGrades().keySet()) {
			final double weight = weights.get(criterion);
			final double newWeight;
			if (worsts.contains(criterion)) {
				newWeight = weight / 2d;
			} else {
				newWeight = weight;
			}
			newWeightsBuilder.put(criterion, newWeight);
		}
		final Criterion presentationCriterion = Criterion.given("Présentation");
		newWeightsBuilder.put(presentationCriterion, maxWeight / 2d);

		final ImmutableMap.Builder<Criterion, IGrade> javaSubGradesBuilder = ImmutableMap.builder();
		javaSubGradesBuilder.putAll(currentJavaGrade.getSubGrades());
		javaSubGradesBuilder.put(presentationCriterion, grades.get("Présentations").withComment(""));

		final WeightingGrade javaGrade = WeightingGrade.from(javaSubGradesBuilder.build(), newWeightsBuilder.build(),
				"Weight of worst grade halved");

		return WeightingGrade.from(ImmutableSet.of(CriterionGradeWeight.from(Criterion.given("CC"), cCGrade, 1d),
				CriterionGradeWeight.from(Criterion.given("Projet"), javaGrade, 1d)));
	}

	private static ImmutableSortedSet<Criterion> byPoints(IGrade grade, boolean normalLex) {
		final Comparator<Criterion> toStringComp = Comparator.comparing(Criterion::toString);
		final Comparator<Criterion> lex = normalLex ? toStringComp : toStringComp.reversed();
		return ImmutableSortedSet.orderedBy(Comparator
				.<Criterion, Double>comparing(c -> grade.getSubGrades().get(c).getPoints()).thenComparing(lex))
				.addAll(grade.getSubGrades().keySet()).build();
	}

	public static WeightingGrade getWeightingGrade(Map<String, IGrade> grades, EmailAddress a) {
		LOGGER.info("Recap for {}.", a);
		final ImmutableMap<Criterion, IGrade> gradesByCriterion = grades.entrySet().stream()
				.collect(ImmutableMap.toImmutableMap(e -> Criterion.given(e.getKey()),
						e -> (e.getKey().equals("Projet UML") || e.getKey().equals("Projet Java"))
								? e.getValue().limitedDepth(1)
								: e.getValue().limitedDepth(0)));
		{
			// final ImmutableMap.Builder<Criterion, Double> javaWeightsBuilder =
			// ImmutableMap.builder();
			// javaWeightsBuilder.put(Criterion.given("git-branching"),
			// 1d).put(Criterion.given("eclipse"), 1d)
			// .put(Criterion.given("persons-manager"), 3d).put(Criterion.given("coffee"),
			// 1d)
			// .put(Criterion.given("admin-manages-users"), 0d).put(Criterion.given("Projet
			// UML"), 0d)
			// .put(Criterion.given("Projet Java"), 6d);
			// if (grades.containsKey("commit")) {
			// javaWeightsBuilder.put(Criterion.given("commit"), 0d);
			// }
		}
		// final ImmutableMap.Builder<Criterion, Double> umlWeightsBuilder =
		// ImmutableMap.builder();
		// umlWeightsBuilder.put(Criterion.given("git-branching"),
		// 0d).put(Criterion.given("eclipse"), 0d)
		// .put(Criterion.given("persons-manager"), 0d).put(Criterion.given("coffee"),
		// 0d)
		// .put(Criterion.given("admin-manages-users"), 1d).put(Criterion.given("Projet
		// UML"), 2d);
		// if (grades.containsKey("commit")) {
		// umlWeightsBuilder.put(Criterion.given("commit"), 1d);
		// }
		// if (grades.containsKey("Projet Java")) {
		// umlWeightsBuilder.put(Criterion.given("Projet Java"), 0d);
		// }
		return WeightingGrade.from(gradesByCriterion, Maps.asMap(gradesByCriterion.keySet(), c -> 1d));
	}

	public static <K> WeightingGrade from(Map<K, IGrade> grades, Function<K, Criterion> criterionFunction,
			Function<K, Double> weightFunction) {
		final ImmutableSet<CriterionGradeWeight> gradesSet = grades.keySet().stream()
				.map(k -> CriterionGradeWeight.from(criterionFunction.apply(k), grades.get(k), weightFunction.apply(k)))
				.collect(ImmutableSet.toImmutableSet());
		return WeightingGrade.from(gradesSet);
	}

	public static <K> MarksTree collate(Map<K, Grade> grades, Function<K, Criterion> criterionFunction) {
		final ImmutableSet<SubMarksTree> gradesSet = grades.keySet().stream()
				.map(k -> SubMarksTree.given(criterionFunction.apply(k), grades.get(k).toMarksTree()))
				.collect(ImmutableSet.toImmutableSet());
		return MarksTree.composite(gradesSet);
	}

	public static MarksTree spread(Map<Criterion, MarksTree> marks, ImmutableSet<Criterion> ccs,
			ImmutableSet<Criterion> projects, MarksTree it4) {
		final ImmutableSet<SubMarksTree> marksCC = ccs.stream()
				.map(c -> SubMarksTree.given(c, marks.getOrDefault(c, Mark.zero("No such grade"))))
				.collect(ImmutableSet.toImmutableSet());
		final ImmutableSet<SubMarksTree> marksPrj = projects.stream()
				.map(c -> SubMarksTree.given(c, marks.getOrDefault(c, Mark.zero("No such grade"))))
				.collect(ImmutableSet.toImmutableSet());
		final SubMarksTree cc = SubMarksTree.given(C_CC, MarksTree.composite(marksCC));
		final SubMarksTree main = SubMarksTree.given(C_PROJECT_MAIN, MarksTree.composite(marksPrj));
		final SubMarksTree it4sub = SubMarksTree.given(C_PROJECT_4, it4);
		final SubMarksTree prj = SubMarksTree.given(C_PROJECT, MarksTree.composite(ImmutableSet.of(main, it4sub)));
		return MarksTree.composite(ImmutableSet.of(cc, prj));
	}

}
