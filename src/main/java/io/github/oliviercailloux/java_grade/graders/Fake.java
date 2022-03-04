package io.github.oliviercailloux.java_grade.graders;

import static com.google.common.base.Verify.verify;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import io.github.oliviercailloux.git.fs.GitPath;
import io.github.oliviercailloux.git.fs.GitPathRoot;
import io.github.oliviercailloux.grade.BatchGitHistoryGrader;
import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.GitFileSystemHistory;
import io.github.oliviercailloux.grade.GitFileSystemWithHistoryFetcherByPrefix;
import io.github.oliviercailloux.grade.Grade;
import io.github.oliviercailloux.grade.GradeAggregator;
import io.github.oliviercailloux.grade.Grader;
import io.github.oliviercailloux.grade.Mark;
import io.github.oliviercailloux.grade.MarksTree;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Fake implements Grader<RuntimeException> {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(Fake.class);

	private static final String PREFIX = "fake";

	public static void main(String[] args) throws Exception {
		/*
		 * TODO exception ioE on stream.
		 *
		 * TODO weights all equal
		 *
		 * TODO min aggregator!
		 *
		 * TODO Owa is a sort of average weighter?
		 *
		 * TODO comment at node levels? (Using commit X, then a set of criteria)
		 *
		 * TODO does batch send non empty graphs only?
		 */
		final BatchGitHistoryGrader<RuntimeException> grader = BatchGitHistoryGrader
				.given(() -> GitFileSystemWithHistoryFetcherByPrefix.getRetrievingByPrefix(PREFIX));
		grader.getAndWriteGrades(new Fake(), 0.2d, Path.of("out - " + PREFIX), PREFIX + " " + Instant.now());
	}

	private static final Criterion C1 = Criterion.given("First commit");
	private static final Criterion C_TWO = Criterion.given("Exactly two files");
	private static final Criterion C_CONTENTS_A = Criterion.given("Contents A");
	private static final Criterion C_CONTENTS_S = Criterion.given("Contents S");
	private static final Criterion C_EXISTS_A = Criterion.given("Exists A");
	private static final Criterion C_EXISTS_S = Criterion.given("Exists S");
	private static final Criterion C_ONE = Criterion.given("Exactly one file");
	private static final Criterion C_NO_A = Criterion.given("! exists A");
	private static final Criterion C2 = Criterion.given("Second commit");

	@Override
	public MarksTree grade(GitFileSystemHistory data) {
		verify(!data.getGraph().nodes().isEmpty());

		final ImmutableSet<GitPathRoot> commitsOrdered = data.getRoots().stream()
				.flatMap(r -> Streams.concat(Stream.of(r), data.getGraph().successors(r).stream()))
				.collect(ImmutableSet.toImmutableSet());
		LOGGER.debug("Commits ordered: {}.", commitsOrdered);
		final Comparator<MarksTree> byPoints = Comparator
				.comparing(m -> Grade.given(firstCommitAggregator(), m).mark().getPoints());
		final GitPathRoot firstCommit = commitsOrdered.stream()
				.sorted(Comparator.comparing(this::firstCommitMark, byPoints.reversed())).findFirst()
				.orElse(data.getGraph().nodes().iterator().next());
		final MarksTree firstCommitMark = firstCommitMark(firstCommit);

		final Comparator<MarksTree> byPointsSecond = Comparator
				.comparing(m -> Grade.given(secondCommitAggregator(), m).mark().getPoints());
		final GitPathRoot secondCommit = data.getGraph().successors(firstCommit).stream()
				.sorted(Comparator.comparing(this::secondCommitMark, byPointsSecond.reversed())).findFirst()
				.orElse(data.getGraph().nodes().iterator().next());
		final MarksTree secondCommitMark = secondCommitMark(secondCommit);

		return MarksTree.composite(ImmutableMap.of(C1, firstCommitMark, C2, secondCommitMark));
	}

	@Override
	public GradeAggregator getAggregator() {
		return GradeAggregator.staticAggregator(ImmutableMap.of(C1, 1d, C2, 1d),
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
		return GradeAggregator.staticAggregator(
				ImmutableMap.of(C_TWO, 1d, C_EXISTS_S, 1d, C_EXISTS_A, 1d, C_CONTENTS_S, 1d, C_CONTENTS_A, 1d),
				ImmutableMap.of());
	}

	private MarksTree secondCommitMark(GitPathRoot root) {
		try {
			final long nbFiles = Files.find(root, Integer.MAX_VALUE, (p, a) -> Files.isRegularFile(p)).count();
			final boolean exactlyOne = nbFiles == 1;
			final GitPath pathS = root.resolve("Some file.txt");
			final GitPath pathA = root.resolve("Some folder/Another file.txt");
			final boolean existsS = Files.exists(pathS);
			final boolean existsA = Files.exists(pathA);
			final String contentS = existsS ? Files.readString(pathS) : "";
			final boolean rightS = contentS.matches("A file!\\v+More content!\\v*");
			final String comment = "Using commit " + root.getCommit().getId().getName().substring(0, 6);
			return MarksTree.composite(ImmutableMap.of(C_ONE, Mark.binary(exactlyOne, comment, comment), C_EXISTS_S,
					Mark.binary(existsS), C_NO_A, Mark.binary(!existsA), C_CONTENTS_S, Mark.binary(rightS)));
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	private GradeAggregator secondCommitAggregator() {
		return GradeAggregator.staticAggregator(
				ImmutableMap.of(C_ONE, 1d, C_EXISTS_S, 1d, C_NO_A, 1d, C_CONTENTS_S, 1d), ImmutableMap.of());
	}
}
