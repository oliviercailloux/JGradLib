package io.github.oliviercailloux.java_grade.graders;

import static com.google.common.base.Verify.verify;
import static io.github.oliviercailloux.grade.GitGrader.Predicates.compose;
import static io.github.oliviercailloux.grade.GitGrader.Predicates.contentMatches;
import static io.github.oliviercailloux.grade.GitGrader.Predicates.isFileNamed;
import static io.github.oliviercailloux.jaris.exceptions.Unchecker.IO_UNCHECKER;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import io.github.oliviercailloux.gitjfs.GitPath;
import io.github.oliviercailloux.gitjfs.impl.GitPathRootImpl;
import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.CriterionGradeWeight;
import io.github.oliviercailloux.grade.DeadlineGrader;
import io.github.oliviercailloux.grade.GitFileSystemHistory;
import io.github.oliviercailloux.grade.GitGeneralGrader;
import io.github.oliviercailloux.grade.GitGrader;
import io.github.oliviercailloux.grade.GitWork;
import io.github.oliviercailloux.grade.IGrade;
import io.github.oliviercailloux.grade.RepositoryFetcher;
import io.github.oliviercailloux.grade.WeightingGrade;
import io.github.oliviercailloux.grade.markers.Marks;
import io.github.oliviercailloux.grade.old.Mark;
import io.github.oliviercailloux.jaris.throwing.TFunction;
import io.github.oliviercailloux.jaris.throwing.TPredicate;
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

// /minimax-ex/src/main/java/io/github/oliviercailloux/minimax/utils/ForwardingMutableGraph.java for unused import statement
public class Eclipse implements GitGrader {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(Eclipse.class);

	public static final String PREFIX = "eclipse";

	public static final ZonedDateTime DEADLINE = ZonedDateTime.parse("2021-01-14T23:00:00+01:00[Europe/Paris]");

	private GitFileSystemHistory history;

	private GitFileSystemHistory authoredHistory;

	private boolean excludeMine;

	public static void main(String[] args) throws Exception {
		final RepositoryFetcher fetcher = RepositoryFetcher.withPrefix(PREFIX);
		GitGeneralGrader.using(fetcher, DeadlineGrader.usingGitGrader(new Eclipse(), DEADLINE)).grade();
	}

	Eclipse() {
		excludeMine = true;
	}

	Eclipse setIncludeMine() {
		excludeMine = false;
		return this;
	}

	@Override
	public WeightingGrade grade(GitWork work) throws IOException {
		this.history = work.getHistory();
		if (excludeMine) {
			authoredHistory = history.filter(r -> !r.getCommit().authorName().equals("Olivier Cailloux")
					&& !r.getCommit().authorName().equals("xoxor")
					&& !r.getCommit().authorName().equals("Beatrice Napolitano"));
		} else {
			authoredHistory = history;
		}
		final ImmutableSet<String> authors = authoredHistory.getGraph().nodes().stream()
				.map(c -> IO_UNCHECKER.getUsing(c::getCommit).authorName()).distinct()
				.collect(ImmutableSet.toImmutableSet());
		verify(authors.size() <= 1, authors.toString());
		LOGGER.debug("Considering whole history {} and own history {}.", history.getGraph().nodes(),
				authoredHistory.getGraph().nodes());

		final ImmutableSet.Builder<CriterionGradeWeight> gradeBuilder = ImmutableSet.builder();

		{
			final Mark hasCommit = Mark.binary(!authoredHistory.getGraph().nodes().isEmpty());
			gradeBuilder.add(CriterionGradeWeight.from(Criterion.given("At least one"), hasCommit, 1d));
		}
		LOGGER.info("Grading compile.");
		gradeBuilder.add(CriterionGradeWeight.from(Criterion.given("Compile"),
				authoredHistory.getBestGrade(this::compileGrade, 1d), 2.5d));
		LOGGER.info("Grading warning.");
		gradeBuilder.add(CriterionGradeWeight.from(Criterion.given("Warning"),
				authoredHistory.getBestGrade(this::warningGrade, 1d), 2.5d));
		LOGGER.info("Grading helper.");
		gradeBuilder.add(CriterionGradeWeight.from(Criterion.given("StrategyHelper → Helper"),
				authoredHistory.getBestGrade(this::helperGrade, 1d), 3.5d));
		LOGGER.info("Grading courses.");
		gradeBuilder.add(CriterionGradeWeight.from(Criterion.given("courses.soc"),
				authoredHistory.getBestGrade(this::coursesGrade, 1d), 3.5d));
		LOGGER.info("Grading number.");
		gradeBuilder.add(CriterionGradeWeight.from(Criterion.given("Number"),
				authoredHistory.getBestGrade(this::numberGrade, 1d), 3.5d));
		LOGGER.info("Grading formatting.");
		gradeBuilder.add(CriterionGradeWeight.from(Criterion.given("Formatting"),
				authoredHistory.getBestGrade(this::formattingGrade, 1d), 3.5d));

		return WeightingGrade.from(gradeBuilder.build());
	}

