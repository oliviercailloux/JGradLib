package io.github.oliviercailloux.java_grade.utils;

import static com.google.common.base.Verify.verify;

import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import javax.mail.Folder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Maps;
import com.google.common.collect.Range;
import com.google.common.graph.ImmutableValueGraph;
import com.google.common.graph.ValueGraphBuilder;

import io.github.oliviercailloux.email.EmailAddress;
import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.CriterionGradeWeight;
import io.github.oliviercailloux.grade.GradeUtils;
import io.github.oliviercailloux.grade.IGrade;
import io.github.oliviercailloux.grade.WeightingGrade;
import io.github.oliviercailloux.grade.comm.Emailer;
import io.github.oliviercailloux.grade.comm.EmailerDauphineHelper;
import io.github.oliviercailloux.grade.comm.GradesInEmails;
import io.github.oliviercailloux.grade.comm.StudentOnGitHubKnown;
import io.github.oliviercailloux.grade.format.CsvGrades;
import io.github.oliviercailloux.grade.format.HtmlGrades;
import io.github.oliviercailloux.grade.format.json.JsonGrade;
import io.github.oliviercailloux.grade.mycourse.json.JsonStudentOnGitHubKnown;
import io.github.oliviercailloux.json.JsonbUtils;
import io.github.oliviercailloux.xml.XmlUtils;

public class SummarizeEmails {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(SummarizeEmails.class);

	public static void main(String[] args) throws Exception {
		final Path workDir = Paths.get("../../Java L3/");

		final ImmutableTable<EmailAddress, String, IGrade> lastGrades;
		try (GradesInEmails gradesInEmails = GradesInEmails.newInstance()) {
			@SuppressWarnings("resource")
			final Emailer emailer = gradesInEmails.getEmailer();
			emailer.connectToStore(Emailer.getZohoImapSession(), EmailerDauphineHelper.USERNAME_OTHERS,
					EmailerDauphineHelper.getZohoToken());
			@SuppressWarnings("resource")
			final Folder folder = emailer.getFolderReadWrite("Grades");
			gradesInEmails.setFolder(folder);
			gradesInEmails.filterSent(Range.atLeast(Instant.parse("2020-01-01T00:00:00.00Z")));
			lastGrades = gradesInEmails.getLastGrades();
			LOGGER.info("Got {} grades.", lastGrades.size());
		}

		final ImmutableSet<EmailAddress> addressesFound = lastGrades.rowKeySet();
		final ImmutableSet<EmailAddress> addresses = addressesFound.stream()
				.filter(a -> !a.getAddress().startsWith("olivier.cailloux@"))
				.filter(a -> !a.getAddress().startsWith("farid.")).collect(ImmutableSet.toImmutableSet());
//		final ImmutableMap<EmailAddress, WeightingGrade> grades = addresses.stream()
//				.collect(ImmutableMap.toImmutableMap(a -> a, a -> from(lastGrades.row(a), Criterion::given, s -> 1d)));

		final ImmutableMap<EmailAddress, WeightingGrade> grades = Maps.toMap(addresses,
				a -> getWeightingGrade(lastGrades.row(a)));

		@SuppressWarnings("all")
		final Type typeSet = new HashSet<StudentOnGitHubKnown>() {
		}.getClass().getGenericSuperclass();
		final Set<StudentOnGitHubKnown> usernamesAsSet = JsonbUtils.fromJson(
				Files.readString(workDir.resolve("usernames.json")), typeSet, JsonStudentOnGitHubKnown.asAdapter());

		final ImmutableMap<EmailAddress, StudentOnGitHubKnown> usernames = usernamesAsSet.stream().collect(
				ImmutableMap.toImmutableMap(s -> EmailAddress.given(s.asStudentOnMyCourse().getEmail()), s -> s));

		final ImmutableMap<String, WeightingGrade> gradesByUsername = transformKeys(grades,
				a -> usernames.get(a).getGitHubUsername());

		Files.writeString(workDir.resolve("grades recap.json"),
				JsonbUtils.toJsonValue(gradesByUsername, JsonGrade.asAdapter()).toString());
		final Document doc = HtmlGrades.asHtml(gradesByUsername, "All grades recap", 20d);
		Files.writeString(workDir.resolve("grades recap.html"), XmlUtils.asString(doc));

		Files.writeString(workDir.resolve("grades recap.csv"), CsvGrades.<String>newInstance().toCsv(gradesByUsername));
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

	public static WeightingGrade getWeightingGrade(Map<String, IGrade> grades) {
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

	public static <K> WeightingGrade from(Map<K, IGrade> grades, Function<K, Criterion> criterionFunction,
			Function<K, Double> weightFunction) {
		final ImmutableSet<CriterionGradeWeight> gradesSet = grades.keySet().stream()
				.map(k -> CriterionGradeWeight.from(criterionFunction.apply(k), grades.get(k), weightFunction.apply(k)))
				.collect(ImmutableSet.toImmutableSet());
		return WeightingGrade.from(gradesSet);
	}

}
