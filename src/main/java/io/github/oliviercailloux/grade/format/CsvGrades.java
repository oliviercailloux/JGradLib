package io.github.oliviercailloux.grade.format;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.StringWriter;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Verify;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.MoreCollectors;
import com.google.common.collect.Streams;
import com.univocity.parsers.csv.CsvWriter;
import com.univocity.parsers.csv.CsvWriterSettings;

import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.CriterionGradeWeight;
import io.github.oliviercailloux.grade.IGrade;
import io.github.oliviercailloux.grade.WeightingGrade;
import io.github.oliviercailloux.grade.comm.StudentOnGitHub;

public class CsvGrades {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(CsvGrades.class);

	public static final double DEFAULT_DENOMINATOR = 20d;

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
}