	void setHistory(GitFileSystemHistory history) {
		this.history = history;
		this.authoredHistory = history;
	}

	private IGrade compileGrade(Optional<GitPathRootImpl> p) throws IOException {
		final CriterionGradeWeight c1 = CriterionGradeWeight.from(Criterion.given("Compile"),
				Mark.binary(p.isPresent() && compiles(p.get())), 7d);
		final CriterionGradeWeight c2 = CriterionGradeWeight.from(Criterion.given("Single change"),
				Mark.binary(p.isPresent() && singleChangeAbout(p.get(), "QuestioningConstraint.java")), 3d);
		return WeightingGrade.from(ImmutableList.of(c1, c2));

	}

	boolean compiles(GitPathRootImpl p) throws IOException {
		LOGGER.debug("Files matching.");
		final TFunction<GitPathRootImpl, ImmutableSet<GitPath>, IOException> filesMatching = Functions
				.filesMatching(isFileNamed("QuestioningConstraint.java"));
		final ImmutableSet<GitPath> matching = filesMatching.apply(p);
		LOGGER.debug("Files matching found: {}.", matching);
		final Pattern patternCompiles = Pattern.compile(".*^(?<indent>\\h+)return[\\v\\h]+kind[\\v\\h]*;.*",
				Pattern.DOTALL | Pattern.MULTILINE);
		final TPredicate<ImmutableSet<GitPath>, IOException> singletonAndMatch = Predicates
				.singletonAndMatch(contentMatches(patternCompiles));
		final boolean tested = singletonAndMatch.test(matching);
		LOGGER.debug("Predicate known: {}.", tested);
		return tested;
//		return compose(filesMatching, singletonAndMatch).test(p);
	}

	boolean singleChangeAbout(GitPathRootImpl p, String file) throws IOException {
		final Set<GitPathRootImpl> predecessors = history.getGraph().predecessors(p);
		return (predecessors.size() == 1) && singleDiffAbout(Iterables.getOnlyElement(predecessors), p, file);
	}

	private boolean singleDiffAbout(GitPathRootImpl predecessor, GitPathRootImpl p, String file) throws IOException {
		final ImmutableSet<DiffEntry> diff = history.getDiff(predecessor, p);
		return diff.size() == 1 && diffIsAboutFile(Iterables.getOnlyElement(diff), file);
	}

	private boolean diffIsAboutFile(DiffEntry singleDiff, String file) {
		return singleDiff.getOldPath().contains(file) && singleDiff.getNewPath().contains(file);
	}

	private IGrade warningGrade(Optional<GitPathRootImpl> p) throws IOException {
		final CriterionGradeWeight c1 = CriterionGradeWeight.from(Criterion.given("Warning"),
				Mark.binary(p.isPresent() && warning(p.get())), 7d);
		final CriterionGradeWeight c2 = CriterionGradeWeight.from(Criterion.given("Single change"),
				Mark.binary(p.isPresent() && singleChangeAbout(p.get(), "ConstraintsOnWeights.java")), 3d);
		return WeightingGrade.from(ImmutableList.of(c1, c2));

	}

