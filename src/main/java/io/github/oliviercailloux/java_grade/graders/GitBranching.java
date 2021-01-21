package io.github.oliviercailloux.java_grade.graders;

import static io.github.oliviercailloux.grade.GitGrader.Functions.resolve;
import static io.github.oliviercailloux.grade.GitGrader.Predicates.compose;
import static io.github.oliviercailloux.grade.GitGrader.Predicates.contentMatches;
import static io.github.oliviercailloux.grade.GitGrader.Predicates.isBranch;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

import io.github.oliviercailloux.git.fs.GitPathRoot;
import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.CriterionGradeWeight;
import io.github.oliviercailloux.grade.GitFileSystemHistory;
import io.github.oliviercailloux.grade.GitGeneralGrader;
import io.github.oliviercailloux.grade.GitGrader;
import io.github.oliviercailloux.grade.Mark;
import io.github.oliviercailloux.grade.WeightingGrade;
import io.github.oliviercailloux.grade.markers.Marks;
import io.github.oliviercailloux.jaris.exceptions.Throwing;
import io.github.oliviercailloux.jaris.exceptions.Throwing.Predicate;

/**
 * // 95dc71b131e08e5870c7e94d21b6adc752205c6c = Start, for Jaris. // git
 * checkout 17874593e8492177f525c57cc24f925beab7f9c9 --
 * src/main/java/io/github/oliviercailloux/java_grade/ex/GitBrGrader.java
 *
 */
