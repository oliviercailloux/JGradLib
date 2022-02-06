package io.github.oliviercailloux.grade.format;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Verify;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Iterables;
import com.google.common.collect.Range;
import com.google.common.collect.Streams;
import com.univocity.parsers.csv.CsvWriter;
import com.univocity.parsers.csv.CsvWriterSettings;
import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.CriterionGradeWeight;
import io.github.oliviercailloux.grade.Grade;
import io.github.oliviercailloux.grade.GradeAggregator;
import io.github.oliviercailloux.grade.IGrade;
import io.github.oliviercailloux.grade.IGrade.CriteriaPath;
import io.github.oliviercailloux.grade.MarksTree;
import io.github.oliviercailloux.grade.StructuredGrade;
import io.github.oliviercailloux.grade.WeightingGrade;
import io.github.oliviercailloux.grade.comm.StudentOnGitHub;
import io.github.oliviercailloux.grade.comm.StudentOnGitHubKnown;
import io.github.oliviercailloux.grade.old.GradeStructure;
import java.io.StringWriter;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CsvGrades<K> {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(CsvGrades.class);

	public static final double DEFAULT_DENOMINATOR = 20d;

	public static final Function<StudentOnGitHub, ImmutableMap<String, String>> STUDENT_IDENTITY_FUNCTION = s -> ImmutableMap
			.of("Name", s.hasInstitutionalPart() ? s.toInstitutionalStudent().getLastName() : "unknown",
					"GitHub username", s.getGitHubUsername().getUsername());

	public static final Function<StudentOnGitHubKnown, ImmutableMap<String, String>> STUDENT_KNOWN_IDENTITY_FUNCTION = s -> ImmutableMap
			.of("Name", s.getLastName(), "GitHub username", s.getGitHubUsername().getUsername());

	public static <K> CsvGrades<K> newInstance(Function<K, ? extends Map<String, String>> identityFunction,
			double denominator) {
		return new CsvGrades<>(identityFunction, denominator);
	}

	private static double getGlobalWeight(GradeStructure structure, CriteriaPath path) {
		double globalWeight = 1d;
		GradeStructure sub = structure;
		for (Criterion criterion : path) {
			final double w = sub.isAbsolute(criterion) ? 1d : sub.getFixedWeights().get(criterion);
			globalWeight *= w;
			sub = sub.getStructure(criterion);
		}
		return globalWeight;
	}

	private static double getPoints(StructuredGrade structured, CriteriaPath p) {
		final double globalWeight = getGlobalWeight(structured.getStructure(), p);
		StructuredGrade sub = structured;
		for (Criterion criterion : p) {
			sub = sub.getStructuredGrade(criterion);
		}
		return globalWeight * sub.getRootMark().getPoints();
	}

	private static Stream<CriteriaPath> getSuccessors(MarksTree grade, CriteriaPath prefix) {
		return grade.getCriteria().stream().map(prefix::withSuffix)
				.flatMap(p -> getSuccessors(grade.getTree(p.getTail()), p));
	}

	private static String shorten(CriteriaPath gradePath) {
		if (gradePath.isRoot()) {
			return "POINTS";
		}
		return gradePath.toSimpleString();
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
						Criterion.given(parent.getCriterion().getName() + "/" + cwg.getCriterion().getName()),
						cwg.getGrade(), parent.getWeight() * cwg.getWeight()));
		final Stream<CriterionGradeWeight> flatmappedChildren = mapped.flatMap((cwg) -> asContextualizedStream(cwg));
		return Stream.concat(itself, flatmappedChildren);
	}

	private static double getPointsScaled(CriterionGradeWeight cgw, double denominator) {
		final double points = cgw.getGrade().getPoints();
		final double pointsSigned = cgw.getWeight() >= 0d ? points : 1d - points;
		final double pointsScaled = pointsSigned * cgw.getWeight() * denominator;
		return pointsScaled;
	}

	private static String boundsToString(NumberFormat formatter, Range<Double> bounds) {
		final double lower = bounds.lowerEndpoint();
		final double upper = bounds.upperEndpoint();
		final String formattedLower = formatter.format(bounds.lowerEndpoint());
		final String formattedUpper = formatter.format(bounds.upperEndpoint());
		return (lower == upper) ? "{" + formattedLower + "}" : "[" + formattedLower + ", " + formattedUpper + "]";
	}

	private Function<K, ? extends Map<String, String>> identityFunction;
	private double denominator;

	private CsvGrades(Function<K, ? extends Map<String, String>> identityFunction, double denominator) {
		this.identityFunction = checkNotNull(identityFunction);
		checkArgument(Double.isFinite(denominator));
		this.denominator = denominator;
	}

	public Function<K, ? extends Map<String, String>> getIdentityFunction() {
		return identityFunction;
	}

	public CsvGrades<K> setIdentityFunction(Function<K, ? extends Map<String, String>> identityFunction) {
		this.identityFunction = checkNotNull(identityFunction);
		return this;
	}

	public double getDenominator() {
		return denominator;
	}

	public CsvGrades<K> setDenominator(double denominator) {
		this.denominator = denominator;
		return this;
	}

	public String toCsv(Map<K, ? extends IGrade> grades) {
		final Set<K> keys = grades.keySet();
		checkArgument(!keys.isEmpty(), "Can’t determine identity headers with no keys.");

		final NumberFormat formatter = NumberFormat.getNumberInstance(Locale.ENGLISH);
		final StringWriter stringWriter = new StringWriter();
		final CsvWriter writer = new CsvWriter(stringWriter, new CsvWriterSettings());

		final ImmutableMap<K, WeightingGrade> weightingGrades = grades.entrySet().stream()
				.filter(e -> e.getValue() instanceof WeightingGrade)
				.collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, e -> (WeightingGrade) e.getValue()));
		final ImmutableSetMultimap<K, CriterionGradeWeight> perKey = weightingGrades.entrySet().stream()
				.collect(ImmutableSetMultimap.flatteningToImmutableSetMultimap(Entry::getKey,
						(e) -> e.getValue().getSubGradesAsSet().stream().flatMap(CsvGrades::asContextualizedStream)));
		final ImmutableTable<K, Criterion, CriterionGradeWeight> asTable = perKey.entries().stream().collect(
				ImmutableTable.toImmutableTable(Entry::getKey, (e) -> e.getValue().getCriterion(), Entry::getValue));
		LOGGER.debug("From {}, obtained {}, as table {}.", grades, perKey, asTable);

		final ImmutableSet<Criterion> allCriteria = asTable.columnKeySet();

		final ImmutableSet<String> identityHeadersFromFunction = keys.stream()
				.flatMap(k -> identityFunction.apply(k).keySet().stream()).distinct()
				.collect(ImmutableSet.toImmutableSet());
		final ImmutableSet<String> identityHeaders = identityHeadersFromFunction.isEmpty() ? ImmutableSet.of("")
				: identityHeadersFromFunction;
		final ImmutableList<String> headers = Streams
				.concat(identityHeaders.stream(), allCriteria.stream().map(Criterion::getName), Stream.of("Points"))
				.collect(ImmutableList.toImmutableList());
		writer.writeHeaders(headers);

		final String firstHeader = identityHeaders.iterator().next();

		for (K key : keys) {
			final Map<String, String> identity = identityFunction.apply(key);
			identity.entrySet().forEach(e -> writer.addValue(e.getKey(), e.getValue()));

			final IGrade grade = grades.get(key);
			final ImmutableCollection<CriterionGradeWeight> marks = asTable.row(key).values();
			LOGGER.debug("Writing {} and {}.", key, marks);
			for (CriterionGradeWeight cgw : marks) {
				final Criterion criterion = cgw.getCriterion();
				Verify.verify(allCriteria.contains(criterion));
				final double pointsScaled = getPointsScaled(cgw, denominator);
				writer.addValue(criterion.getName(), formatter.format(pointsScaled));
			}

			writer.addValue("Points", formatter.format(grade.getPoints() * denominator));
			writer.writeValuesToRow();
		}
		writer.writeEmptyRow();

		final ImmutableMap<Criterion, ImmutableSet<Double>> weightsPerCriterion = asTable.columnKeySet().stream()
				.collect(ImmutableMap.toImmutableMap(c -> c, c -> asTable.column(c).values().stream()
						.map(CriterionGradeWeight::getWeight).collect(ImmutableSet.toImmutableSet())));
		final boolean homogeneousWeights = weightsPerCriterion.values().stream().allMatch(s -> s.size() == 1);

		LOGGER.debug("Writing summary data.");
		{
			writer.addValue(firstHeader, "Range");
			if (homogeneousWeights) {
				for (Criterion criterion : allCriteria) {
					final double weight = Iterables.getOnlyElement(weightsPerCriterion.get(criterion));
					final Range<Double> bounds = Range.closed(0d, weight * denominator);
					writer.addValue(criterion.getName(), boundsToString(formatter, bounds));
				}
			}
			final Range<Double> overallBounds = Range.closed(0d, denominator);
			writer.addValue("Points", boundsToString(formatter, overallBounds));
			writer.writeValuesToRow();
		}

		{
			writer.addValue(firstHeader, "Upper bound");
			if (homogeneousWeights) {
				for (Criterion criterion : allCriteria) {
					final double weight = Iterables.getOnlyElement(weightsPerCriterion.get(criterion));
					writer.addValue(criterion.getName(), formatter.format(weight * denominator));
				}
			}
			writer.addValue("Points", formatter.format(denominator));
			writer.writeValuesToRow();
		}

		{
			writer.addValue(firstHeader, "Average");
			for (Criterion criterion : allCriteria) {
				final double average;
				if (homogeneousWeights) {
					average = asTable.column(criterion).values().stream()
							.collect(Collectors.averagingDouble(c -> getPointsScaled(c, denominator)));
				} else {
					average = asTable.column(criterion).values().stream()
							.collect(Collectors.averagingDouble(c -> c.getGrade().getPoints()));
				}
				writer.addValue(criterion.getName(), formatter.format(average));
			}
			final double averageOfTotalScore = weightingGrades.values().stream()
					.collect(Collectors.averagingDouble(g -> g.getPoints() * denominator));
			writer.addValue("Points", formatter.format(averageOfTotalScore));
			writer.writeValuesToRow();
		}

		{
			writer.addValue(firstHeader, "Nb > 0");
			for (Criterion criterion : allCriteria) {
				final int nb = Math.toIntExact(
						asTable.column(criterion).values().stream().filter(c -> c.getGrade().getPoints() > 0d).count());
				writer.addValue(criterion.getName(), formatter.format(nb));
			}
			final int nb = Math.toIntExact(weightingGrades.values().stream().filter(g -> g.getPoints() > 0d).count());
			writer.addValue("Points", formatter.format(nb));
			writer.writeValuesToRow();
		}

		{
			writer.addValue(firstHeader, "Nb MAX");
			for (Criterion criterion : allCriteria) {
				final int nb = Math.toIntExact(asTable.column(criterion).values().stream()
						.filter(c -> c.getGrade().getPoints() == 1d).count());
				writer.addValue(criterion.getName(), formatter.format(nb));
			}
			final int nb = Math.toIntExact(weightingGrades.values().stream().filter(g -> g.getPoints() == 1d).count());
			writer.addValue("Points", formatter.format(nb));
			writer.writeValuesToRow();
		}

		LOGGER.debug("Done writing.");
		writer.close();

		return stringWriter.toString();
	}

	public String gradesToCsv(GradeAggregator aggregator, Map<K, ? extends MarksTree> trees) {
		final Set<K> keys = trees.keySet();
		checkArgument(!keys.isEmpty(), "Can’t determine identity headers with no keys.");

		final NumberFormat formatter = NumberFormat.getNumberInstance(Locale.ENGLISH);
		final StringWriter stringWriter = new StringWriter();
		final CsvWriter writer = new CsvWriter(stringWriter, new CsvWriterSettings());

		final ImmutableSet<String> identityHeadersFromFunction = keys.stream()
				.flatMap(k -> identityFunction.apply(k).keySet().stream()).collect(ImmutableSet.toImmutableSet());
		final ImmutableSet<String> identityHeaders = identityHeadersFromFunction.isEmpty() ? ImmutableSet.of("")
				: identityHeadersFromFunction;
		final ImmutableSet<CriteriaPath> allPaths = trees.values().stream()
				.flatMap(g -> getSuccessors(g, CriteriaPath.ROOT)).collect(ImmutableSet.toImmutableSet());

		final ImmutableList<String> headers = Streams
				.concat(identityHeaders.stream(), allPaths.stream().map(CsvGrades::shorten))
				.collect(ImmutableList.toImmutableList());
		writer.writeHeaders(headers);

		final String firstHeader = headers.iterator().next();

		for (K key : keys) {
			final Map<String, String> identity = identityFunction.apply(key);
			identity.entrySet().forEach(e -> writer.addValue(e.getKey(), e.getValue()));

			final MarksTree tree = trees.get(key);
			final Grade grade = Grade.given(aggregator, tree);
			allPaths.stream().forEach(p -> writer.addValue(CsvGrades.shorten(p), formatter
					.format(grade.getWeight(p) * grade.getGrade(p).getMark().getPoints() * denominator)));
			writer.writeValuesToRow();
		}
		writer.writeEmptyRow();

		LOGGER.debug("Writing summary data.");
		{
			writer.addValue(firstHeader, "Upper bound");
			allPaths.stream().forEach(
					p -> writer.addValue(CsvGrades.shorten(p), formatter.format(getGlobalWeight(structure, p))));
			writer.writeValuesToRow();
		}

		{
			writer.addValue(firstHeader, "Average");
		}

		{
			writer.addValue(firstHeader, "Nb > 0");
		}

		{
			writer.addValue(firstHeader, "Nb MAX");
		}

		LOGGER.debug("Done writing.");
		writer.close();

		return stringWriter.toString();
	}
}
