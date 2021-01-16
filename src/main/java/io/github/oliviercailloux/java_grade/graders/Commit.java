package io.github.oliviercailloux.java_grade.graders;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Verify.verify;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.time.ZonedDateTime;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

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
	@SuppressWarnings("hiding")
	public WeightingGrade grade(GitFileSystemHistory filteredHistory, String gitHubUsername) throws IOException {
		this.filteredHistory = checkNotNull(filteredHistory);

		final ImmutableSet.Builder<CriterionGradeWeight> gradeBuilder = ImmutableSet.builder();

		{
			final Mark hasCommit = Mark.binary(!filteredHistory.getGraph().nodes().isEmpty());
			final Mark allCommitsRightName = Mark.binary(
					filteredHistory.allMatchAndExists(c -> JavaMarkHelper.committerAndAuthorIs(c, gitHubUsername)));
			final WeightingGrade commitsGrade = WeightingGrade
					.from(ImmutableSet.of(CriterionGradeWeight.from(Criterion.given("At least one"), hasCommit, 1d),
							CriterionGradeWeight.from(Criterion.given("Right identity"), allCommitsRightName, 3d)));
			gradeBuilder.add(CriterionGradeWeight.from(Criterion.given("Has commits"), commitsGrade, 2d));
		}

		final Pattern coucouPattern = Marks.extend("coucou");
		{
			final WeightingGrade coucouCommit = WeightingGrade.proportional(
					Criterion.given("'afile.txt' content (anywhere)"),
					Mark.binary(filteredHistory.anyMatch(r -> matches(r.resolve("afile.txt"), coucouPattern))),
					Criterion.given("'coucou' content"), Mark.binary(matches("coucou", "afile.txt", coucouPattern)));
			gradeBuilder.add(CriterionGradeWeight.from(Criterion.given("Commit 'coucou'"), coucouCommit, 3d));
		}
		{
			final Pattern digitPattern = Marks.extend("\\d+");
			final CriterionGradeWeight myIdContent = CriterionGradeWeight.from(Criterion.given("'myid.txt' content"),
					Mark.binary(filteredHistory.anyMatch(r -> matches(r.resolve("myid.txt"), digitPattern))), 1d);
			final CriterionGradeWeight myIdAndAFileContent = CriterionGradeWeight.from(
					Criterion.given("'myid.txt' and 'afile.txt' content (anywhere)"),
					Mark.binary(filteredHistory.anyMatch(r -> matches(r.resolve("myid.txt"), digitPattern)
							&& matches(r.resolve("afile.txt"), coucouPattern))),
					1d);
			final CriterionGradeWeight mainContent = CriterionGradeWeight.from(
					Criterion.given("'main' (or 'master') content"),
					Mark.binary(
							(matches("main", "myid.txt", digitPattern) || matches("master", "myid.txt", digitPattern))
									&& (matches("main", "afile.txt", coucouPattern)
											|| matches("master", "afile.txt", coucouPattern))),
					2d);
			gradeBuilder.add(CriterionGradeWeight.from(Criterion.given("Commit 'main'"),
					WeightingGrade.from(ImmutableSet.of(myIdContent, myIdAndAFileContent, mainContent)), 3d));
		}
		{
			final Pattern anything = Pattern.compile(".*");
			final WeightingGrade commit = WeightingGrade.proportional(Criterion.given("'another file.txt' exists"),
					Mark.binary(
							filteredHistory.getFilesMatching(p -> p.getFileName().toString().equals("another file.txt"))
									.findAny().isPresent()),
					Criterion.given("'dev' content"), Mark.binary(matches("dev", "sub/a/another file.txt", anything)));
			gradeBuilder.add(CriterionGradeWeight.from(Criterion.given("Commit 'dev'"), commit, 2d));
		}

		return WeightingGrade.from(gradeBuilder.build());
	}

	boolean matches(String remoteBranch, String filePath, Pattern pattern) throws IOException {
		checkArgument(!remoteBranch.contains("/"));
		checkArgument(!remoteBranch.isEmpty());
		checkArgument(!filePath.startsWith("/"));

		final GitPathRoot matchingRef;
		{
			final Pattern patternBranch = Pattern.compile("refs/remotes/[^/]+/" + remoteBranch);
			final ImmutableSet<GitPathRoot> refs = filteredHistory.getGitFilesystem().getRefs();
			final ImmutableSet<GitPathRoot> matchingRefs = refs.stream()
					.filter(r -> patternBranch.matcher(r.getGitRef()).matches()).collect(ImmutableSet.toImmutableSet());
			LOGGER.debug("Among refs {}, matching {}: {}.", refs, patternBranch, matchingRefs);
			verify(matchingRefs.size() <= 1);
			if (matchingRefs.isEmpty()) {
				return false;
			}
			matchingRef = Iterables.getOnlyElement(matchingRefs);
		}

		return matches(matchingRef.resolve(filePath), pattern);
	}

	boolean matches(GitPath path, Pattern regExp) throws UncheckedIOException {
		try {
			if (filteredHistory.asDirect(path.getRoot()).isEmpty()) {
				return false;
			}
			if (!Files.exists(path)) {
				return false;
			}
			final String content = Files.readString(path);
			return regExp.matcher(content).matches();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
