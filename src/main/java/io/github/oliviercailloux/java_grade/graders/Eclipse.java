package io.github.oliviercailloux.java_grade.graders;

import static com.google.common.base.Verify.verify;
import static io.github.oliviercailloux.grade.GitGrader.Predicates.compose;
import static io.github.oliviercailloux.grade.GitGrader.Predicates.contentMatches;
import static io.github.oliviercailloux.grade.GitGrader.Predicates.isFileNamed;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jgit.diff.DiffEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

import io.github.oliviercailloux.git.fs.GitPath;
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
import io.github.oliviercailloux.jaris.exceptions.Throwing.Function;
import io.github.oliviercailloux.jaris.exceptions.Throwing.Predicate;

// /minimax-ex/src/main/java/io/github/oliviercailloux/minimax/utils/ForwardingMutableGraph.java for unused import statement
public class Eclipse implements GitGrader {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(Eclipse.class);

	public static final String PREFIX = "eclipse";

	public static final ZonedDateTime DEADLINE = ZonedDateTime.parse("2021-01-14T23:00:00+01:00[Europe/Paris]");

	private GitFileSystemHistory h;

	public static void main(String[] args) throws Exception {
		GitGeneralGrader.grade(PREFIX, DEADLINE, new Eclipse());
	}

	Eclipse() {
	}

	@Override
	public WeightingGrade grade(GitFileSystemHistory history, String gitHubUsername) throws IOException {
		h = history;
		final ImmutableSet.Builder<CriterionGradeWeight> gradeBuilder = ImmutableSet.builder();

		{
			final Mark hasCommit = Mark.binary(!history.getGraph().nodes().isEmpty());
			gradeBuilder.add(CriterionGradeWeight.from(Criterion.given("At least one"), hasCommit, 1d));
		}
		gradeBuilder
				.add(CriterionGradeWeight.from(Criterion.given("Compile"), h.getBestGrade(this::compileGrade), 2.5d));
		gradeBuilder
				.add(CriterionGradeWeight.from(Criterion.given("Warning"), h.getBestGrade(this::warningGrade), 2.5d));
		gradeBuilder.add(CriterionGradeWeight.from(Criterion.given("StrategyHelper → Helper"),
				h.getBestGrade(this::helperGrade), 3.5d));
		gradeBuilder.add(
				CriterionGradeWeight.from(Criterion.given("courses.soc"), h.getBestGrade(this::coursesGrade), 3.5d));
		gradeBuilder.add(CriterionGradeWeight.from(Criterion.given("Number"), h.getBestGrade(this::numberGrade), 3.5d));
		gradeBuilder.add(
				CriterionGradeWeight.from(Criterion.given("Formatting"), h.getBestGrade(this::formattingGrade), 3.5d));

		return WeightingGrade.from(gradeBuilder.build());
	}

	private IGrade compileGrade(Optional<GitPathRoot> p) throws IOException {
		LOGGER.info("Computing compile.");
		final boolean compiles = compiles(p.get());
		LOGGER.info("Computed compile.");
		final CriterionGradeWeight c1 = CriterionGradeWeight.from(Criterion.given("Compile"),
				Mark.binary(p.isPresent() && compiles), 7d);
		final CriterionGradeWeight c2 = CriterionGradeWeight.from(Criterion.given("Single change"),
				Mark.binary(p.isPresent() && singleChangeAbout(p.get(), "QuestioningConstraint.java")), 3d);
		return WeightingGrade.from(ImmutableList.of(c1, c2));

	}

	private boolean compiles(GitPathRoot p) throws IOException {
		LOGGER.info("Files matching.");
		final Function<GitPathRoot, ImmutableSet<GitPath>, IOException> filesMatching = Functions
				.filesMatching(isFileNamed("QuestioningConstraint.java"));
		final ImmutableSet<GitPath> matching = filesMatching.apply(p);
		LOGGER.info("Files matching found.");
		final Predicate<ImmutableSet<GitPath>, IOException> singletonAndMatch = Predicates
				.singletonAndMatch(contentMatches(Marks.extendAll("[\\v\\h]+return[\\v\\h]+kind[\\v\\h]+;")));
		final boolean tested = singletonAndMatch.test(matching);
		LOGGER.info("Predicate known.");
		return tested;
//		return compose(filesMatching, singletonAndMatch).test(p);
	}

	private boolean singleChangeAbout(GitPathRoot p, String file) throws IOException {
		final Set<GitPathRoot> predecessors = h.getGraph().predecessors(p);
		return (predecessors.size() == 1) && singleDiffAbout(Iterables.getOnlyElement(predecessors), p, file);
	}

	private boolean singleDiffAbout(GitPathRoot predecessor, GitPathRoot p, String file) throws IOException {
		final ImmutableSet<DiffEntry> diff = h.getDiff(predecessor, p);
		return diff.size() == 1 && diffIsAboutFile(Iterables.getOnlyElement(diff), file);
	}

