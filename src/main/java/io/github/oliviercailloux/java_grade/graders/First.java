package io.github.oliviercailloux.java_grade.graders;

import static com.google.common.base.Verify.verify;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.graph.Graphs;
import io.github.oliviercailloux.git.fs.GitPath;
import io.github.oliviercailloux.git.fs.GitPathRoot;
import io.github.oliviercailloux.grade.BatchGitHistoryGrader;
import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.GitFileSystemHistory;
import io.github.oliviercailloux.grade.GitFileSystemWithHistoryFetcherByPrefix;
import io.github.oliviercailloux.grade.Grade;
import io.github.oliviercailloux.grade.GradeAggregator;
import io.github.oliviercailloux.grade.GitFsGrader;
import io.github.oliviercailloux.grade.Mark;
import io.github.oliviercailloux.grade.MarksTree;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class First implements GitFsGrader<RuntimeException> {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(First.class);

	public static final String PREFIX = "first";

	public static void main(String[] args) throws Exception {
		final BatchGitHistoryGrader<RuntimeException> grader = BatchGitHistoryGrader
				.given(() -> GitFileSystemWithHistoryFetcherByPrefix.getRetrievingByPrefix(PREFIX));
		grader.getAndWriteGrades(ZonedDateTime.parse("2022-03-07T14:08:00+01:00[Europe/Paris]"), Duration.ofMinutes(3),
				new First(), 0.15d, Path.of("grades " + PREFIX), PREFIX + " " + Instant.now());
	}

	private static final Criterion C0 = Criterion.given("Anything committed");
	private static final Criterion C1 = Criterion.given("First commit");
	private static final Criterion C_TWO = Criterion.given("Exactly two files");
	private static final Criterion C_EXISTS_S = Criterion.given("Exists Some file");
	private static final Criterion C_EXISTS_A = Criterion.given("Exists Another file");
	private static final Criterion C_CONTENTS_S = Criterion.given("Contents Some file");
	private static final Criterion C_CONTENTS_A = Criterion.given("Contents Another file");
	private static final Criterion C_THREE = Criterion.given("Exactly three files");
	private static final Criterion C2 = Criterion.given("Second commit");
	private static final Criterion C_EXISTS_THIRD = Criterion.given("Exists Third file");
	private static final Criterion C_CONTENTS_THIRD = Criterion.given("Contents Third file");

	@Override
	public MarksTree grade(GitFileSystemHistory data) {
		verify(!data.getGraph().nodes().isEmpty());

		final ImmutableSet<GitPathRoot> commitsOrdered = data.getRoots().stream()
				.flatMap(r -> Graphs.reachableNodes(data.getGraph(), r).stream())
				.collect(ImmutableSet.toImmutableSet());
		final ImmutableSet<GitPathRoot> commitsOrderedExceptRoots = Sets.difference(commitsOrdered, data.getRoots())
				.immutableCopy();
		LOGGER.info("Commits ordered (except for roots): {}.", commitsOrderedExceptRoots);
		final int nbCommits = commitsOrderedExceptRoots.size();

		final MarksTree anyCommitMark = Mark.binary(!commitsOrderedExceptRoots.isEmpty(),
				String.format("Found %s commit%s, not counting the root ones", nbCommits, nbCommits == 1 ? "" : "s"),
				"");

		final Comparator<MarksTree> byPoints = Comparator
				.comparing(m -> Grade.given(firstCommitDiscriminator(), m).mark().getPoints());
		final GitPathRoot firstCommit = commitsOrderedExceptRoots.stream()
				.sorted(Comparator.comparing(this::firstCommitMark, byPoints.reversed())).findFirst()
				.orElse(data.getRoots().iterator().next());
		final MarksTree firstCommitMark = firstCommitMark(firstCommit);

		final Comparator<MarksTree> byPointsSecond = Comparator
				.comparing(m -> Grade.given(secondCommitDiscriminator(), m).mark().getPoints());
		final GitPathRoot secondCommit = Graphs.reachableNodes(data.getGraph(), firstCommit).stream()
				.sorted(Comparator.comparing(this::secondCommitMark, byPointsSecond.reversed())).findFirst()
				.orElse(data.getRoots().iterator().next());
		final MarksTree secondCommitMark = secondCommitMark(secondCommit);

		return MarksTree.composite(ImmutableMap.of(C0, anyCommitMark, C1, firstCommitMark, C2, secondCommitMark));
	}

	@Override
	public GradeAggregator getAggregator() {
		return GradeAggregator.staticAggregator(ImmutableMap.of(C0, 5d, C1, 6d, C2, 6d),
				ImmutableMap.of(C1, firstCommitAggregator(), C2, secondCommitAggregator()));
	}

	private MarksTree firstCommitMark(GitPathRoot root) {
		try {
			final long nbFiles = Files.find(root, Integer.MAX_VALUE, (p, a) -> Files.isRegularFile(p)).count();
			final boolean exactlyTwo = nbFiles == 2;
			final GitPath pathS = root.resolve("Some file.txt");
			final GitPath pathA = root.resolve("Some folder/Another file.txt");
			final boolean existsS = Files.exists(pathS);
			final boolean existsA = Files.exists(pathA);
			final String contentS = existsS ? Files.readString(pathS) : "";
			final String contentA = existsA ? Files.readString(pathA) : "";
			final Pattern patternS = Pattern.compile("A file!\\v+More content!\\v*");
			final boolean rightS = patternS.matcher(contentS).matches();
			verify(patternS.matcher("A file!\nMore content!").matches());
			verify(patternS.matcher("A file!\nMore content!\n").matches());
			verify(patternS.matcher("A file!\n\nMore content!\n").matches());
			final boolean rightA = contentA.matches("More content!\\v*");
			final String id = root.getCommit().getId().getName().substring(0, 6);
			final String comment = "Using commit " + id;
			final MarksTree mark = MarksTree.composite(ImmutableMap.of(C_TWO, Mark.binary(exactlyTwo, comment, comment),
					C_EXISTS_S, Mark.binary(existsS), C_EXISTS_A, Mark.binary(existsA), C_CONTENTS_S,
					Mark.binary(rightS), C_CONTENTS_A, Mark.binary(rightA)));
			LOGGER.debug("Commit {}; Seen content {}, matches? {}; score {}.", id, contentS, rightS,
					Grade.given(firstCommitAggregator(), mark).mark().getPoints());
			return mark;
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	private GradeAggregator firstCommitAggregator() {
		return GradeAggregator.MIN;
	}

	private GradeAggregator firstCommitDiscriminator() {
		return GradeAggregator.staticAggregator(
				ImmutableMap.of(C_TWO, 1d, C_EXISTS_S, 1d, C_EXISTS_A, 1d, C_CONTENTS_S, 1d, C_CONTENTS_A, 1d),
				ImmutableMap.of());
	}

	private MarksTree secondCommitMark(GitPathRoot root) {
		try {
			final long nbFiles = Files.find(root, Integer.MAX_VALUE, (p, a) -> Files.isRegularFile(p)).count();
			final boolean exactlyThree = nbFiles == 3;
			final GitPath pathS = root.resolve("Some file.txt");
			final GitPath pathA = root.resolve("Some folder/Another file.txt");
			final GitPath pathThird = root.resolve("a subfolder/A third file.txt");
			final boolean existsS = Files.exists(pathS);
			final boolean existsA = Files.exists(pathA);
			final boolean existsThird = Files.exists(pathThird);
			final String contentS = existsS ? Files.readString(pathS) : "";
			final boolean rightS = contentS.matches("A file!\\v+More content!\\v*");
			final String contentA = existsA ? Files.readString(pathA) : "";
			final boolean rightA = contentA.matches("More content!\\v*");
			final String contentThird = existsThird ? Files.readString(pathThird) : "";
			final boolean rightThird = contentThird.matches("Hello\\v*");
			final String comment = "Using commit " + root.getCommit().getId().getName().substring(0, 6);
			final ImmutableMap.Builder<Criterion, MarksTree> builder = ImmutableMap.builder();
			builder.put(C_THREE, Mark.binary(exactlyThree, comment, comment));
			builder.put(C_EXISTS_S, Mark.binary(existsS));
			builder.put(C_CONTENTS_S, Mark.binary(rightS));
			builder.put(C_EXISTS_A, Mark.binary(existsA));
			builder.put(C_CONTENTS_A, Mark.binary(rightA));
			builder.put(C_EXISTS_THIRD, Mark.binary(existsThird));
			builder.put(C_CONTENTS_THIRD, Mark.binary(rightThird));
			return MarksTree.composite(builder.build());
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	private GradeAggregator secondCommitAggregator() {
		return GradeAggregator.MIN;
	}

	private GradeAggregator secondCommitDiscriminator() {
		final ImmutableMap.Builder<Criterion, Double> builder = ImmutableMap.builder();
		builder.put(C_THREE, 1d);
		builder.put(C_EXISTS_S, 1d);
		builder.put(C_CONTENTS_S, 1d);
		builder.put(C_EXISTS_A, 1d);
		builder.put(C_CONTENTS_A, 1d);
		builder.put(C_EXISTS_THIRD, 1d);
		builder.put(C_CONTENTS_THIRD, 1d);
		return GradeAggregator.staticAggregator(builder.build(), ImmutableMap.of());
	}
}
