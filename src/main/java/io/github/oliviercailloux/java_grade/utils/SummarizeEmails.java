package io.github.oliviercailloux.java_grade.utils;

import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.function.Function;

import javax.mail.Folder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Maps;
import com.google.common.collect.Range;

import io.github.oliviercailloux.email.EmailAddress;
import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.CriterionGradeWeight;
import io.github.oliviercailloux.grade.IGrade;
import io.github.oliviercailloux.grade.WeightingGrade;
import io.github.oliviercailloux.grade.comm.Emailer;
import io.github.oliviercailloux.grade.comm.EmailerDauphineHelper;
import io.github.oliviercailloux.grade.comm.GradesInEmails;
import io.github.oliviercailloux.grade.comm.StudentOnGitHubKnown;
import io.github.oliviercailloux.grade.format.CsvGrades;

public class SummarizeEmails {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(SummarizeEmails.class);

	public static void main(String[] args) throws Exception {
		@SuppressWarnings("all")
		final Type typeSet = new HashSet<StudentOnGitHubKnown>() {
		}.getClass().getGenericSuperclass();

		try (GradesInEmails gradesInEmails = GradesInEmails.newInstance()) {
			@SuppressWarnings("resource")
			final Emailer emailer = gradesInEmails.getEmailer();
			EmailerDauphineHelper.connect(emailer);
			@SuppressWarnings("resource")
			final Folder folder = emailer.getFolderReadWrite("Grades");
			gradesInEmails.setFolder(folder);
			gradesInEmails.filterSent(Range.atLeast(Instant.parse("2020-01-01T00:00:00.00Z")));
			final ImmutableTable<EmailAddress, String, IGrade> lastGrades = gradesInEmails.getLastGrades();
			final ImmutableSet<EmailAddress> addressesFound = lastGrades.rowKeySet();
			final ImmutableSet<EmailAddress> addresses = addressesFound.stream()
					.filter(a -> !a.getAddress().startsWith("olivier.cailloux@"))
					.collect(ImmutableSet.toImmutableSet());
			final CsvGrades<EmailAddress> csvGrades = CsvGrades.<EmailAddress>newInstance();
			csvGrades.setIdentityFunction(e -> ImmutableMap.of("Address", e.getAddress()));
			Files.writeString(Path.of("../../Java L3/grades.csv"), csvGrades.toCsv(addresses,
					a -> from(Maps.filterKeys(lastGrades.row(a), s -> !s.endsWith("-fake")), Criterion::given, s -> 1d)
							.limitedDepthAsWeighting(1)));
		}
	}

	public static <K> WeightingGrade from(Map<K, IGrade> grades, Function<K, Criterion> criterionFunction,
			Function<K, Double> weightFunction) {
		final ImmutableSet<CriterionGradeWeight> gradesSet = grades.keySet().stream()
				.map(k -> CriterionGradeWeight.from(criterionFunction.apply(k), grades.get(k), weightFunction.apply(k)))
				.collect(ImmutableSet.toImmutableSet());
		return WeightingGrade.from(gradesSet);
	}

}
