package io.github.oliviercailloux.java_grade.graders;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.github.oliviercailloux.grade.GitGrader.Functions.resolve;
import static io.github.oliviercailloux.grade.GitGrader.Predicates.compose;
import static io.github.oliviercailloux.grade.GitGrader.Predicates.contentMatches;
import static io.github.oliviercailloux.grade.GitGrader.Predicates.isBranch;

import java.io.IOException;
import java.nio.file.Path;
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
import io.github.oliviercailloux.grade.IGrade;
import io.github.oliviercailloux.grade.Mark;
import io.github.oliviercailloux.grade.WeightingGrade;
import io.github.oliviercailloux.grade.markers.Marks;
import io.github.oliviercailloux.jaris.exceptions.Throwing;
import io.github.oliviercailloux.jaris.exceptions.Throwing.Predicate;

/**
 * 95dc71b131e08e5870c7e94d21b6adc752205c6c = Start, for Jaris. // git checkout
 * 17874593e8492177f525c57cc24f925beab7f9c9 --
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

	private static class CriterionGraderWeight {
		public static CriterionGraderWeight given(Criterion criterion,
				Throwing.Predicate<GitPathRoot, IOException> predicate, double weight) {
			return new CriterionGraderWeight(criterion,
					path -> Mark.binary(path.isPresent() && predicate.test(path.get())), weight);
		}

		public static CriterionGraderWeight given(Criterion criterion,
				Throwing.Function<Optional<GitPathRoot>, IGrade, IOException> grader, double weight) {
			return new CriterionGraderWeight(criterion, grader, weight);
		}

		private final Criterion criterion;
		private final Throwing.Function<Optional<GitPathRoot>, IGrade, IOException> grader;
		private final double weight;

		private CriterionGraderWeight(Criterion criterion,
				Throwing.Function<Optional<GitPathRoot>, IGrade, IOException> grader, double weight) {
			this.criterion = checkNotNull(criterion);
			this.grader = checkNotNull(grader);
			this.weight = weight;
		}

		public CriterionGradeWeight grade(Optional<GitPathRoot> path) throws IOException {
			return CriterionGradeWeight.from(criterion, grader.apply(path), weight);
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
		final CriterionGraderWeight graderTopoSiblings = CriterionGraderWeight
				.given(Criterion.given("Father has 2 or 3 children"), hasAFewSiblings, 2d);
		final Predicate<GitPathRoot, IOException> aBranch = compose(history::getRefsTo, anyMatch(isBranch("br1")));
		{
			final Pattern helloPattern = Marks.extendWhite("hello\\h+world");
			final CriterionGraderWeight graderBr1 = CriterionGraderWeight.given(Criterion.given("Branch br1"), aBranch,
					2d);
			final CriterionGraderWeight graderContent = CriterionGraderWeight.given(
					Criterion.given("first.txt content"), compose(resolve("first.txt"), contentMatches(helloPattern)),
					1d);

			final Throwing.Function<Optional<GitPathRoot>, IGrade, IOException> grader = getGrader(
					ImmutableList.of(graderTopoSiblings, graderBr1, graderContent));
			final IGrade grade = getBestGrade(history, grader);

			final CriterionGradeWeight aGrade = CriterionGradeWeight.from(Criterion.given("Commit A"), grade, 5d);
			gradeBuilder.add(aGrade);
		}
		{
			final Pattern coucouPattern = Marks.extendWhite("coucou\\h+monde");
			final CriterionGraderWeight graderContent = CriterionGraderWeight.given(
					Criterion.given("first.txt content"), compose(resolve("first.txt"), contentMatches(coucouPattern)),
					1d);

			final Throwing.Function<Optional<GitPathRoot>, IGrade, IOException> grader = getGrader(
					ImmutableList.of(graderTopoSiblings, graderContent));
			final IGrade grade = getBestGrade(history, grader);

			final CriterionGradeWeight bGrade = CriterionGradeWeight.from(Criterion.given("Commit B"), grade, 3d);
			gradeBuilder.add(bGrade);
		}
		final Throwing.Function<GitPathRoot, Set<GitPathRoot>, IOException> fatherSiblings = r -> getSingleParent(
				history, r).map(graphSiblings).orElse(ImmutableSet.of());
		final Throwing.Predicate<GitPathRoot, IOException> hasCTopo = compose(fatherSiblings,
				s -> 2 <= s.size() && s.size() <= 3);
		final Predicate<GitPathRoot, IOException> cBranch = compose(history::getRefsTo, anyMatch(isBranch("br2")));
		{
			final CriterionGraderWeight graderTopoC = CriterionGraderWeight
					.given(Criterion.given("Grand-father has 2 or 3 children"), hasCTopo, 2d);
			final CriterionGraderWeight graderBr2 = CriterionGraderWeight.given(Criterion.given("Branch br2"), cBranch,
					2d);
			final CriterionGraderWeight graderContent = CriterionGraderWeight.given(Criterion.given("content"),
					compose(resolve("a/b/c/x/z/some file.txt"), contentMatches(Marks.extendWhite("2021"))), 1d);

			final Throwing.Function<Optional<GitPathRoot>, IGrade, IOException> grader = getGrader(
					ImmutableList.of(graderTopoC, graderBr2, graderContent));
			final IGrade grade = getBestGrade(history, grader);

			final CriterionGradeWeight cGrade = CriterionGradeWeight.from(Criterion.given("Commit C"), grade, 5d);
			gradeBuilder.add(cGrade);
		}
		{
			/**
			 * Topo D: has exactly two parents; one which is a C (right branch OR C Topo)
			 * and one which is is an A (right branch or A topo). Thus the parents must
			 * include at least one A, at least one C, and no nothing.
			 */
			final Predicate<GitPathRoot, IOException> isA = aBranch.or(hasAFewSiblings);
			final Predicate<GitPathRoot, IOException> isC = cBranch.or(hasCTopo);
			final Throwing.Function<GitPathRoot, ImmutableSet<GitPathRoot>, IOException> parents = r -> ImmutableSet
					.copyOf(history.getGraph().predecessors(r));
			final Throwing.Predicate<ImmutableSet<GitPathRoot>, IOException> aAndC = s -> s.size() == 2
					&& (isA.test(s.asList().get(0)) && isC.test(s.asList().get(1)));
			final Throwing.Predicate<ImmutableSet<GitPathRoot>, IOException> cAndA = s -> s.size() == 2
					&& (isC.test(s.asList().get(0)) && isA.test(s.asList().get(1)));
			final Throwing.Predicate<GitPathRoot, IOException> twoRightParents = compose(parents, aAndC.or(cAndA));
			final Predicate<GitPathRoot, IOException> dBranch = compose(history::getRefsTo, anyMatch(isBranch("br3")));

			final CriterionGraderWeight graderTopo = CriterionGraderWeight
					.given(Criterion.given("Has apparent A and C parents"), twoRightParents, 2d);
			final CriterionGraderWeight graderBr3 = CriterionGraderWeight.given(Criterion.given("Branch br3"), dBranch,
					2d);
			final CriterionGraderWeight graderContentSomeFile = CriterionGraderWeight.given(
					Criterion.given("some file"),
					compose(resolve("a/b/c/x/z/some file.txt"), contentMatches(Marks.extendWhite("2021"))), 1d);
			final Predicate<Path, IOException> merged = contentMatches(Marks.extendAll("<<<<<<<")).negate();
			final Predicate<Path, IOException> matchesApprox = contentMatches(Marks.extendAll("hello\\h+world"))
					.or(contentMatches(Marks.extendAll("coucou\\h+monde")));
			final CriterionGraderWeight graderContentFirstApprox = CriterionGraderWeight
					.given(Criterion.given("approx"), compose(resolve("first.txt"), matchesApprox.and(merged)), 1d);
			final CriterionGraderWeight graderContentFirstExact = CriterionGraderWeight
					.given(Criterion.given("exact"),
							compose(resolve("first.txt"),
									contentMatches(Pattern.compile("hello world\\v+coucou monde\\v*")).and(merged)),
							1d);
			final CriterionGraderWeight graderContentFirst = CriterionGraderWeight.given(Criterion.given("content"),
					getGrader(ImmutableList.of(graderContentFirstApprox, graderContentFirstExact)), 1d);
			final CriterionGraderWeight graderContent = CriterionGraderWeight.given(Criterion.given("content"),
					getGrader(ImmutableList.of(graderContentSomeFile, graderContentFirst)), 2d);

			final Throwing.Function<Optional<GitPathRoot>, IGrade, IOException> grader = getGrader(
					ImmutableList.of(graderTopo, graderBr3, graderContent));
			final IGrade grade = getBestGrade(history, grader);

			final CriterionGradeWeight dGrade = CriterionGradeWeight.from(Criterion.given("Commit D"), grade, 6d);
			gradeBuilder.add(dGrade);
		}

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

	public IGrade getGrade(GitFileSystemHistory history, ImmutableList<CriterionGraderWeight> cs) throws IOException {
		return getBestGrade(history, getGrader(cs));
	}

	private Throwing.Function<Optional<GitPathRoot>, IGrade, IOException> getGrader(
			ImmutableList<CriterionGraderWeight> cs) {
		final Set<Throwing.Function<Optional<GitPathRoot>, CriterionGradeWeight, IOException>> graders = cs.stream()
				.map(c -> ((Throwing.Function<Optional<GitPathRoot>, CriterionGradeWeight, IOException>) c::grade))
				.collect(ImmutableSet.toImmutableSet());
		final Throwing.Function<Optional<GitPathRoot>, IGrade, IOException> grader = getGrader(graders);
		return grader;
	}

	private IGrade getBestGrade(GitFileSystemHistory history,
			final Throwing.Function<Optional<GitPathRoot>, IGrade, IOException> grader) throws IOException {
		final Throwing.Function<Optional<GitPathRoot>, Double, IOException> scorer = r -> grader.apply(r).getPoints();
		final Optional<GitPathRoot> best = history.getCommitMaximizing(r -> scorer.apply(Optional.of(r)));
		return grader.apply(best);
	}

	private <FI> Throwing.Function<FI, IGrade, IOException> getGrader(
			Set<Throwing.Function<FI, CriterionGradeWeight, IOException>> graders) {
		return target -> {
			final ImmutableSet.Builder<CriterionGradeWeight> builder = ImmutableSet.builder();
			for (Throwing.Function<FI, CriterionGradeWeight, IOException> grader : graders) {
				final CriterionGradeWeight grade = grader.apply(target);
				builder.add(grade);
			}
			return WeightingGrade.from(builder.build());
		};
	}
}
