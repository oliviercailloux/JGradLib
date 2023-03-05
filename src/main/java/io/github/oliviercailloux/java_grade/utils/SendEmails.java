package io.github.oliviercailloux.java_grade.utils;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.math.Quantiles;
import com.google.common.math.Stats;
import io.github.oliviercailloux.email.EmailAddress;
import io.github.oliviercailloux.email.EmailAddressAndPersonal;
import io.github.oliviercailloux.git.git_hub.model.GitHubUsername;
import io.github.oliviercailloux.grade.Exam;
import io.github.oliviercailloux.grade.Grade;
import io.github.oliviercailloux.grade.Mark;
import io.github.oliviercailloux.grade.MarksTree;
import io.github.oliviercailloux.grade.comm.Email;
import io.github.oliviercailloux.grade.comm.Emailer;
import io.github.oliviercailloux.grade.comm.EmailerDauphineHelper;
import io.github.oliviercailloux.grade.comm.GradesInEmails;
import io.github.oliviercailloux.grade.comm.json.JsonStudents;
import io.github.oliviercailloux.grade.format.json.JsonSimpleGrade;
import io.github.oliviercailloux.xml.XmlUtils;
import jakarta.mail.Folder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SendEmails {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(SendEmails.class);

	private static final Path WORK_DIR = Path.of("");

	public static void main(String[] args) throws Exception {
//		final String prefix = "Présentation";
//		final String prefix = GraderDiceRoller.PREFIX;
		final String prefix = "recap Java";

		final JsonStudents students = JsonStudents.from(Files.readString(WORK_DIR.resolve("usernames.json")));

		final Exam exam = JsonSimpleGrade.asExam(Files.readString(WORK_DIR.resolve("grades " + prefix + ".json")));
		final boolean allKnown = students.getInstitutionalStudentsByGitHubUsername().keySet()
				.containsAll(exam.getUsernames());
		checkState(allKnown,
				Sets.difference(exam.getUsernames(), students.getInstitutionalStudentsByGitHubUsername().keySet()));

		final ImmutableSet<GitHubUsername> missing = Sets
				.difference(students.getInstitutionalStudentsByGitHubUsername().keySet(), exam.getUsernames())
				.immutableCopy();
		if (!missing.isEmpty()) {
			LOGGER.warn("Missing: {}.", missing);
		}

		final Mark defaultMark = Mark.given(0d, "GitHub repository not found");
		final ImmutableMap<EmailAddressAndPersonal, MarksTree> marksByEmail = exam.getUsernames().stream()
				.collect(ImmutableMap.toImmutableMap(
						u -> students.getInstitutionalStudentsByGitHubUsername().get(u).getEmail(),
						u -> exam.getUsernames().contains(u) ? exam.getGrade(u).toMarksTree() : defaultMark));
		final ImmutableMap<EmailAddressAndPersonal, Grade> gradesByEmail = ImmutableMap
				.copyOf(Maps.transformValues(marksByEmail, m -> Grade.given(exam.aggregator(), m)));

		final ImmutableList<Double> points = gradesByEmail.values().stream().map(Grade::mark).map(Mark::getPoints)
				.collect(ImmutableList.toImmutableList());
		final Stats stats = Stats.of(points);
		final Map<Integer, Double> quartiles = Quantiles.quartiles().indexes(1, 2, 3).compute(points);

		try (GradesInEmails gradesInEmails = GradesInEmails.newInstance()) {
			@SuppressWarnings("resource")
			final Emailer emailer = gradesInEmails.getEmailer();
			EmailerDauphineHelper.connect(emailer);
			@SuppressWarnings("resource")
			final Folder folder = emailer.getFolderReadWrite("Grades");
			gradesInEmails.setFolder(folder);

			final ImmutableSet<EmailAddress> addresses = gradesByEmail.keySet().stream()
					.map(EmailAddressAndPersonal::getAddress).collect(ImmutableSet.toImmutableSet());
			gradesInEmails.filterRecipients(addresses);
			final ImmutableMap<EmailAddress, Grade> lastGrades = gradesInEmails.getLastGrades(prefix);
			LOGGER.debug("Searching grades sent to {}, got those sent to {}.", addresses, lastGrades.keySet());

//			checkState(!lastGrades.isEmpty());

			for (EmailAddressAndPersonal address : gradesByEmail.keySet()) {
				if (!lastGrades.containsKey(address.getAddress())) {
					LOGGER.info("Not found {} among {}.", address, lastGrades.keySet());
				}
			}

			final Map<EmailAddressAndPersonal, Grade> gradesDiffering = gradesByEmail.entrySet().stream()
					.filter(e -> isDiff(Optional.ofNullable(lastGrades.get(e.getKey().getAddress())), e.getValue()))
					// .filter(e -> !e.getKey().getPersonal().contains("…"))
					.collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));

			for (EmailAddressAndPersonal address : gradesDiffering.keySet()) {
				final Grade lastGrade = lastGrades.get(address.getAddress());
				if (lastGrade == null) {
					continue;
				}
				final Grade gradeFromJson = gradesByEmail.get(address);
				// LOGGER.info("Diff {}: {} (before {}, after {}).", address, getDiff(lastGrade,
				// gradeFromJson),
				// lastGrade, gradeFromJson);
				final double before = lastGrade.mark().getPoints();
				final double after = gradeFromJson.mark().getPoints();
				LOGGER.info("Diff {} (before {}, after {}).", address, before, after);
				if (before > after) {
					LOGGER.warn("Losing points.");
				}
			}

			final ImmutableSet<Email> emails = gradesDiffering.entrySet().stream().map(
					e -> GradesInEmails.asEmail(getDestination(e.getKey()), prefix, e.getValue(), stats, quartiles))
					.collect(ImmutableSet.toImmutableSet());

			final Optional<Email> first = emails.stream().findFirst();
			LOGGER.info("Prepared first doc (out of {}): {}, to {}.", emails.size(),
					first.map(Email::getDocument).map(XmlUtils::asString), first.map(Email::getTo));
			// LOGGER.info("Prepared {}.", effectiveEmails);

			emailer.saveInto(folder);
//			emailer.send(emails, EmailerDauphineHelper.FROM);
		}
	}

	private static boolean isDiff(Optional<Grade> lastGrade, Grade current) {
		final boolean isSame = current.toAggregator().equals(lastGrade.map(Grade::toAggregator).orElse(null))
				&& current.toMarksTree().equals(lastGrade.map(Grade::toMarksTree).orElse(null));
		return !isSame;
//		return !DoubleMath.fuzzyEquals(lastGrade.map(IGrade::getPoints).orElse(-1d), current.getPoints(), 1e-8d);
	}

	private static EmailAddressAndPersonal getDestination(EmailAddressAndPersonal e) {
		return e;
//		return EmailAddressAndPersonal.given(EmailerDauphineHelper.FROM.getAddress().getAddress(), e.getPersonal());
	}
}