	boolean warning(GitPathRootImpl p) throws IOException {
		final Pattern pattern = Pattern.compile(".*^(?<indent>\\h+)builder[\\v\\h]*=[\\v\\h]*mp[\\v\\h]*;.*",
				Pattern.DOTALL | Pattern.MULTILINE);
		return compose(Functions.filesMatching(isFileNamed("ConstraintsOnWeights.java")),
				Predicates.singletonAndMatch(contentMatches(pattern))).test(p);
	}

	private IGrade helperGrade(Optional<GitPathRootImpl> p) throws IOException {
		return Mark.binary(p.isPresent() && helper(p.get()));

	}

	private boolean helper(GitPathRootImpl p) throws IOException {
		return compose(Functions.filesMatching(isFileNamed("StrategyHelperTests.java")),
				Predicates.singletonAndMatch(contentMatches(Marks.extendAll("StrategyHelper[^T]")).negate())).test(p);
	}

	private IGrade coursesGrade(Optional<GitPathRootImpl> p) throws IOException {
		final CriterionGradeWeight c1 = CriterionGradeWeight.from(Criterion.given("courses.soc"),
				Mark.binary(p.isPresent() && coursesChange(p.get())), 7d);
		final CriterionGradeWeight c2 = CriterionGradeWeight.from(Criterion.given("Single change"),
				Mark.binary(p.isPresent() && singleChangeAbout(p.get(), "courses.soc")), 3d);
		return WeightingGrade.from(ImmutableList.of(c1, c2));

	}

	private boolean coursesChange(GitPathRootImpl p) throws IOException {
		return compose(Functions.filesMatching(isFileNamed("courses.soc")),
				Predicates.singletonAndMatch(contentMatches(Pattern.compile("^8[\\r\\n]1.+", Pattern.DOTALL)))).test(p);
	}

	private IGrade numberGrade(Optional<GitPathRootImpl> p) throws IOException {
		final CriterionGradeWeight c1 = CriterionGradeWeight.from(Criterion.given("Number"),
				Mark.binary(p.isPresent() && numberChange(p.get())), 7d);
		final CriterionGradeWeight c2 = CriterionGradeWeight.from(Criterion.given("Single change"),
				Mark.binary(p.isPresent() && singleChangeAbout(p.get(), "Oracles m = 10, n = 6, 100.json")), 3d);
		return WeightingGrade.from(ImmutableList.of(c1, c2));

	}

	private boolean numberChange(GitPathRootImpl p) throws IOException {
		return compose(Functions.filesMatching(isFileNamed("Oracles m = 10, n = 6, 100.json")),
				Predicates.singletonAndMatch(contentMatches(Marks.extendAll("0.8388174124160426")))).test(p);
	}

	private IGrade formattingGrade(Optional<GitPathRootImpl> p) throws IOException {
		return Mark.binary(p.isPresent() && formatted(p.get()));

	}

	boolean formatted(GitPathRootImpl p) throws IOException {
		return compose(Functions.filesMatching(isFileNamed("PreferenceInformation.java")),
				Predicates.singletonAndMatch(this::isFormatted)).test(p);
	}

	private boolean isFormatted(Path p) throws IOException {
		if (!Files.exists(p)) {
			return false;
		}
		final String content = Files.readString(p);
		final Pattern patternOne = Pattern.compile("(?<start>.*)^(?<indent>\\h+)checkState\\h*\\(c\\h*==\\h*null\\);.*",
				Pattern.DOTALL | Pattern.MULTILINE);
		final Pattern patternTwo = Pattern.compile("(?<start>.*)^(?<indent>\\h+)verify\\h*\\(v\\h*!=\\h*null\\);.*",
				Pattern.DOTALL | Pattern.MULTILINE);
		final Matcher matcherOne = patternOne.matcher(content);
		final Matcher matcherTwo = patternTwo.matcher(content);
		if (!matcherOne.matches() || !matcherTwo.matches()) {
			return false;
		}
		final String indentOne = matcherOne.group("indent");
		final String indentTwo = matcherTwo.group("indent");
		verify(!indentOne.isEmpty());
		verify(!indentTwo.isEmpty());
		return indentOne.equals(indentTwo);
	}
}
