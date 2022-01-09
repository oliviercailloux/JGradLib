package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableSet;
import io.github.oliviercailloux.jaris.exceptions.Throwing;
import io.github.oliviercailloux.jaris.exceptions.Throwing.Function;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Could consider a WeightingGraderBuilder.
 * <li>Add (c1, predicate1, w1).
 * <li>Add (c2, p2, w2)
 * <li>Add c3, w3), then on the returned object:
 * <ul>
 * <li>Add f1, then on the returned object:
 * <ul>
 * <li>Add c1, p1 (over the output of f1), w1
 * <li>Add c2, p2 (f1), w2
 * <li>end f1
 * <li>f2
 * <li>Add c3, p3(f2), w3
 * <li>end f2
 * <li>end c3
 * <li>Add (c4, w4), …
 * <p>
 * One important advantage to lambdas here is that the intermediate objects need
 * not exist for this to make sense: if no pathroot, the corresponding structure
 * may be empty; if the function does not work, it may avoid applying the
 * remaining predicates, … Some of these functions may return Optional<NewType>
 * to indicate conditional continuation (or even Optional<SameType>).
 *
 * @param <E> the input type to the grader
 */
public class WeightingGrader<E> {

	public static class CriterionGraderWeight<E> {
		public static <E> CriterionGraderWeight<E> given(Criterion criterion,
				Throwing.Predicate<E, IOException> predicate, double weight) {
			return new CriterionGraderWeight<>(criterion,
					(Optional<E> o) -> Mark.binary(o.isPresent() && predicate.test(o.get())), weight);
		}

		public static <E> CriterionGraderWeight<E> given(Criterion criterion,
				Throwing.Function<Optional<E>, IGrade, IOException> grader, double weight) {
			return new CriterionGraderWeight<>(criterion, grader, weight);
		}

		private final Criterion criterion;
		private final Throwing.Function<Optional<E>, IGrade, IOException> grader;
		private final double weight;

		private CriterionGraderWeight(Criterion criterion, Throwing.Function<Optional<E>, IGrade, IOException> grader,
				double weight) {
			this.criterion = checkNotNull(criterion);
			this.grader = checkNotNull(grader);
			this.weight = weight;
		}

		public CriterionGradeWeight grade(Optional<E> path) throws IOException {
			return CriterionGradeWeight.from(criterion, grader.apply(path), weight);
		}
	}

	public static <E> WeightingGrader<E> getGrader(List<CriterionGraderWeight<E>> cs) {
		final ImmutableSet<Throwing.Function<Optional<E>, CriterionGradeWeight, IOException>> graders = cs.stream()
				.map(c -> ((Throwing.Function<Optional<E>, CriterionGradeWeight, IOException>) c::grade))
				.collect(ImmutableSet.toImmutableSet());
		return new WeightingGrader<>(graders);
	}

	private final ImmutableSet<Function<Optional<E>, CriterionGradeWeight, IOException>> subGraders;

	private WeightingGrader(Set<Throwing.Function<Optional<E>, CriterionGradeWeight, IOException>> subGraders) {
		checkArgument(!subGraders.isEmpty());
		this.subGraders = ImmutableSet.copyOf(subGraders);
	}

	public WeightingGrade getGrade(Optional<E> source) throws IOException {
		final ImmutableSet.Builder<CriterionGradeWeight> builder = ImmutableSet.builder();
		for (Throwing.Function<Optional<E>, CriterionGradeWeight, IOException> subGrader : subGraders) {
			final CriterionGradeWeight grade = subGrader.apply(source);
			builder.add(grade);
		}
		return WeightingGrade.from(builder.build());
	}
}
