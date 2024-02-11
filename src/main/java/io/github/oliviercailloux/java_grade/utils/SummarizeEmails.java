package io.github.oliviercailloux.java_grade.utils;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import io.github.oliviercailloux.email.EmailAddress;
import io.github.oliviercailloux.git.git_hub.model.GitHubUsername;
import io.github.oliviercailloux.grade.CompositeMarksTree;
import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.Exam;
import io.github.oliviercailloux.grade.Grade;
import io.github.oliviercailloux.grade.GradeAggregator;
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

	private static final Criterion C_CC_GIT = Criterion.given("git");

	private static final Criterion C_CC_1_TWO_FILES = Criterion.given("two-files");

	private static final Criterion C_CC_2_BR = Criterion.given("branching");

	private static final Criterion C_CC_3_STRINGS = Criterion.given("strings second");

	private static final Criterion C_CC_4_CAR = Criterion.given("car");

	private static final Criterion C_CC_COLORS = Criterion.given("colors");

	private static final Criterion C_CC_5_COLORS_1 = Criterion.given("colors");

	private static final Criterion C_CC_6_COLORS_2 = Criterion.given("colors second");

	private static final Criterion C_CC_JAVA_INTERMEDIATE = Criterion.given("java intermediate");

	private static final Criterion C_CC_7_COMP_CUST = Criterion.given("computer-customer");

	private static final Criterion C_CC_JAVA = Criterion.given("java");

	private static final ImmutableSet<Criterion> C_CCS = ImmutableSet.of(C_CC_1_TWO_FILES, C_CC_2_BR,
			C_CC_3_STRINGS, C_CC_4_CAR, C_CC_5_COLORS_1, C_CC_6_COLORS_2, C_CC_7_COMP_CUST);

	private static final Criterion C_PROJECT = Criterion.given("Project");

	private static final Criterion C_PROJECT_1 = Criterion.given("Release 1");

	private static final Criterion C_PROJECT_2 = Criterion.given("Release 2");

	private static final Criterion C_PROJECT_3 = Criterion.given("Release 3");

	private static final Criterion C_PROJECT_PRES = Criterion.given("Présentation");

	private static final ImmutableSet<Criterion> C_PRS =
			ImmutableSet.of(C_PROJECT_1, C_PROJECT_2, C_PROJECT_3, C_PROJECT_PRES);

	private static final ImmutableSet<Criterion> CS = Sets.union(C_CCS, C_PRS).immutableCopy();

	public static void main(String[] args) throws Exception {
		/*
		 * keys: [two-files, branching, strings, car, strings second, Release 1, colors, colors second,
		 * Release 2, Release 3, Présentation, computer-customer]. colors second is a different one
		 * strings second replaces strings
		 */
		final JsonStudents students = JsonStudents.from(Files.readString(Path.of("usernames.json")));
		final ImmutableMap<EmailAddress, StudentOnGitHubKnown> usernames =
				students.getStudentsKnownByGitHubUsername().values().stream()
						.collect(ImmutableBiMap.toImmutableBiMap(s -> s.getEmail().getAddress(), s -> s));

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

		final ImmutableSet<Criterion> unused =
				ImmutableSet.of(Criterion.given("strings"), Criterion.given("final"));
		final ImmutableSet<Criterion> observedCrits = lastGrades.columnKeySet().stream()
				.map(Criterion::given).collect(ImmutableSet.toImmutableSet());
		final ImmutableSet<Criterion> expected = Sets.union(CS, unused).immutableCopy();
		checkState(observedCrits.equals(expected), Sets.symmetricDifference(observedCrits, expected));

		final ImmutableMap.Builder<EmailAddress, MarksTree> marksBuilder = ImmutableMap.builder();
		for (EmailAddress id : lastGrades.rowKeySet()) {
			LOGGER.debug("Considering {}.", id);
			final ImmutableMap<String, Grade> grades = lastGrades.row(id);
			final ImmutableMap<Criterion, Grade> gradesByC =
					CollectionUtils.transformKeys(grades, Criterion::given);
			final ImmutableMap<Criterion, MarksTree> marksByC =
					ImmutableMap.copyOf(Maps.transformValues(gradesByC, Grade::toMarksTree));
			final CompositeMarksTree mark = toTree(marksByC);
			marksBuilder.put(id, mark);
		}
		final ImmutableMap<EmailAddress, MarksTree> marks = marksBuilder.build();
		final ImmutableMap<GitHubUsername, MarksTree> marksGh =
				CollectionUtils.transformKeys(marks, e -> usernames.get(e).getGitHubUsername());

		final ImmutableMap.Builder<Criterion, GradeAggregator> builder = ImmutableMap.builder();
		for (Criterion criterion : CS) {
			final ImmutableMap<EmailAddress, Grade> grades = lastGrades.column(criterion.getName());
			final ImmutableSet<GradeAggregator> aggrs =
					grades.values().stream().map(Grade::toAggregator).collect(ImmutableSet.toImmutableSet());
			checkState(aggrs.size() == 1);
			final GradeAggregator aggr = Iterables.getOnlyElement(aggrs);
			builder.put(criterion, aggr);
		}
		final ImmutableMap<Criterion, GradeAggregator> aggregators = builder.build();

		final Exam exam = new Exam(aggregator(aggregators), marksGh);
		Files.writeString(Path.of("grades.json"), JsonSimpleGrade.toJson(exam));
		final ImmutableMap<String, Grade> gradesById = exam.getUsernames().stream()
				.collect(ImmutableMap.toImmutableMap(u -> u.getUsername(), u -> exam.getGrade(u)));
		final Document doc = HtmlGrades.asHtmlGrades(gradesById, "All grades recap", 20d);
		Files.writeString(Path.of("grades.html"), XmlUtils.asString(doc));

		Files.writeString(Path.of("grades.csv"),
				CsvGrades.<GitHubUsername>newInstance(k -> ImmutableMap.of("username", k.getUsername()),
						CsvGrades.DEFAULT_DENOMINATOR).gradesToCsv(exam.aggregator(), exam.grades()));
	}

	private static CompositeMarksTree toTree(Map<Criterion, MarksTree> marks) {
		final CompositeMarksTree gitMark = CompositeMarksTree.givenGrades(ImmutableMap
				.of(C_CC_1_TWO_FILES, marks.get(C_CC_1_TWO_FILES), C_CC_2_BR, marks.get(C_CC_2_BR)));
		final CompositeMarksTree colorsMark =
				CompositeMarksTree.givenGrades(ImmutableMap.of(C_CC_5_COLORS_1, marks.get(C_CC_5_COLORS_1),
						C_CC_6_COLORS_2, marks.get(C_CC_6_COLORS_2)));
		final CompositeMarksTree interMark;
		if (marks.containsKey(C_CC_4_CAR)) {
			interMark = CompositeMarksTree.givenGrades(ImmutableMap.of(C_CC_3_STRINGS,
					marks.get(C_CC_3_STRINGS), C_CC_4_CAR, marks.get(C_CC_4_CAR), C_CC_COLORS, colorsMark));
		} else {
			interMark = CompositeMarksTree.givenGrades(
					ImmutableMap.of(C_CC_3_STRINGS, marks.get(C_CC_3_STRINGS), C_CC_COLORS, colorsMark));
		}
		final CompositeMarksTree javaMark = CompositeMarksTree.givenGrades(ImmutableMap
				.of(C_CC_JAVA_INTERMEDIATE, interMark, C_CC_7_COMP_CUST, marks.get(C_CC_7_COMP_CUST)));
		final CompositeMarksTree ccMark =
				CompositeMarksTree.givenGrades(ImmutableMap.of(C_CC_GIT, gitMark, C_CC_JAVA, javaMark));
		final ImmutableSet.Builder<SubMarksTree> builder = ImmutableSet.builder();
		for (Criterion criterion : C_PRS) {
			checkArgument(marks.containsKey(criterion));
			final MarksTree mark = marks.get(criterion);
			final SubMarksTree sub = SubMarksTree.given(criterion, mark);
			builder.add(sub);
		}
		final ImmutableSet<SubMarksTree> prSubs = builder.build();
		final CompositeMarksTree prMark = CompositeMarksTree.givenSubGrades(prSubs);
		final CompositeMarksTree mark =
				CompositeMarksTree.givenGrades(ImmutableMap.of(C_CC, ccMark, C_PROJECT, prMark));
		return mark;
	}

	private static GradeAggregator aggregator(Map<Criterion, GradeAggregator> subs) {
		checkArgument(subs.keySet().equals(CS));

		// final GradeAggregator git = GradeAggregator
		// .staticAggregator(ImmutableMap.of(C_CC_1_TWO_FILES, 0.35, C_CC_2_BR, 0.65),
		final GradeAggregator git = GradeAggregator.owa(ImmutableList.of(0.65d, 0.35d), ImmutableMap
				.of(C_CC_1_TWO_FILES, subs.get(C_CC_1_TWO_FILES), C_CC_2_BR, subs.get(C_CC_2_BR)));
		final GradeAggregator colors = GradeAggregator.staticAggregator(
				ImmutableMap.of(C_CC_5_COLORS_1, 1d, C_CC_6_COLORS_2, 9d), ImmutableMap.of(C_CC_5_COLORS_1,
						subs.get(C_CC_5_COLORS_1), C_CC_6_COLORS_2, subs.get(C_CC_6_COLORS_2)));
		// final GradeAggregator inter = GradeAggregator.staticAggregator(
		// ImmutableMap.of(C_CC_3_STRINGS, 7d, C_CC_4_CAR, 8d, C_CC_COLORS, 5d),
		// ImmutableMap.of(C_CC_3_STRINGS,
		final GradeAggregator inter =
				GradeAggregator.owa(ImmutableList.of(8d, 7d, 5d), ImmutableMap.of(C_CC_3_STRINGS,
						subs.get(C_CC_3_STRINGS), C_CC_4_CAR, subs.get(C_CC_4_CAR), C_CC_COLORS, colors));
		final GradeAggregator java = GradeAggregator.staticAggregator(
				ImmutableMap.of(C_CC_JAVA_INTERMEDIATE, 12d, C_CC_7_COMP_CUST, 8d), ImmutableMap
						.of(C_CC_JAVA_INTERMEDIATE, inter, C_CC_7_COMP_CUST, subs.get(C_CC_7_COMP_CUST)));
		final GradeAggregator cc =
				GradeAggregator.staticAggregator(ImmutableMap.of(C_CC_GIT, 3d, C_CC_JAVA, 17d),
						ImmutableMap.of(C_CC_GIT, git, C_CC_JAVA, java));

		final Map<Criterion, GradeAggregator> prSubs = Maps.filterKeys(subs, c -> C_PRS.contains(c));
		final GradeAggregator pr = GradeAggregator.staticAggregator(
				ImmutableMap.of(C_PROJECT_1, 9d, C_PROJECT_2, 9d, C_PROJECT_3, 16d, C_PROJECT_PRES, 6d),
				prSubs);
		return GradeAggregator.staticAggregator(ImmutableMap.of(C_CC, 12d, C_PROJECT, 8d),
				ImmutableMap.of(C_CC, cc, C_PROJECT, pr));
	}
}