	private boolean diffIsAboutFile(DiffEntry singleDiff, String file) {
		return singleDiff.getOldPath().contains(file) && singleDiff.getNewPath().contains(file);
	}

	private IGrade warningGrade(Optional<GitPathRoot> p) throws IOException {
		final CriterionGradeWeight c1 = CriterionGradeWeight.from(Criterion.given("Warning"),
				Mark.binary(p.isPresent() && warning(p.get())), 7d);
		final CriterionGradeWeight c2 = CriterionGradeWeight.from(Criterion.given("Single change"),
				Mark.binary(p.isPresent() && singleChangeAbout(p.get(), "ConstraintsOnWeights.java")), 3d);
		return WeightingGrade.from(ImmutableList.of(c1, c2));

	}

	private boolean warning(GitPathRoot p) throws IOException {
		return compose(Functions.filesMatching(isFileNamed("ConstraintsOnWeights.java")), Predicates
				.singletonAndMatch(contentMatches(Marks.extendAll("[\\v\\h]+builder[\\v\\h]+=[\\v\\h]+mp[\\v\\h]+;"))))
						.test(p);
	}

	private IGrade helperGrade(Optional<GitPathRoot> p) throws IOException {
		return Mark.binary(p.isPresent() && helper(p.get()));

	}

	private boolean helper(GitPathRoot p) throws IOException {
		return compose(Functions.filesMatching(isFileNamed("StrategyHelperTests.java")),
				Predicates.singletonAndMatch(contentMatches(Marks.extendAll("StrategyHelper")).negate())).test(p);
	}

	private IGrade coursesGrade(Optional<GitPathRoot> p) throws IOException {
		final CriterionGradeWeight c1 = CriterionGradeWeight.from(Criterion.given("courses.soc"),
				Mark.binary(p.isPresent() && coursesChange(p.get())), 7d);
		final CriterionGradeWeight c2 = CriterionGradeWeight.from(Criterion.given("Single change"),
				Mark.binary(p.isPresent() && singleChangeAbout(p.get(), "courses.soc")), 3d);
		return WeightingGrade.from(ImmutableList.of(c1, c2));

	}

	private boolean coursesChange(GitPathRoot p) throws IOException {
		return compose(Functions.filesMatching(isFileNamed("courses.soc")),
				Predicates.singletonAndMatch(contentMatches(Pattern.compile("^8[\\r\\n]1.+", Pattern.DOTALL)))).test(p);
	}

	private IGrade numberGrade(Optional<GitPathRoot> p) throws IOException {
		final CriterionGradeWeight c1 = CriterionGradeWeight.from(Criterion.given("Number"),
				Mark.binary(p.isPresent() && numberChange(p.get())), 7d);
		final CriterionGradeWeight c2 = CriterionGradeWeight.from(Criterion.given("Single change"),
				Mark.binary(p.isPresent() && singleChangeAbout(p.get(), "Oracles m = 10, n = 6, 100.json")), 3d);
		return WeightingGrade.from(ImmutableList.of(c1, c2));

	}

	private boolean numberChange(GitPathRoot p) throws IOException {
		return compose(Functions.filesMatching(isFileNamed("Oracles m = 10, n = 6, 100.json")),
				Predicates.singletonAndMatch(contentMatches(Marks.extendAll("0.8388174124160426")))).test(p);
	}

	private IGrade formattingGrade(Optional<GitPathRoot> p) throws IOException {
		return Mark.binary(p.isPresent() && formatted(p.get()));

	}

	private boolean formatted(GitPathRoot p) throws IOException {
		return compose(Functions.filesMatching(isFileNamed("PreferenceInformation")),
				Predicates.singletonAndMatch(this::isFormatted)).test(p);
	}

	private boolean isFormatted(Path p) throws IOException {
		// checkState(c == null);
//		  verify(v != null);
		if (!Files.exists(p)) {
			return false;
		}
		final String content = Files.readString(p);
		final Pattern patternOne = Pattern.compile(".*^(<?indent>\\h+)checkState\\h+\\(c\\h*==\\h*null);.*",
				Pattern.DOTALL | Pattern.MULTILINE);
		final Pattern patternTwo = Pattern.compile(".*^(<?indent>\\h+)verify\\h+\\(v\\h*!=\\h*null);.*",
				Pattern.DOTALL | Pattern.MULTILINE);
		final Matcher matcherOne = patternOne.matcher(content);
		final Matcher matcherTwo = patternTwo.matcher(content);
		if (!matcherOne.matches() || !matcherTwo.matches()) {
			return false;
		}
		final String indentOne = matcherOne.group("indent");
		final String indentTwo = matcherOne.group("indent");
		verify(!indentOne.isEmpty());
		verify(!indentTwo.isEmpty());
		return indentOne.equals(indentTwo);
	}
}
