package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Streams;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

class MaxGradeStructure extends GradeStructureAbstractImpl implements GradeStructure {

	private final ImmutableSet<Criterion> absolutes;
	private final ImmutableMap<Criterion, GradeStructure> subStructures;

	MaxGradeStructure(Set<Criterion> absolutes, Map<Criterion, GradeStructure> subStructures) {
		this.absolutes = ImmutableSet.copyOf(absolutes);
		this.subStructures = ImmutableMap.copyOf(subStructures);
	}

	@Override
	public DefaultAggregation getDefaultAggregation() {
		return DefaultAggregation.MAX;
	}

	@Override
	public ImmutableSet<Criterion> getAbsolutes() {
		return absolutes;
	}

	@Override
	public boolean isAbsolute(Criterion criterion) {
		return absolutes.contains(criterion);
	}

	@Override
	public ImmutableMap<Criterion, GradeStructure> getSubStructures() {
		return subStructures;
	}

	@Override
	public GradeStructure getStructure(Criterion criterion) {
		checkArgument(subStructures.containsKey(criterion));
		return subStructures.get(criterion);
	}

	@Override
	public ImmutableMap<Criterion, Double> getFixedWeights() {
		return ImmutableMap.of();
	}

	@Override
	public ImmutableMap<SubMark, Double> getWeights(Set<SubMark> subMarks) {
		final ImmutableSet<Criterion> criteria = subMarks.stream().map(SubMark::getCriterion)
				.collect(ImmutableSet.toImmutableSet());
		checkArgument(subMarks.size() == criteria.size());
		checkArgument(criteria.stream().noneMatch(this::isAbsolute));

		final Comparator<SubMark> comparingPoints = Comparator.comparing(s -> s.getGrade().getPoints());
		final ImmutableSortedSet<SubMark> subMarksLargestFirst = ImmutableSortedSet.copyOf(comparingPoints.reversed(),
				subMarks);

		final ImmutableMap.Builder<SubMark, Double> weightsBuilder = ImmutableMap.builder();
		final Stream<Double> weights = Stream.concat(Stream.of(1d), Stream.generate(() -> 0d));
		Streams.forEachPair(subMarksLargestFirst.stream(), weights, weightsBuilder::put);
		return weightsBuilder.build();

//		final double tolerance = 1e-6d;
//		final ImmutableSortedSet<Double> pointsLargestFirst = subMarksLargestFirst.stream().map(SubMark::getGrade)
//				.mapToDouble(Mark::points).boxed()
//				.collect(ImmutableSortedSet.toImmutableSortedSet(Comparator.naturalOrder()));
//		for (double points : pointsLargestFirst) {
//			final ImmutableSortedSet<Double> dangerZone = pointsLargestFirst.subSet(points + tolerance, true,
//					points + 2 * tolerance, true);
//			checkArgument(dangerZone.isEmpty(),
//					"Marks include elements close together but not approximately-equal (that is, equal-up-to-tolerance), which might break transitivity of approximate-equality: "
//							+ points + " and " + dangerZone + ".");
//		}
//		final ImmutableSet.Builder<ImmutableSet<SubMark>> equivalenceClassesBuilder = ImmutableSet.builder();
//		double currentPoints = subMarksLargestFirst.stream().map(SubMark::getGrade).mapToDouble(Mark::points)
//				.findFirst().orElse(-1d);
//		ImmutableSet.Builder<SubMark> currentClassBuilder = ImmutableSet.builder();
//		for (SubMark subMark : subMarksLargestFirst) {
//			final double newPoints = subMark.getGrade().points();
//			if (!DoubleMath.fuzzyEquals(newPoints, currentPoints, tolerance)) {
//				equivalenceClassesBuilder.add(currentClassBuilder.build());
//				currentClassBuilder = ImmutableSet.builder();
//			}
//			currentClassBuilder.add(subMark);
//			currentPoints = newPoints;
//		}
//		equivalenceClassesBuilder.add(currentClassBuilder.build());
//		final ImmutableSet<ImmutableSet<SubMark>> equivalenceClasses = equivalenceClassesBuilder.build();
//		verify(equivalenceClasses.stream().mapToInt(Set::size).sum() == subMarksLargestFirst.size());
//
//		final ImmutableMap.Builder<ImmutableSet<SubMark>, Range<Integer>> indicesBuilder = ImmutableMap.builder();
//		int index = 0;
//		for (ImmutableSet<SubMark> equivalenceClass : equivalenceClasses) {
//			indicesBuilder.put(equivalenceClass, Range.closed(index, index + equivalenceClass.size() - 1));
//			index += equivalenceClass.size();
//		}
//		final ImmutableMap<ImmutableSet<SubMark>, Range<Integer>> allIndices = indicesBuilder.build();
//
//		final ImmutableSet<SubMark> equivalenceClass = allIndices.keySet().stream().filter(e -> e.contains(criterion))
//				.collect(MoreCollectors.onlyElement());
//		final Range<Integer> theseIndices = allIndices.get(equivalenceClass);

//		final int criterionIndexByLargestMarks = Iterables
//				.indexOf(subMarksLargestFirst.stream().map(SubGrade::getCriterion).toList(), c -> c.equals(criterion));
//		verify(criterionIndexByLargestMarks >= 0);
//		return criterionIndexByLargestMarks == 0 ? 1d : 0d;
	}

	@Override
	public Mark getMark(Set<SubMark> subMarks) {
		return StructuredGrade.getMark(this, subMarks);
	}

}