public class GitBranching implements GitGrader {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GitBranching.class);

	public static final String PREFIX = "git-branching";

	public static final ZonedDateTime DEADLINE = ZonedDateTime.parse("2021-01-13T14:17:00+01:00[Europe/Paris]");

	public static void main(String[] args) throws Exception {
		GitGeneralGrader.grade(PREFIX, DEADLINE, new GitBranching());
	}

	private static class CriterionPredicateWeight {
		public final Criterion criterion;
		public final Throwing.Predicate<GitPathRoot, IOException> predicate;
		public final double weight;

		public CriterionPredicateWeight(Criterion criterion, Predicate<GitPathRoot, IOException> predicate,
				double weight) {
			this.criterion = criterion;
			this.predicate = predicate;
			this.weight = weight;
		}

		public CriterionGradeWeight grade(GitPathRoot path) throws IOException {
			return CriterionGradeWeight.from(criterion, Mark.binary(predicate.test(path)), weight);
		}

		public CriterionGradeWeight grade(Optional<GitPathRoot> path) throws IOException {
			return CriterionGradeWeight.from(criterion, Mark.binary(path.isPresent() && predicate.test(path.get())),
					weight);
		}
	}

	GitBranching() {
	}

	@Override
	public WeightingGrade grade(GitFileSystemHistory history, String gitHubUsername) throws IOException {
		final ImmutableSet.Builder<CriterionGradeWeight> gradeBuilder = ImmutableSet.builder();

		{
			final Mark hasCommit = Mark.binary(!history.getGraph().nodes().isEmpty());
			gradeBuilder.add(CriterionGradeWeight.from(Criterion.given("At least one"), hasCommit, 1d));
		}

		final Function<GitPathRoot, Set<GitPathRoot>> graphSiblings = r -> getSingleParent(history, r)
				.map(p -> history.getGraph().successors(p)).orElse(ImmutableSet.of(r));
		final Throwing.Function<GitPathRoot, Set<GitPathRoot>, IOException> siblings = r -> graphSiblings.apply(r);
		final Throwing.Predicate<GitPathRoot, IOException> hasAFewSiblings = compose(siblings,
				s -> 2 <= s.size() && s.size() <= 3);
		final CriterionPredicateWeight cSibl = new CriterionPredicateWeight(
				Criterion.given("Father has 2 or 3 children"), hasAFewSiblings, 1d);
		{
			final Predicate<GitPathRoot, IOException> isBranch = compose(history::getRefsTo, anyMatch(isBranch("br1")));

			final CriterionPredicateWeight cBr1 = new CriterionPredicateWeight(Criterion.given("Branch br1"), isBranch,
					1d);
			final Pattern helloPattern = Marks.extend("hello\\h+world");
			final CriterionPredicateWeight content = new CriterionPredicateWeight(Criterion.given("first.txt content"),
					compose(resolve("first.txt"), contentMatches(helloPattern)), 1d);
			final ImmutableList<CriterionPredicateWeight> cs = ImmutableList.of(cSibl, cBr1, content);

			final WeightingGrade grade = getGrade(history, cs);

			final CriterionGradeWeight aGrade = CriterionGradeWeight.from(Criterion.given("Commit A"), grade, 3d);
			gradeBuilder.add(aGrade);
		}
		{
			final Pattern coucouPattern = Marks.extend("coucou\\h+monde");
			final CriterionPredicateWeight content = new CriterionPredicateWeight(Criterion.given("first.txt content"),
					compose(resolve("first.txt"), contentMatches(coucouPattern)), 1d);
			final ImmutableList<CriterionPredicateWeight> cs = ImmutableList.of(cSibl, content);

			final WeightingGrade grade = getGrade(history, cs);

			final CriterionGradeWeight bGrade = CriterionGradeWeight.from(Criterion.given("Commit B"), grade, 2d);
			gradeBuilder.add(bGrade);
		}
		final Throwing.Function<GitPathRoot, Set<GitPathRoot>, IOException> fatherSiblings = r -> getSingleParent(
				history, r).map(graphSiblings).orElse(ImmutableSet.of());
		final Throwing.Predicate<GitPathRoot, IOException> hasCTopo = compose(fatherSiblings,
				s -> 2 <= s.size() && s.size() <= 3);
		{
			final CriterionPredicateWeight cFSibl = new CriterionPredicateWeight(
					Criterion.given("Grand-father has 2 or 3 children"), hasCTopo, 1d);
			final CriterionPredicateWeight cBr2 = new CriterionPredicateWeight(Criterion.given("Branch br2"),
					isBranch("br2"), 1d);
			final CriterionPredicateWeight content = new CriterionPredicateWeight(Criterion.given("content"),
					compose(resolve("a/b/c/x/z/some file.txt"), contentMatches(Marks.extend("2021"))), 1d);
			final ImmutableList<CriterionPredicateWeight> cs = ImmutableList.of(cFSibl, cBr2, content);

			final WeightingGrade grade = getGrade(history, cs);

			final CriterionGradeWeight cGrade = CriterionGradeWeight.from(Criterion.given("Commit C"), grade, 3d);
			gradeBuilder.add(cGrade);
		}
		/**
		 * Topo D: has exactly two parents; one which is a C (right branch OR C Topo)
		 * and one which is is an A (right branch or A topo). Thus the parents must
		 * include at least one A, at least one C, and no nothing.
		 */
//		{
//			final Throwing.Function<GitPathRoot, Set<GitPathRoot>, IOException> fatherSiblings = r -> getSingleParent(
//					history, r).map(graphSiblings).orElse(ImmutableSet.of());
//			final Throwing.Predicate<GitPathRoot, IOException> hasFSiblings = compose(fatherSiblings,
//					s -> 2 <= s.size() && s.size() <= 3);
//			final CriterionPredicateWeight cFSibl = new CriterionPredicateWeight(
//					Criterion.given("Grand-father has 2 or 3 children"), hasFSiblings, 1d);
//			final CriterionPredicateWeight cBr2 = new CriterionPredicateWeight(Criterion.given("Branch br2"),
//					isBranch("br2"), 1d);
//			final CriterionPredicateWeight content = new CriterionPredicateWeight(Criterion.given("content"),
//					compose(resolve("a/b/c/x/z/some file.txt"), contentMatches(Marks.extend("2021"))), 1d);
//			final ImmutableList<CriterionPredicateWeight> cs = ImmutableList.of(cFSibl, cBr2, content);
//
//			final WeightingGrade grade = getGrade(history, cs);
//
//			final CriterionGradeWeight cGrade = CriterionGradeWeight.from(Criterion.given("Commit C"), grade, 3d);
//			gradeBuilder.add(cGrade);
//		}

		return WeightingGrade.from(gradeBuilder.build());
	}

	private Predicate<Set<GitPathRoot>, IOException> anyMatch(final Predicate<GitPathRoot, IOException> predicate) {
		final Predicate<Set<GitPathRoot>, IOException> anyMatchPredicate = s -> {
			for (GitPathRoot r : s) {
				if (predicate.test(r)) {
					return true;
				}
			}
			return false;
		};
		return anyMatchPredicate;
	}

	private Optional<GitPathRoot> getSingleParent(GitFileSystemHistory history, GitPathRoot r) {
		final Set<GitPathRoot> parents = history.getGraph().predecessors(r).stream()
				.collect(ImmutableSet.toImmutableSet());
		if (parents.size() == 1) {
			return Optional.of(Iterables.getOnlyElement(parents));
		}
		return Optional.empty();
	}

	private WeightingGrade getGrade(GitFileSystemHistory history, ImmutableList<CriterionPredicateWeight> cs)
			throws IOException {
		final Throwing.Function<GitPathRoot, Integer, IOException> scorer = Functions
				.countTrue(cs.stream().map(c -> c.predicate).collect(ImmutableSet.toImmutableSet()));
		final Optional<GitPathRoot> best = history.getCommitMaximizing(scorer);
		final Set<Throwing.Function<Optional<GitPathRoot>, CriterionGradeWeight, IOException>> graders = cs.stream()
				.map(c -> ((Throwing.Function<Optional<GitPathRoot>, CriterionGradeWeight, IOException>) c::grade))
				.collect(ImmutableSet.toImmutableSet());
		final WeightingGrade grade = grade(graders, best);
		return grade;
	}

	public <T> WeightingGrade grade(Set<Throwing.Function<T, CriterionGradeWeight, IOException>> graders, T target)
			throws IOException {
		final ImmutableSet.Builder<CriterionGradeWeight> builder = ImmutableSet.builder();
		for (Throwing.Function<T, CriterionGradeWeight, IOException> grader : graders) {
			final CriterionGradeWeight grade = grader.apply(target);
			builder.add(grade);
		}
		return WeightingGrade.from(builder.build());
	}
}
