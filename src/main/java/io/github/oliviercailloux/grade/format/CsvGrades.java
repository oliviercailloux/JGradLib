package io.github.oliviercailloux.grade.format;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.InputStream;
import java.io.StringWriter;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;
import com.google.common.base.Verify;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.MoreCollectors;
import com.google.common.collect.Sets;
import com.google.common.collect.Streams;
import com.univocity.parsers.common.record.Record;
import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;
import com.univocity.parsers.csv.CsvWriter;
import com.univocity.parsers.csv.CsvWriterSettings;

import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.CriterionAndMark;
import io.github.oliviercailloux.grade.CriterionAndPoints;
import io.github.oliviercailloux.grade.CriterionGradeWeight;
import io.github.oliviercailloux.grade.GradeWithStudentAndCriterion;
import io.github.oliviercailloux.grade.IGrade;
import io.github.oliviercailloux.grade.WeightingGrade;
import io.github.oliviercailloux.grade.comm.StudentOnGitHub;

public class CsvGrades {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(CsvGrades.class);

	private static final double DEFAULT_DENOMINATOR = 20d;

	public static String asCsv(Map<StudentOnGitHub, WeightingGrade> grades) {
		return asCsv(grades, DEFAULT_DENOMINATOR);
	}

	public static String asCsv(Map<StudentOnGitHub, WeightingGrade> grades, double denominator) {
		final NumberFormat formatter = NumberFormat.getNumberInstance(Locale.FRENCH);
		final StringWriter stringWriter = new StringWriter();
		final CsvWriter writer = new CsvWriter(stringWriter, new CsvWriterSettings());

		final ImmutableSetMultimap<StudentOnGitHub, CriterionGradeWeight> perStudent = grades.entrySet().stream()
				.collect(ImmutableSetMultimap.flatteningToImmutableSetMultimap(Entry::getKey,
						(e) -> e.getValue().getSubGradesAsSet().stream().flatMap(CsvGrades::asContextualizedStream)));
		final ImmutableTable<StudentOnGitHub, Criterion, CriterionGradeWeight> asTable = perStudent.entries().stream()
				.collect(ImmutableTable.toImmutableTable(Entry::getKey, (e) -> e.getValue().getCriterion(),
						Entry::getValue));

		final ImmutableSet<Criterion> allCriteria = asTable.columnKeySet();

		final ImmutableList<String> headers = Streams.concat(Stream.of("Name", "GitHub username"),
				allCriteria.stream().map(Object::toString), Stream.of("Points"))
				.collect(ImmutableList.toImmutableList());
		writer.writeHeaders(headers);

		for (Entry<StudentOnGitHub, WeightingGrade> studentGrade : grades.entrySet()) {
			final StudentOnGitHub student = studentGrade.getKey();
			LOGGER.info("Writing {}.", student);
			writer.addValue("Name", student.getLastName().orElse("unknown"));
			writer.addValue("GitHub username", student.getGitHubUsername());

			final WeightingGrade grade = studentGrade.getValue();
			final ImmutableCollection<CriterionGradeWeight> marks = asTable.row(student).values();
			for (CriterionGradeWeight cgw : marks) {
				final Criterion criterion = cgw.getCriterion();
				Verify.verify(allCriteria.contains(criterion));
				final IGrade mark = cgw.getGrade();
				final double points = mark.getPoints();
				final double pointsSigned = cgw.getWeight() > 0d ? points : 1d - points;
				final double pointsScaled = pointsSigned * cgw.getWeight() * denominator;
				writer.addValue(criterion.getName(), formatter.format(pointsScaled));
			}

			writer.addValue("Points", formatter.format(grade.getPoints() * denominator));
			writer.writeValuesToRow();
		}

		writer.addValue("Name", "Range");
		writer.addValue("GitHub username", "Range");

		for (Criterion criterion : allCriteria) {
			final double weight = asTable.column(criterion).values().stream().map(CriterionGradeWeight::getWeight)
					.distinct().collect(MoreCollectors.onlyElement());
			writer.addValue(criterion.getName(), "[0, " + formatter.format(weight * denominator) + "]");
		}
		writer.addValue("Points", "[0," + formatter.format(denominator) + "]");
		writer.writeValuesToRow();

		writer.close();

		return stringWriter.toString();
	}

	private static Stream<Map.Entry<Criterion, IGrade>> childrenAsStream(Entry<Criterion, IGrade> parent) {
		final Stream<Entry<Criterion, IGrade>> itself = Stream.of(parent);
		final IGrade grade = parent.getValue();
		final Stream<Entry<Criterion, IGrade>> allSubGrades = grade.getSubGrades().entrySet().stream()
				.flatMap(CsvGrades::childrenAsStream);
		return Stream.concat(itself, allSubGrades);
	}

	@SuppressWarnings("unused")
	private static Stream<Map.Entry<Criterion, IGrade>> childrenAsStream(IGrade parent) {
		if (parent.getSubGrades().isEmpty()) {
			return Stream.of();
		}
		return parent.getSubGrades().entrySet().stream().flatMap(CsvGrades::childrenAsStream);
	}

	private static Stream<CriterionGradeWeight> asContextualizedStream(CriterionGradeWeight parent) {
		final Stream<CriterionGradeWeight> itself = Stream.of(parent);
		final ImmutableSet<CriterionGradeWeight> subGrades;
		if (parent.getGrade() instanceof WeightingGrade) {
			WeightingGrade weightingParent = (WeightingGrade) parent.getGrade();
			subGrades = weightingParent.getSubGradesAsSet();
		} else {
			checkArgument(parent.getGrade().getSubGrades().isEmpty());
			subGrades = ImmutableSet.of();
		}
		final Stream<CriterionGradeWeight> mapped = subGrades.stream()
				.map((cwg) -> CriterionGradeWeight.from(
						Criterion.given(parent.getCriterion().getName() + "/" + cwg.getCriterion()), cwg.getGrade(),
						parent.getWeight() * cwg.getWeight()));
		final Stream<CriterionGradeWeight> flatmappedChildren = mapped.flatMap((cwg) -> asContextualizedStream(cwg));
		return Stream.concat(itself, flatmappedChildren);
	}

	public static ImmutableSet<GradeWithStudentAndCriterion> fromCsv(InputStream input,
			Function<String, CriterionAndPoints> toCriterion) {
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
				final GradeWithStudentAndCriterion grade = GradeWithStudentAndCriterion
						.of(StudentOnGitHub.with(username), marks);
				LOGGER.debug("Grade built for {}: {}.", name, grade);
				gradesBuilder.add(grade);
			}
			record = parser.parseNextRecord();
		}
		return gradesBuilder.build();
	}

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
}
