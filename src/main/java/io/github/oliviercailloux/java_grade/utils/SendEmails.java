package io.github.oliviercailloux.java_grade.utils;

import static com.google.common.base.Preconditions.checkArgument;

import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.mail.Folder;
import javax.mail.Message;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Verify;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.math.Quantiles;
import com.google.common.math.Stats;

import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinates;
import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.IGrade;
import io.github.oliviercailloux.grade.Mark;
import io.github.oliviercailloux.grade.WeightingGrade;
import io.github.oliviercailloux.grade.comm.Emailer;
import io.github.oliviercailloux.grade.comm.Email;
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

	private static final String PREFIX = "string-files-fake";
	private static final Path WORK_DIR = Path.of("../../Java L3/");

	public static void main(String[] args) throws Exception {

		@SuppressWarnings("all")
		final Type typeSet = new HashSet<StudentOnGitHubKnown>() {
		}.getClass().getGenericSuperclass();
		final Set<StudentOnGitHubKnown> usernamesAsSet = JsonbUtils.fromJson(
				Files.readString(WORK_DIR.resolve("usernames.json")), typeSet, JsonStudentOnGitHubKnown.asAdapter());

		final ImmutableMap<String, StudentOnGitHubKnown> usernames = usernamesAsSet.stream()
				.collect(ImmutableMap.toImmutableMap(s -> s.getGitHubUsername(), s -> s));

		@SuppressWarnings("all")
		final Type type = new HashMap<RepositoryCoordinates, IGrade>() {
		}.getClass().getGenericSuperclass();
		final Map<String, IGrade> grades = JsonbUtils.fromJson(
				Files.readString(WORK_DIR.resolve("all grades " + PREFIX + ".json")), type, JsonGrade.asAdapter());

		// final Map<String, IGrade> gradesFiltered =
		// grades.entrySet().stream().limit(1)
		// .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey,
		// Map.Entry::getValue));
		// final Map<String, IGrade> gradesFiltered =
		// grades.entrySet().stream().filter(e -> e.getKey().equals("…"))
		// .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey,
		// Map.Entry::getValue));
		final Map<String, IGrade> gradesFiltered = grades;

		final ImmutableList<Double> points = grades.values().stream().map(IGrade::getPoints)
				.collect(ImmutableList.toImmutableList());
		final Stats stats = Stats.of(points);
		final Map<Integer, Double> quartiles = Quantiles.quartiles().indexes(1, 2, 3).compute(points);

		try (GradesInEmails sendEmails = GradesInEmails.newInstance()) {
			@SuppressWarnings("resource")
			final Emailer emailer = sendEmails.getEmailer();
			EmailerDauphineHelper.connect(emailer);
			@SuppressWarnings("resource")
			final Folder folder = emailer.getFolderReadWrite("Grades");
			sendEmails.setFolder(folder);

			final Map<String, Optional<IGrade>> lastGrades = gradesFiltered.keySet().stream()
					.collect(ImmutableMap.toImmutableMap(l -> l,
							l -> sendEmails.getLastGradeTo(GradesInEmails.asAddress(usernames.get(l)), PREFIX)));

			for (String login : lastGrades.keySet()) {
				final Optional<IGrade> lastGrade = lastGrades.get(login);
				if (lastGrade.isPresent()) {
					LOGGER.info("Diff {}: {}.", login, getDiff(lastGrade.get(), gradesFiltered.get(login)));
				} else {
					LOGGER.info("Not found {}.", login);
				}
			}

			final Map<String, IGrade> gradesDiffering = gradesFiltered.entrySet().stream()
					.filter(e -> lastGrades.get(e.getKey()).filter(g -> g.equals(e.getValue())).isEmpty())
					.collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));

			final ImmutableSet<Email> emails = gradesDiffering.entrySet().stream()
					.map(e -> GradesInEmails.asEmail(GradesInEmails.asAddress(usernames.get(e.getKey())), PREFIX,
							e.getValue(), stats, quartiles))
					.collect(ImmutableSet.toImmutableSet());

			final ImmutableSet<Email> effectiveEmails = emails;
			// final ImmutableSet<Email> effectiveEmails =
			// emails.stream().limit(1).collect(ImmutableSet.toImmutableSet());
			LOGGER.info("Prepared first doc {}.", XmlUtils.asString(effectiveEmails.iterator().next().getDocument()));
			LOGGER.info("Prepared {}.", effectiveEmails);

			final ImmutableSet<Message> sent = emailer.send(effectiveEmails, EmailerDauphineHelper.FROM);
			LOGGER.info("Sent {} messages.", sent.size());

			emailer.saveInto(sent, folder);
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
			checkArgument(subGrades1.keySet().equals(subGrades2.keySet()));
			for (Criterion criterion : subGrades1.keySet()) {
				final IGrade subGrade1 = subGrades1.get(criterion);
				final IGrade subGrade2 = subGrades2.get(criterion);
				final String subDiff = getDiff(subGrade1, subGrade2);
				if (!subDiff.isEmpty()) {
					diff += criterion + ": [" + subDiff + "] ";
				}
			}
		} else {
			throw new IllegalArgumentException("Unsupported type.");
		}

		Verify.verify(!diff.isEmpty(), String.format("Grade1: %s, Grade2: %s.", grade1, grade2));
		return diff;
	}

}
