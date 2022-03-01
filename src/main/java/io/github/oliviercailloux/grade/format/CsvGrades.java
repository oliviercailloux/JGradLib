package io.github.oliviercailloux.grade.format;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Verify.verify;

import com.google.common.base.Verify;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Range;
import com.google.common.collect.Streams;
import com.google.common.math.DoubleMath;
import com.google.common.math.Stats;
import com.univocity.parsers.csv.CsvWriter;
import com.univocity.parsers.csv.CsvWriterSettings;
import io.github.oliviercailloux.git.git_hub.model.GitHubUsername;
import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.CriterionGradeWeight;
import io.github.oliviercailloux.grade.Grade;
import io.github.oliviercailloux.grade.GradeAggregator;
import io.github.oliviercailloux.grade.IGrade;
import io.github.oliviercailloux.grade.IGrade.CriteriaPath;
import io.github.oliviercailloux.grade.Mark;
import io.github.oliviercailloux.grade.MarksTree;
import io.github.oliviercailloux.grade.WeightingGrade;
import io.github.oliviercailloux.grade.WeightingGradeAggregator;
import io.github.oliviercailloux.grade.comm.StudentOnGitHub;
import io.github.oliviercailloux.grade.comm.StudentOnGitHubKnown;
import java.io.StringWriter;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CsvGrades<K> {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(CsvGrades.class);

	public static final double DEFAULT_DENOMINATOR = 20d;

	public static final Function<String, ImmutableMap<String, String>> STUDENT_NAME_FUNCTION = s -> ImmutableMap
			.of("Name", s);

	public static final Function<GitHubUsername, ImmutableMap<String, String>> STUDENT_USERNAME_FUNCTION = s -> ImmutableMap
			.of("Username", s.getUsername());

	public static final Function<Object, ImmutableMap<String, String>> STUDENT_GENERIC_NAME_FUNCTION = s -> ImmutableMap
			.of("Name", s.toString());

	public static final Function<StudentOnGitHub, ImmutableMap<String, String>> STUDENT_IDENTITY_FUNCTION = s -> ImmutableMap
			.of("Name", s.hasInstitutionalPart() ? s.toInstitutionalStudent().getLastName() : "unknown",
					"GitHub username", s.getGitHubUsername().getUsername());

	public static final Function<StudentOnGitHubKnown, ImmutableMap<String, String>> STUDENT_KNOWN_IDENTITY_FUNCTION = s -> ImmutableMap
			.of("Name", s.getLastName(), "GitHub username", s.getGitHubUsername().getUsername());

	public static <K> CsvGrades<K> newInstance(Function<K, ? extends Map<String, String>> identityFunction,
			double denominator) {
		return new CsvGrades<>(identityFunction, denominator);
	}

	public static record GenericExam<K> (GradeAggregator aggregator, ImmutableMap<K, ? extends MarksTree> trees) {
		public GenericExam(GradeAggregator aggregator, Map<K, ? extends MarksTree> trees) {
			this(aggregator, ImmutableMap.copyOf(trees));
		}

		public ImmutableSet<K> getUsernames() {
			return trees.keySet();
		}

		public Grade getGrade(K username) {
			return Grade.given(aggregator, trees.get(username));
		}
	}

	public static record PerCriterionWeightingExam<K> (WeightingGradeAggregator aggregator,
			ImmutableMap<K, MarksTree> trees) {
		private static Stream<CriteriaPath> getSuccessors(MarksTree grade, CriteriaPath prefix) {
			return Streams.concat(Stream.of(prefix), grade.getCriteria().stream().map(prefix::withSuffix)
					.flatMap(p -> getSuccessors(grade.getTree(p.getTail()), p)));
		}

		public ImmutableSet<K> getUsernames() {
			return trees.keySet();
		}

		public Grade getGrade(K username) {
			return Grade.given(aggregator, trees.get(username));
		}

		public double weight(CriteriaPath path) {
			return aggregator.weight(path);
		}

		public DoubleStream points(CriteriaPath path) {
			return getUsernames().stream().map(this::getGrade).map(g -> g.getGrade(path)).map(Grade::mark)
					.mapToDouble(Mark::getPoints);
		}

		public double averagePoints(CriteriaPath path) {
			return Stats.of(points(path)).mean();
		}

		public ImmutableSet<CriteriaPath> allPaths() {
			return trees.values().stream().flatMap(g -> getSuccessors(g, CriteriaPath.ROOT))
					.collect(ImmutableSet.toImmutableSet());
		}
	}

	private static <K> PerCriterionWeightingExam<K> toPerCriterionWeightingExam(GenericExam<K> exam) {
		final PerCriterionWeightingExam<K> newExam = new PerCriterionWeightingExam<>(
				Grade.transformToPerCriterionWeighting(exam.aggregator()),
				Maps.toMap(exam.getUsernames(), u -> Grade.adaptMarksForPerCriterionWeighting(exam.getGrade(u))));

		verify(exam.getUsernames().equals(newExam.getUsernames()));
		verify(exam.getUsernames().stream().allMatch(u -> DoubleMath.fuzzyEquals(exam.getGrade(u).mark().getPoints(),
				newExam.getGrade(u).mark().getPoints(), 1e-6d)));
		return newExam;
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

	public <L extends K> String gradesToCsv(GradeAggregator aggregator, Map<L, ? extends MarksTree> trees) {
		final Set<L> keys = trees.keySet();
		checkArgument(!keys.isEmpty(), "Can’t determine identity headers with no keys.");

		final GenericExam<L> inputExam = new GenericExam<>(aggregator, trees);
		final PerCriterionWeightingExam<L> exam = toPerCriterionWeightingExam(inputExam);

		final NumberFormat formatter = NumberFormat.getNumberInstance(Locale.ENGLISH);
		final StringWriter stringWriter = new StringWriter();
		final CsvWriter writer = new CsvWriter(stringWriter, new CsvWriterSettings());

		final ImmutableSet<String> identityHeadersFromFunction = keys.stream()
				.flatMap(k -> identityFunction.apply(k).keySet().stream()).collect(ImmutableSet.toImmutableSet());
		final ImmutableSet<String> identityHeaders = identityHeadersFromFunction.isEmpty() ? ImmutableSet.of("")
				: identityHeadersFromFunction;
		final ImmutableSet<CriteriaPath> allPaths = exam.allPaths();

		final ImmutableList<String> headers = Streams
				.concat(identityHeaders.stream(), allPaths.stream().map(CsvGrades::shorten))
				.collect(ImmutableList.toImmutableList());
		writer.writeHeaders(headers);

		final String firstHeader = headers.iterator().next();

		for (L key : keys) {
			final Map<String, String> identity = identityFunction.apply(key);
			identity.entrySet().forEach(e -> writer.addValue(e.getKey(), e.getValue()));

			final Grade grade = exam.getGrade(key);
			allPaths.stream().forEach(p -> writer.addValue(CsvGrades.shorten(p),
					formatter.format(exam.weight(p) * grade.mark(p).getPoints() * denominator)));
			writer.writeValuesToRow();
		}
		writer.writeEmptyRow();

		LOGGER.debug("Writing summary data.");
		{
			writer.addValue(firstHeader, "Upper bound");
			allPaths.stream().forEach(
					p -> writer.addValue(CsvGrades.shorten(p), formatter.format(exam.weight(p) * denominator)));
			writer.writeValuesToRow();
		}

		{
			writer.addValue(firstHeader, "Average");
			allPaths.stream().forEach(p -> writer.addValue(CsvGrades.shorten(p),
					formatter.format(exam.weight(p) * exam.averagePoints(p) * denominator)));
			writer.writeValuesToRow();
		}

		{
			writer.addValue(firstHeader, "Nb ≠ 0");
			allPaths.stream().forEach(p -> writer.addValue(CsvGrades.shorten(p),
					formatter.format(exam.points(p).filter(v -> v != 0d).count())));
			writer.writeValuesToRow();
		}

		{
			writer.addValue(firstHeader, "Nb MAX");
			allPaths.stream().forEach(p -> writer.addValue(CsvGrades.shorten(p),
					formatter.format(exam.points(p).filter(v -> v == 1d).count())));
			writer.writeValuesToRow();
		}

		LOGGER.debug("Done writing.");
		writer.close();

		return stringWriter.toString();
	}
}
