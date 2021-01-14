package io.github.oliviercailloux.java_grade.utils;

import static com.google.common.base.Verify.verify;

import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.mail.Folder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Verify;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.math.Quantiles;
import com.google.common.math.Stats;

import io.github.oliviercailloux.email.EmailAddress;
import io.github.oliviercailloux.email.EmailAddressAndPersonal;
import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.IGrade;
import io.github.oliviercailloux.grade.Mark;
import io.github.oliviercailloux.grade.WeightingGrade;
import io.github.oliviercailloux.grade.comm.Email;
import io.github.oliviercailloux.grade.comm.Emailer;
import io.github.oliviercailloux.grade.comm.EmailerDauphineHelper;
import io.github.oliviercailloux.grade.comm.GradesInEmails;
import io.github.oliviercailloux.grade.comm.StudentOnGitHubKnown;
import io.github.oliviercailloux.grade.format.json.JsonGrade;
import io.github.oliviercailloux.grade.mycourse.json.JsonStudentOnGitHubKnown;
import io.github.oliviercailloux.json.JsonbUtils;
import io.github.oliviercailloux.xml.XmlUtils;

public class SendEmails {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(SendEmails.class);

	private static final String PREFIX = "commit";
	private static final Path WORK_DIR = Path.of("");

	public static void main(String[] args) throws Exception {
		@SuppressWarnings("all")
		final Type typeSet = new HashSet<StudentOnGitHubKnown>() {
		}.getClass().getGenericSuperclass();
		final Set<StudentOnGitHubKnown> usernamesAsSet = JsonbUtils.fromJson(
				Files.readString(WORK_DIR.resolve("usernames.json")), typeSet, JsonStudentOnGitHubKnown.asAdapter());

		final ImmutableMap<String, StudentOnGitHubKnown> usernames = usernamesAsSet.stream()
				.collect(ImmutableMap.toImmutableMap(s -> s.getGitHubUsername(), s -> s));

		@SuppressWarnings("all")
		final Type type = new HashMap<String, IGrade>() {
		}.getClass().getGenericSuperclass();
		final Map<String, IGrade> gradesByString = JsonbUtils.fromJson(
				Files.readString(WORK_DIR.resolve("grades " + PREFIX + ".json")), type, JsonGrade.asAdapter());
		final ImmutableMap<EmailAddressAndPersonal, IGrade> gradesByEmail = gradesByString.entrySet().stream()
				.filter(e -> !e.getKey().equals("Humbledon")).collect(ImmutableMap
						.toImmutableMap(e -> GradesInEmails.asAddress(usernames.get(e.getKey())), Map.Entry::getValue));

		final ImmutableList<Double> points = gradesByString.values().stream().map(IGrade::getPoints)
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
			final ImmutableMap<EmailAddress, IGrade> lastGrades = gradesInEmails.getLastGrades(PREFIX);
			LOGGER.debug("Searching grades sent to {}, got those sent to {}.", addresses, lastGrades.keySet());

			for (EmailAddressAndPersonal address : gradesByEmail.keySet()) {
				final Optional<IGrade> lastGradeOpt = Optional.ofNullable(lastGrades.get(address.getAddress()));
				if (lastGradeOpt.isPresent()) {
					final IGrade lastGrade = lastGradeOpt.get();
					final IGrade gradeFromJson = gradesByEmail.get(address);
					if (!lastGrade.equals(gradeFromJson)) {
						LOGGER.info("Diff {}: {} (before {}, after {}).", address, getDiff(lastGrade, gradeFromJson),
								lastGrade, gradeFromJson);
					}
				} else {
					LOGGER.info("Not found {}.", address);
				}
			}

			final Map<EmailAddressAndPersonal, IGrade> gradesDiffering = gradesByEmail.entrySet().stream()
					.filter(e -> !e.getValue().equals(lastGrades.get(e.getKey().getAddress())))
					.collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));

			final ImmutableSet<Email> emails = gradesDiffering.entrySet().stream()
					.map(e -> GradesInEmails.asEmail(e.getKey(), PREFIX, e.getValue(), stats, quartiles))
					.collect(ImmutableSet.toImmutableSet());

			final ImmutableSet<Email> effectiveEmails = emails;
			// final ImmutableSet<Email> effectiveEmails =
			// emails.stream().limit(1).collect(ImmutableSet.toImmutableSet());
			LOGGER.info("Prepared first doc {}.", XmlUtils.asString(effectiveEmails.iterator().next().getDocument()));
//			LOGGER.info("Prepared {}.", effectiveEmails);

			emailer.saveInto(folder);
			emailer.send(effectiveEmails, EmailerDauphineHelper.FROM);
		}
	}

	private static String getDiff(IGrade grade1, IGrade grade2) {
		if (grade1.equals(grade2)) {
			return "";
		}
		if (ImmutableSet.of(grade1.getClass(), grade2.getClass())
				.equals(ImmutableSet.of(Mark.class, WeightingGrade.class))) {
			return "Different types.";
		}

		String diff = "";
		if (!grade1.getComment().equals(grade2.getComment())) {
			diff += "First: '" + grade1.getComment() + "'; Second: '" + grade2.getComment() + "'. ";
		}
		if (grade1 instanceof Mark) {
			if (grade1.getPoints() != grade2.getPoints()) {
				diff += "First: " + grade1.getPoints() + "; Second: " + grade2.getPoints() + ". ";
			}
		} else if (grade1 instanceof WeightingGrade) {
			final ImmutableMap<Criterion, IGrade> subGrades1 = grade1.getSubGrades();
			final ImmutableMap<Criterion, IGrade> subGrades2 = grade2.getSubGrades();
			final ImmutableSet<Criterion> crits1 = subGrades1.keySet();
			final ImmutableSet<Criterion> crits2 = subGrades2.keySet();
			final ImmutableSet<Criterion> both = Sets.intersection(crits1, crits2).immutableCopy();
			final ImmutableSet<Criterion> onlyFirst = Sets.difference(crits1, crits2).immutableCopy();
			final ImmutableSet<Criterion> onlySecond = Sets.difference(crits2, crits1).immutableCopy();
			final Set<Criterion> allLeft = Sets.union(Sets.union(both, onlyFirst), onlySecond).immutableCopy();
			final Set<Criterion> allRight = Sets.union(crits1, crits2).immutableCopy();
			verify(allLeft.equals(allRight), Sets.symmetricDifference(allLeft, allRight).toString());
			for (Criterion criterion : both) {
				final IGrade subGrade1 = subGrades1.get(criterion);
				final IGrade subGrade2 = subGrades2.get(criterion);
				final String subDiff = getDiff(subGrade1, subGrade2);
				if (!subDiff.isEmpty()) {
					diff += criterion + ": [" + subDiff + "] ";
				}
			}
			if (!onlyFirst.isEmpty()) {
				diff += "Not in Second: " + onlyFirst;
			}
			if (!onlySecond.isEmpty()) {
				diff += "Not in First: " + onlySecond + " ";
			}
		} else {
			throw new IllegalArgumentException("Unsupported type.");
		}

		Verify.verify(!diff.isEmpty(), String.format("Grade1: %s, Grade2: %s.", grade1, grade2));
		return diff;
	}

}
