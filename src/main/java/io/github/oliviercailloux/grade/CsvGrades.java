package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.InputStream;
import java.io.StringWriter;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.collect.Streams;
import com.univocity.parsers.common.record.Record;
import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;
import com.univocity.parsers.csv.CsvWriter;
import com.univocity.parsers.csv.CsvWriterSettings;

import io.github.oliviercailloux.grade.mycourse.StudentOnGitHub;

public class CsvGrades {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(CsvGrades.class);

	public static String asCsv(Collection<GradeWithStudentAndCriterion> grades) {
		final NumberFormat formatter = NumberFormat.getNumberInstance(Locale.FRENCH);
		final StringWriter stringWriter = new StringWriter();
		final CsvWriter writer = new CsvWriter(stringWriter, new CsvWriterSettings());

		final ImmutableSet<CriterionAndPoints> allKeys = grades.stream().flatMap((g) -> g.getMarks().keySet().stream())
				.collect(ImmutableSet.toImmutableSet());
		writer.writeHeaders(Streams.concat(Stream.of("Name", "GitHub username"), allKeys.stream().map(Object::toString),
				Stream.of("Grade")).collect(Collectors.toList()));
		for (GradeWithStudentAndCriterion grade : grades) {
			final StudentOnGitHub student = grade.getStudent();
			LOGGER.info("Writing {}.", student);
			writer.addValue("Name", student.getLastName().orElse("unknown"));
			writer.addValue("GitHub username", student.getGitHubUsername());

			for (CriterionAndPoints criterion : grade.getMarks().keySet()) {
				final double mark = grade.getMarks().get(criterion).getPoints();
				writer.addValue(criterion.toString(), formatter.format(mark));
			}

			writer.addValue("Grade", formatter.format(grade.getPoints()));
			writer.writeValuesToRow();
		}

		writer.addValue("Name", "Range");
		writer.addValue("GitHub username", "Range");
		for (CriterionAndPoints criterion : allKeys) {
			writer.addValue(criterion.toString(),
					"[" + criterion.getMinPoints() + ", " + criterion.getMaxPoints() + "]");
		}
		final double minGrade = allKeys.stream().collect(Collectors.summingDouble(CriterionAndPoints::getMinPoints));
		final double maxGrade = allKeys.stream().collect(Collectors.summingDouble(CriterionAndPoints::getMaxPoints));
		writer.addValue("Grade", "[" + minGrade + "," + maxGrade + "]");
		writer.writeValuesToRow();

		writer.close();

		return stringWriter.toString();
	}

	public static ImmutableSet<GradeWithStudentAndCriterion> fromCsv(InputStream input, Function<String, CriterionAndPoints> toCriterion) {
		final NumberFormat formatter = NumberFormat.getNumberInstance(Locale.FRENCH);
		final CsvParserSettings settings = new CsvParserSettings();
		settings.setHeaderExtractionEnabled(true);
		final CsvParser parser = new CsvParser(settings);
		parser.beginParsing(input);
		Record record = parser.parseNextRecord();
		final ImmutableSet<String> allHeaders = ImmutableSet.copyOf(parser.getRecordMetadata().headers());
		final ImmutableSet<String> expected = ImmutableSet.of("Name", "GitHub username", "Grade");
		checkArgument(allHeaders.containsAll(expected));
		final ImmutableSet<String> criteria = Sets.difference(allHeaders, expected).immutableCopy();
		final ImmutableSet.Builder<GradeWithStudentAndCriterion> gradesBuilder = ImmutableSet.builder();
		while (record != null) {
			final String name = record.getString("Name");
			final String username = record.getString("GitHub username");
			final Set<CriterionAndMark> marks = new LinkedHashSet<>();
			for (String criterionName : criteria) {
				final String pointsStr = record.getString(criterionName);
				if (Strings.isNullOrEmpty(pointsStr)) {
					continue;
				}
				final double points;
				try {
					points = formatter.parse(pointsStr).doubleValue();
				} catch (ParseException e) {
					throw new IllegalStateException(e);
				}
				final CriterionAndPoints criterion = toCriterion.apply(criterionName);
				final CriterionAndMark mark = CriterionAndMark.of(criterion, points, "");
				marks.add(mark);
			}
			if (!marks.isEmpty()) {
				final GradeWithStudentAndCriterion grade = GradeWithStudentAndCriterion.of(StudentOnGitHub.with(username), marks);
				LOGGER.debug("Grade built for {}: {}.", name, grade);
				gradesBuilder.add(grade);
			}
			record = parser.parseNextRecord();
		}
		return gradesBuilder.build();
	}
}
