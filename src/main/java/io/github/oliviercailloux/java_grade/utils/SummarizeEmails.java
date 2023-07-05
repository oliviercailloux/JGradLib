package io.github.oliviercailloux.java_grade.utils;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Sets;
import io.github.oliviercailloux.email.EmailAddress;
import io.github.oliviercailloux.git.git_hub.model.GitHubUsername;
import io.github.oliviercailloux.grade.CompositeMarksTree;
import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.Exam;
import io.github.oliviercailloux.grade.Grade;
import io.github.oliviercailloux.grade.GradeAggregator;
import io.github.oliviercailloux.grade.Mark;
import io.github.oliviercailloux.grade.MarksTree;
import io.github.oliviercailloux.grade.SubMarksTree;
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
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

public class SummarizeEmails {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(SummarizeEmails.class);

	private static final Criterion C_CC = Criterion.given("CC");

	private static final Criterion C_CC_1 = Criterion.given("two-files");

	private static final Criterion C_CC_2 = Criterion.given("branching-bis");

	private static final Criterion C_CC_3 = Criterion.given("strings second");

	private static final Criterion C_CC_4 = Criterion.given("car");

	private static final Criterion C_CC_5 = Criterion.given("colors");

	private static final Criterion C_CC_6 = Criterion.given("colors second");

	private static final Criterion C_CC_7 = Criterion.given("computer-customer");

	private static final ImmutableSet<Criterion> C_CCS = ImmutableSet.of(C_CC_1, C_CC_2, C_CC_3, C_CC_4, C_CC_5, C_CC_6,
			C_CC_7);

	private static final Criterion C_PROJECT = Criterion.given("Project");

	private static final Criterion C_PROJECT_1 = Criterion.given("Release 1");

	private static final Criterion C_PROJECT_2 = Criterion.given("Release 2");

	private static final Criterion C_PROJECT_3 = Criterion.given("Release 3");

	private static final Criterion C_PROJECT_PRES = Criterion.given("Présentation");

	private static final ImmutableSet<Criterion> C_PRS = ImmutableSet.of(C_PROJECT_1, C_PROJECT_2, C_PROJECT_3,
			C_PROJECT_PRES);

	private static final ImmutableSet<Criterion> CS = Sets.union(C_CCS, C_PRS).immutableCopy();

	public static void main(String[] args) throws Exception {
		/*
		 * keys: [two-files, branching, strings, car, strings second, Release 1, colors,
		 * colors second, Release 2, Release 3, Présentation, computer-customer]. colors
		 * second is a different one strings second replaces strings
		 */
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
			lastGrades = gradesInEmails.getLastGrades();
			LOGGER.info("Got {} grades, keys: {}.", lastGrades.size(), lastGrades.columnKeySet());
		}

		final ImmutableSet<Criterion> unused = ImmutableSet.of(Criterion.given("strings"));
		final ImmutableSet<Criterion> observedCrits = lastGrades.columnKeySet().stream().map(Criterion::given)
				.collect(ImmutableSet.toImmutableSet());
		checkState(observedCrits.equals(Sets.union(CS, unused).immutableCopy()));

		final ImmutableMap.Builder<EmailAddress, MarksTree> marksBuilder = ImmutableMap.builder();
		for (EmailAddress id : lastGrades.rowKeySet()) {
			LOGGER.info("Considering {}.", id);
			final ImmutableMap<String, Grade> grades = lastGrades.row(id);
			final CompositeMarksTree mark = toTree(grades);
			marksBuilder.put(id, mark);
		}
		final ImmutableMap<EmailAddress, MarksTree> marks = marksBuilder.build();
		final ImmutableMap<GitHubUsername, MarksTree> marksGh = CollectionUtils.transformKeys(marks,
				e -> usernames.get(e).getGitHubUsername());

		final Exam exam = new Exam(aggregator(), marksGh);
		Files.writeString(Path.of("grades.json"), JsonSimpleGrade.toJson(exam));
		final ImmutableMap<String, Grade> gradesById = exam.getUsernames().stream()
				.collect(ImmutableMap.toImmutableMap(u -> u.toString(), u -> exam.getGrade(u)));
		final Document doc = HtmlGrades.asHtmlGrades(gradesById, "All grades recap", 20d);
		Files.writeString(Path.of("grades.html"), XmlUtils.asString(doc));

		Files.writeString(Path.of("grades.csv"), CsvGrades
				.<GitHubUsername>newInstance(k -> ImmutableMap.of("name", k.toString()), CsvGrades.DEFAULT_DENOMINATOR)
				.gradesToCsv(exam.aggregator(), exam.grades()));
	}

	private static CompositeMarksTree toTree(Map<String, Grade> grades) {
		final ImmutableSet.Builder<SubMarksTree> builder = ImmutableSet.builder();
		for (Criterion criterion : CS) {
			final MarksTree mark;
			if (grades.containsKey(criterion.getName())) {
				final Grade grade = grades.get(criterion.getName());
				mark = grade.toMarksTree();
			} else {
				LOGGER.warn("Missing: {}.", criterion);
				mark = Mark.zero("No such grade");
			}
			final SubMarksTree sub = SubMarksTree.given(criterion, mark);
			builder.add(sub);
		}
		final ImmutableSet<SubMarksTree> allSubs = builder.build();
		final ImmutableSet<SubMarksTree> ccSubs = allSubs.stream().filter(s -> C_CCS.contains(s.getCriterion()))
				.collect(ImmutableSet.toImmutableSet());
		final ImmutableSet<SubMarksTree> prSubs = allSubs.stream().filter(s -> C_PRS.contains(s.getCriterion()))
				.collect(ImmutableSet.toImmutableSet());
		final CompositeMarksTree ccMark = CompositeMarksTree.givenSubGrades(ccSubs);
		final CompositeMarksTree prMark = CompositeMarksTree.givenSubGrades(prSubs);
		final CompositeMarksTree mark = CompositeMarksTree
				.givenGrades(ImmutableMap.of(C_CC, ccMark, C_PROJECT, prMark));
		return mark;
	}

	private static GradeAggregator aggregator() {
		final GradeAggregator ccA = GradeAggregator.MAX;
		final GradeAggregator prA = GradeAggregator.MAX;
		return GradeAggregator.staticAggregator(ImmutableMap.of(C_CC, 10d, C_PROJECT, 10d),
				ImmutableMap.of(C_CC, ccA, C_PROJECT, prA));
	}

}
