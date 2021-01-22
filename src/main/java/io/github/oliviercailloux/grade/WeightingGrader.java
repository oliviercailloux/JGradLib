package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.google.common.collect.ImmutableSet;

import io.github.oliviercailloux.jaris.exceptions.Throwing;
import io.github.oliviercailloux.jaris.exceptions.Throwing.Function;

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
