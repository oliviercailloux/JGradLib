package io.github.oliviercailloux.java_grade.utils;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Verify.verify;

import com.google.common.base.Verify;
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
import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.Exam;
import io.github.oliviercailloux.grade.Grade;
import io.github.oliviercailloux.grade.IGrade;
import io.github.oliviercailloux.grade.IGrade.CriteriaPath;
import io.github.oliviercailloux.grade.Mark;
import io.github.oliviercailloux.grade.MarksTree;
import io.github.oliviercailloux.grade.WeightingGrade;
import io.github.oliviercailloux.grade.WeightingGrade.WeightedGrade;
import io.github.oliviercailloux.grade.comm.Email;
import io.github.oliviercailloux.grade.comm.Emailer;
import io.github.oliviercailloux.grade.comm.EmailerDauphineHelper;
import io.github.oliviercailloux.grade.comm.GradesInEmails;
import io.github.oliviercailloux.grade.comm.StudentOnGitHubKnown;
import io.github.oliviercailloux.grade.comm.json.JsonStudentsReader;
import io.github.oliviercailloux.grade.format.json.JsonSimpleGrade;
import io.github.oliviercailloux.xml.XmlUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import javax.mail.Folder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SendEmails {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(SendEmails.class);

	private static final Path WORK_DIR = Path.of("");

	public static void main(String[] args) throws Exception {
//		final String prefix = PersonsManagerGrader.PREFIX;
		final String prefix = "UML";

		final JsonStudentsReader students = JsonStudentsReader
				.from(Files.readString(WORK_DIR.resolve("usernames.json")));
		final ImmutableMap<GitHubUsername, StudentOnGitHubKnown> usernames = students
				.getStudentsKnownByGitHubUsername();

		final Exam exam = JsonSimpleGrade.asExam(Files.readString(WORK_DIR.resolve("grades " + prefix + ".json")));
		final boolean allKnown = students.getInstitutionalStudentsByGitHubUsername().keySet()
				.containsAll(exam.getUsernames());
		checkState(allKnown);

		final ImmutableSet<GitHubUsername> missing = Sets
				.difference(exam.getUsernames(), students.getInstitutionalStudentsByGitHubUsername().keySet())
				.immutableCopy();
		if (!missing.isEmpty()) {
			LOGGER.warn("Missing: {}.", missing);
		}

		final Mark defaultMark = Mark.given(0d, "GitHub repository not found");
		final ImmutableMap<EmailAddressAndPersonal, MarksTree> marksByEmail = exam.getUsernames().stream()
				.collect(ImmutableMap.toImmutableMap(u -> usernames.get(u).getEmail(),
						u -> exam.getUsernames().contains(u) ? exam.getGrade(u).toMarksTree() : defaultMark));
		final ImmutableMap<EmailAddressAndPersonal, Grade> gradesByEmail = ImmutableMap
				.copyOf(Maps.transformValues(marksByEmail, m -> Grade.given(exam.aggregator(), m)));

		final ImmutableMap<String, IGrade> knownGrades = gradesByString.keySet().stream()
				.filter(s -> usernames.containsKey(GitHubUsername.given(s)))
				.collect(ImmutableMap.toImmutableMap(s -> s, gradesByString::get));

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
			final ImmutableMap<EmailAddress, IGrade> lastGrades = gradesInEmails.getLastGrades(prefix);
			LOGGER.debug("Searching grades sent to {}, got those sent to {}.", addresses, lastGrades.keySet());

			for (EmailAddressAndPersonal address : gradesByEmail.keySet()) {
				if (!lastGrades.containsKey(address.getAddress())) {
					LOGGER.info("Not found {} among {}.", address, lastGrades.keySet());
				}
			}

			final Map<EmailAddressAndPersonal, IGrade> gradesDiffering = gradesByEmail.entrySet().stream()
					.filter(e -> isDiff(Optional.ofNullable(lastGrades.get(e.getKey().getAddress())), e.getValue()))
					// .filter(e -> !e.getKey().getPersonal().contains("…"))
					.collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));

			for (EmailAddressAndPersonal address : gradesDiffering.keySet()) {
				final IGrade lastGrade = lastGrades.get(address.getAddress());
				if (lastGrade == null) {
					continue;
				}
				final IGrade gradeFromJson = gradesByEmail.get(address);
				// LOGGER.info("Diff {}: {} (before {}, after {}).", address, getDiff(lastGrade,
				// gradeFromJson),
				// lastGrade, gradeFromJson);
				final double before = lastGrade.getPoints();
				final double after = gradeFromJson.getPoints();
				LOGGER.info("Diff {} (before {}, after {}).", address, before, after);
				if (before > after) {
					LOGGER.warn("Losing points.");
				}
				computeDiffUsingLeaves(lastGrade, gradeFromJson);
			}

			final ImmutableSet<Email> emails = gradesDiffering.entrySet().stream()
					.map(e -> GradesInEmails.asEmail(e.getKey(), prefix, e.getValue(), stats, quartiles))
					.collect(ImmutableSet.toImmutableSet());

			final ImmutableSet<Email> effectiveEmails = emails;
			// final ImmutableSet<Email> effectiveEmails =
			// emails.stream().limit(3).collect(ImmutableSet.toImmutableSet());
			LOGGER.info("Prepared first doc (out of {}): {}.", effectiveEmails.size(),
					XmlUtils.asString(effectiveEmails.iterator().next().getDocument()));
			// LOGGER.info("Prepared {}.", effectiveEmails);

			emailer.saveInto(folder);
//			emailer.send(effectiveEmails, EmailerDauphineHelper.FROM);
		}
	}

	private static boolean isDiff(Optional<IGrade> lastGrade, IGrade current) {
		return !current.equals(lastGrade.orElse(null));
//		return !DoubleMath.fuzzyEquals(lastGrade.map(IGrade::getPoints).orElse(-1d), current.getPoints(), 1e-8d);
	}

	static void computeDiffUsingLeaves(IGrade grade1, IGrade grade2) {
		/*
		 * Considers the leaves that are different or do not exist in one. Builds a
		 * grade with that.
		 */
		final ImmutableSet<CriteriaPath> allLeaves = Stream.of(grade1, grade2)
				.flatMap(g -> g.toTree().getLeaves().stream()).collect(ImmutableSet.toImmutableSet());
		for (CriteriaPath leaf : allLeaves) {
			final boolean contains1 = grade1.toTree().getPaths().contains(leaf);
			final boolean contains2 = grade2.toTree().getPaths().contains(leaf);
			verify(contains1 || contains2, leaf.toSimpleString());
			final Optional<WeightedGrade> sub1 = contains1 ? Optional.of(grade1.getWeightedGrade(leaf))
					: Optional.empty();
			final Optional<WeightedGrade> sub2 = contains2 ? Optional.of(grade2.getWeightedGrade(leaf))
					: Optional.empty();
			if (!sub1.equals(sub2)) {
				final String sub1String = sub1.map(Object::toString).orElse("");
				final String sub2String = sub2.map(Object::toString).orElse("");
				LOGGER.debug("Path: {}, first: {}, second: {}.", leaf.toSimpleString(), sub1String, sub2String);
			}
		}
	}

	static String getDiff(IGrade grade1, IGrade grade2) {
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
