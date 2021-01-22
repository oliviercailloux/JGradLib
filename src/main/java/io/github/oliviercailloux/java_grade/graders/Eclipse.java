package io.github.oliviercailloux.java_grade.graders;

import static io.github.oliviercailloux.grade.GitGrader.Functions.resolve;
import static io.github.oliviercailloux.grade.GitGrader.Predicates.compose;
import static io.github.oliviercailloux.grade.GitGrader.Predicates.containsFileMatching;
import static io.github.oliviercailloux.grade.GitGrader.Predicates.contentMatches;
import static io.github.oliviercailloux.grade.GitGrader.Predicates.isBranch;
import static io.github.oliviercailloux.grade.GitGrader.Predicates.isFileNamed;

import java.io.IOException;
import java.nio.file.Files;
import java.time.ZonedDateTime;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSet;

import io.github.oliviercailloux.git.fs.GitPathRoot;
import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.CriterionGradeWeight;
import io.github.oliviercailloux.grade.GitFileSystemHistory;
import io.github.oliviercailloux.grade.GitGeneralGrader;
import io.github.oliviercailloux.grade.GitGrader;
import io.github.oliviercailloux.grade.Mark;
import io.github.oliviercailloux.grade.WeightingGrade;
import io.github.oliviercailloux.grade.WeightingGrader.CriterionGraderWeight;
import io.github.oliviercailloux.grade.markers.Marks;
import io.github.oliviercailloux.jaris.exceptions.Throwing;
import io.github.oliviercailloux.java_grade.testers.JavaMarkHelper;

// PreferenceInformation#asVoterInformation() for indentation
// /minimax-ex/src/main/java/io/github/oliviercailloux/minimax/utils/ForwardingMutableGraph.java for unused import statement
public class Eclipse implements GitGrader {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(Eclipse.class);

	public static final String PREFIX = "eclipse";

	public static final ZonedDateTime DEADLINE = ZonedDateTime.parse("2021-01-14T23:00:00+01:00[Europe/Paris]");

	public static void main(String[] args) throws Exception {
		GitGeneralGrader.grade(PREFIX, DEADLINE, new Eclipse());
	}

	Eclipse() {
	}

	@Override
	public WeightingGrade grade(GitFileSystemHistory history, String gitHubUsername) throws IOException {
		final ImmutableSet.Builder<CriterionGradeWeight> gradeBuilder = ImmutableSet.builder();

		CriterionGraderWeight.given(Criterion.given("Compile"),
				compose(resolve("file.java"), contentMatches(Marks.extendAll("withchar"))), 1d);

		{
			final Mark hasCommit = Mark.binary(!history.getGraph().nodes().isEmpty());
			final Mark allCommitsRightName = history
					.allAndSomeCommitMatch(c -> JavaMarkHelper.committerAndAuthorIs(c, gitHubUsername));
			final WeightingGrade commitsGrade = WeightingGrade
					.from(ImmutableSet.of(CriterionGradeWeight.from(Criterion.given("At least one"), hasCommit, 1d),
							CriterionGradeWeight.from(Criterion.given("Right identity"), allCommitsRightName, 3d)));
			gradeBuilder.add(CriterionGradeWeight.from(Criterion.given("Has commits"), commitsGrade, 2d));
		}

		final Pattern coucouPattern = Marks.extendWhite("coucou");
		{
			final Mark content = history.anyCommitMatches(compose(resolve("afile.txt"), contentMatches(coucouPattern)));
			final Mark branchAndContent = history.anyRefMatches(
					isBranch("coucou").and(compose(resolve("afile.txt"), contentMatches(coucouPattern))));
			final WeightingGrade coucouCommit = WeightingGrade.proportional(
					Criterion.given("'afile.txt' content (anywhere)"), content, Criterion.given("'coucou' content"),
					branchAndContent);
			gradeBuilder.add(CriterionGradeWeight.from(Criterion.given("Commit 'coucou'"), coucouCommit, 3d));
		}
		{
			final Pattern digitPattern = Marks.extendWhite("\\d+");
			final Mark myIdContent = history
					.anyCommitMatches(compose(resolve("myid.txt"), contentMatches(digitPattern)));
			final Throwing.Predicate<GitPathRoot, IOException> both = compose(resolve("myid.txt"),
					contentMatches(digitPattern)).and(compose(resolve("afile.txt"), contentMatches(coucouPattern)));
			final Mark myIdAndAFileContent = history.anyCommitMatches(both);
			final Throwing.Predicate<GitPathRoot, IOException> branch = isBranch("main").or(isBranch("master"));
			final Mark mainContent = history.anyRefMatches(branch.and(both));
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
			final Mark anotherFile = history.anyCommitMatches(containsFileMatching(isFileNamed("another file.txt")));
			final Mark devRightFile = history
					.anyRefMatches(isBranch("dev").and(compose(resolve("sub/a/another file.txt"), Files::exists)));
			final WeightingGrade commit = WeightingGrade.proportional(Criterion.given("'another file.txt' exists"),
					anotherFile, Criterion.given("'dev' content"), devRightFile);
			gradeBuilder.add(CriterionGradeWeight.from(Criterion.given("Commit 'dev'"), commit, 2d));
		}

		return WeightingGrade.from(gradeBuilder.build());
	}
}
