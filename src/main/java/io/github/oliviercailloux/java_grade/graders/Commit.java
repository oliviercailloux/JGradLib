package io.github.oliviercailloux.java_grade.graders;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static io.github.oliviercailloux.jaris.exceptions.Unchecker.IO_UNCHECKER;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSet;

import io.github.oliviercailloux.git.fs.GitPath;
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
import io.github.oliviercailloux.java_grade.testers.JavaMarkHelper;

public class Commit implements GitGrader {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(Commit.class);

	public static final String PREFIX = "commit";

	public static final ZonedDateTime DEADLINE = ZonedDateTime.parse("2021-01-11T14:10:00+01:00[Europe/Paris]");

	public static void main(String[] args) throws Exception {
		GitGeneralGrader.grade(PREFIX, DEADLINE, new Commit());
	}

	private GitFileSystemHistory filteredHistory;

	Commit() {
	}

	@Override
	public WeightingGrade grade(GitFileSystemHistory history, String gitHubUsername) throws IOException {
		this.filteredHistory = checkNotNull(history);

		final ImmutableSet.Builder<CriterionGradeWeight> gradeBuilder = ImmutableSet.builder();

		{
			final Mark hasCommit = Mark.binary(!filteredHistory.getGraph().nodes().isEmpty());
			final Mark allCommitsRightName = allAndSomeCommitMatch(
					c -> JavaMarkHelper.committerAndAuthorIs(c, gitHubUsername));
			final WeightingGrade commitsGrade = WeightingGrade
					.from(ImmutableSet.of(CriterionGradeWeight.from(Criterion.given("At least one"), hasCommit, 1d),
							CriterionGradeWeight.from(Criterion.given("Right identity"), allCommitsRightName, 3d)));
			gradeBuilder.add(CriterionGradeWeight.from(Criterion.given("Has commits"), commitsGrade, 2d));
		}

		final Pattern coucouPattern = Marks.extend("coucou");
		{
			final Mark content = anyCommitMatches(compose(resolve("afile.txt"), contentMatches(coucouPattern)));
			final Mark branchAndContent = anyRefMatches(
					isBranch("coucou").and(compose(resolve("afile.txt"), contentMatches(coucouPattern))));
			final WeightingGrade coucouCommit = WeightingGrade.proportional(
					Criterion.given("'afile.txt' content (anywhere)"), content, Criterion.given("'coucou' content"),
					branchAndContent);
			gradeBuilder.add(CriterionGradeWeight.from(Criterion.given("Commit 'coucou'"), coucouCommit, 3d));
		}
		{
			final Pattern digitPattern = Marks.extend("\\d+");
			final Mark myIdContent = anyCommitMatches(compose(resolve("myid.txt"), contentMatches(digitPattern)));
			final Throwing.Predicate<GitPathRoot, IOException> both = compose(resolve("myid.txt"),
					contentMatches(digitPattern)).and(compose(resolve("afile.txt"), contentMatches(coucouPattern)));
			final Mark myIdAndAFileContent = anyCommitMatches(both);
			final Throwing.Predicate<GitPathRoot, IOException> branch = isBranch("main").or(isBranch("master"));
			final Mark mainContent = anyRefMatches(branch.and(both));
			final CriterionGradeWeight myIdGrade = CriterionGradeWeight.from(Criterion.given("'myid.txt' content"),
					myIdContent, 1d);
			final CriterionGradeWeight myIdAndAFileGrade = CriterionGradeWeight
					.from(Criterion.given("'myid.txt' and 'afile.txt' content (anywhere)"), myIdAndAFileContent, 1d);
			final CriterionGradeWeight mainGrade = CriterionGradeWeight
					.from(Criterion.given("'main' (or 'master') content"), mainContent, 2d);
			gradeBuilder.add(CriterionGradeWeight.from(Criterion.given("Commit 'main'"),
					WeightingGrade.from(ImmutableSet.of(myIdGrade, myIdAndAFileGrade, mainGrade)), 3d));
		}
		{
			final Mark anotherFile = anyCommitMatches(containsFileMatching(isFileNamed("another file.txt")));
			final Mark devRightFile = anyRefMatches(
					isBranch("dev").and(compose(resolve("sub/a/another file.txt"), Files::exists)));
			final WeightingGrade commit = WeightingGrade.proportional(Criterion.given("'another file.txt' exists"),
					anotherFile, Criterion.given("'dev' content"), devRightFile);
			gradeBuilder.add(CriterionGradeWeight.from(Criterion.given("Commit 'dev'"), commit, 2d));
		}

		return WeightingGrade.from(gradeBuilder.build());
	}

	private Mark allAndSomeCommitMatch(Throwing.Predicate<GitPathRoot, IOException> p) throws IOException {
		final GitFileSystemHistory filtered = filteredHistory.filter(p);
		final int nbMatch = filtered.getGraph().nodes().size();
		final boolean match = nbMatch >= 1 && nbMatch == filteredHistory.getGraph().nodes().size();
		return Mark.binary(match);
	}

	private Mark anyCommitMatches(Throwing.Predicate<GitPathRoot, IOException> p) throws IOException {
		final boolean match = !filteredHistory.filter(p).isEmpty();
		return Mark.binary(match);
	}

	private Mark anyRefMatches(Throwing.Predicate<GitPathRoot, IOException> p) throws IOException {
		final boolean matches = !filteredHistory.getRefsMatching(p).isEmpty();
		return Mark.binary(matches);
	}

	public static Throwing.Function<GitPathRoot, GitPath, IOException> resolve(String file) {
		return r -> r.resolve(file);
	}

	public static Throwing.Predicate<Path, IOException> contentMatches(Pattern pattern) {
		return p -> Files.exists(p) && pattern.matcher(Files.readString(p)).matches();
	}

	public static Throwing.Predicate<Path, IOException> isFileNamed(String fileName) {
		return p -> p.getFileName() != null && p.getFileName().toString().equals(fileName);
	}

	public static <FO extends PI, PI> Throwing.Predicate<GitPathRoot, IOException> compose(
			Throwing.Function<GitPathRoot, FO, IOException> f, Throwing.Predicate<PI, IOException> p) {
		return r -> p.test(f.apply(r));
	}

	public static Throwing.Predicate<GitPathRoot, IOException> isBranch(String remoteBranch) {
		checkArgument(!remoteBranch.contains("/"));
		checkArgument(!remoteBranch.isEmpty());

		final Pattern patternBranch = Pattern.compile("refs/remotes/[^/]+/" + remoteBranch);
		return r -> patternBranch.matcher(r.getGitRef()).matches();
	}

	public static Throwing.Predicate<GitPathRoot, IOException> containsFileMatching(
			Throwing.Predicate<? super GitPath, IOException> predicate) {
		final Predicate<? super GitPath> wrappedPredicate = IO_UNCHECKER.wrapPredicate(predicate);
		return r -> {
			try (Stream<Path> found = Files.find(r, 100, (p, a) -> wrappedPredicate.test((GitPath) p))) {
				return found.anyMatch(p -> true);
			} catch (UncheckedIOException e) {
				throw e.getCause();
			}
		};
	}
}
