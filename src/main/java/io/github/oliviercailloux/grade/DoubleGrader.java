package io.github.oliviercailloux.grade;

import static com.google.common.base.Verify.verify;
import static io.github.oliviercailloux.jaris.exceptions.Unchecker.IO_UNCHECKER;

import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import io.github.oliviercailloux.git.git_hub.model.GitHubUsername;
import io.github.oliviercailloux.grade.ByTimeGrader.PreparedGrader;
import io.github.oliviercailloux.grade.DeadlineGrader.LinearPenalizer;
import io.github.oliviercailloux.java_grade.graders.Grader421;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.regex.Pattern;

public class DoubleGrader implements Grader<IOException> {

	private static final Criterion C_DIFF = Criterion.given("diff");
	private static final Criterion C_OLD = Criterion.given("old");
	private static final Criterion C_SECOND = Criterion.given("second");
	private final CodeGrader<RuntimeException> codeGrader;
	private final MavenCodeGrader<RuntimeException> m;
	private final LinearPenalizer penalizer;
	private final GradeModifier penalizerModifier;
	private final ByTimeGrader<RuntimeException> byTimeGraderOld;
	private final ByTimeGrader<RuntimeException> byTimeGraderSecond;

	public DoubleGrader(CodeGrader<RuntimeException> g) {
		codeGrader = g;
		m = new MavenCodeGrader<>(codeGrader);
		penalizer = LinearPenalizer.proportionalToLateness(Duration.ofMinutes(5));
		penalizerModifier = GradePenalizer.using(penalizer, Grader421.DEADLINE_ORIGINAL.toInstant());
		byTimeGraderOld = ByTimeGrader.using(Grader421.DEADLINE_ORIGINAL, m, penalizerModifier, Grader421.USER_WEIGHT);
		byTimeGraderSecond = ByTimeGrader.using(Grader421.DEADLINE_SECOND_CHANCE, m, penalizerModifier,
				Grader421.USER_WEIGHT);
	}

	@Override
	public MarksTree grade(GitHubUsername author, GitFileSystemHistory data) throws IOException {
		final ImmutableSortedSet<Instant> timestampsOriginal = ByTimeGrader.getTimestamps(data,
				Grader421.DEADLINE_ORIGINAL.toInstant(), Grader421.CAP_ORIGINAL);
		final ImmutableSortedSet<Instant> timestampsSecond = ByTimeGrader.getTimestamps(data,
				Grader421.DEADLINE_SECOND_CHANCE.toInstant(), Instant.MAX);
		final PreparedGrader<RuntimeException> preparedOld = byTimeGraderOld.prepared(author, data);
		final PreparedGrader<RuntimeException> preparedSecond = byTimeGraderSecond.prepared(author, data);

		final ImmutableBiMap.Builder<Criterion, MarksTree> byTimeBuilder = ImmutableBiMap.builder();
		for (Instant capO : timestampsOriginal) {
			for (Instant capS : timestampsSecond) {
				final MarksTree old = preparedOld.grade(capO);
				final Path javaOld = java();

				final MarksTree second = preparedSecond.grade(capS);
				final Path javaSecond = java();
				final int diff = diff(javaOld, javaSecond);
				final double propOld = Double.max(20d, diff) / 20d;
				final String commentDiff = "Diff between '%s' and '%s' (%s ≠ lines / 20).".formatted(javaOld,
						javaSecond, propOld);

				final MarksTree merged = MarksTree.composite(
						ImmutableMap.of(C_DIFF, Mark.given(propOld, commentDiff), C_OLD, old, C_SECOND, second));

				byTimeBuilder.put(Criterion.given(preparedOld.asString(capO) + "; " + preparedSecond.asString(capS)),
						merged);
			}
		}
		final ImmutableBiMap<Criterion, MarksTree> byTime = byTimeBuilder.build();

		final MarksTree byTimeGrade;
		if (byTime.isEmpty()) {
			byTimeGrade = Mark.zero(String.format("No commit found%s", preparedSecond.getCommentGeneralCapped()));
		} else {
			byTimeGrade = MarksTree.composite(byTime);
		}

		return byTimeGrade;
	}

	private Path java() throws IOException {
		final ImmutableMap<Path, MarksTree> gradedProjectsOld = m.getGradedProjects();
		final Path oldPath = Iterables.getOnlyElement(gradedProjectsOld.keySet());
		final ImmutableSet<Path> javas = Files.find(oldPath, Integer.MAX_VALUE, (p, a) -> matches(p))
				.collect(ImmutableSet.toImmutableSet());
		final Path javaOld = Iterables.getOnlyElement(javas);
		return javaOld;
	}

	private boolean matches(Path p) {
		final Pattern pattern = Pattern.compile("implements([\\v\\h])+DiceRoller");
		verify(pattern.matcher("implements DiceRoller").find());
		verify(pattern.matcher("implements\n \t DiceRoller").find());
		verify(pattern.matcher("implements  DiceRoller").find());
		verify(!pattern.matcher("implements CyclicDiceRoller").find());
		verify(!pattern.matcher("implementsDiceRoller").find());
		return p.getFileName() != null && p.getFileName().toString().endsWith(".java")
				&& pattern.matcher(IO_UNCHECKER.getUsing(() -> Files.readString(p))).find();
	}

	private int diff(Path javaOld, Path javaSecond) {
		final ImmutableSet<String> contentOld = ImmutableSet
				.copyOf(IO_UNCHECKER.getUsing(() -> Files.readAllLines(javaOld)));
		final ImmutableSet<String> contentSecond = ImmutableSet
				.copyOf(IO_UNCHECKER.getUsing(() -> Files.readAllLines(javaSecond)));
		return Sets.symmetricDifference(contentOld, contentSecond).size();
	}

	@Override
	public GradeAggregator getAggregator() {
		final GradeAggregator oldAg = byTimeGraderOld.getPenalizedAggregator();
		final GradeAggregator secondAg = byTimeGraderSecond.getPenalizedAggregator();
		return GradeAggregator.parametric(C_OLD, C_DIFF, oldAg, secondAg);
	}

}