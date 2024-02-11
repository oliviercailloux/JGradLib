package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Verify.verify;
import static io.github.oliviercailloux.jaris.exceptions.Unchecker.IO_UNCHECKER;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import io.github.oliviercailloux.git.filter.GitHistorySimple;
import io.github.oliviercailloux.git.github.model.GitHubUsername;
import io.github.oliviercailloux.gitjfs.GitPathRootSha;
import io.github.oliviercailloux.grade.DeadlineGrader.LinearPenalizer;
import io.github.oliviercailloux.jaris.throwing.TOptional;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DoubleGrader implements Grader<IOException> {
  private static record Cappings (Instant cappedO, Instant cappedS) {
  }

  @SuppressWarnings("unused")
  private static final Logger LOGGER = LoggerFactory.getLogger(DoubleGrader.class);

  private static final Criterion C_DIFF = Criterion.given("diff");
  private static final Criterion C_OLD = Criterion.given("old");
  private static final Criterion C_SECOND = Criterion.given("second");
  private final ZoneId zone;
  private final ComplexGrader<RuntimeException> gO;
  private final ComplexGrader<RuntimeException> gS;

  private Instant deadlineO;

  private Instant deadlineS;

  private Instant capO;

  public DoubleGrader(PathGrader<RuntimeException> m, Instant deadlineO, Instant deadlineS,
      ZoneId zone, Instant capO, double userWeight) {
    this.deadlineO = deadlineO;
    this.deadlineS = deadlineS;
    this.capO = capO;
    final GitFsGraderUsingLast<RuntimeException> gitG = GitFsGraderUsingLast.using(m);
    final LinearPenalizer l = LinearPenalizer.proportionalToLateness(Duration.ofMinutes(5));
    {
      final GradePenalizer pO = GradePenalizer.using(l, deadlineO);
      gO = ComplexGrader.using(gitG, pO, userWeight);
    }
    {
      final GradePenalizer pS = GradePenalizer.using(l, deadlineS);
      gS = ComplexGrader.using(gitG, pS, userWeight);
    }
    this.zone = zone;
  }

  @Override
  public MarksTree grade(GitHubUsername author, GitHistorySimple data) throws IOException {
    final Optional<Instant> earliestTimeCommitByGitHub;
    final GitHistorySimple beforeCommitByGitHub;
    try {
      earliestTimeCommitByGitHub = ByTimeGrader.earliestTimeCommitByGitHub(data);
      LOGGER.debug("Earliest: {}.", earliestTimeCommitByGitHub);
      beforeCommitByGitHub = TOptional.wrapping(earliestTimeCommitByGitHub)
          .map(t -> data.filtered(i -> i.isBefore(t))).orElse(data);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
    final String commentGeneralCapped = earliestTimeCommitByGitHub
        .map(t -> "; ignored commits after " + t.atZone(zone).toString() + ", sent by GitHub")
        .orElse("");

    final ImmutableSet<GitHistorySimple> cappedsOriginal =
        ByTimeGrader.getCapped(beforeCommitByGitHub, deadlineO, capO);
    final ImmutableSet<GitHistorySimple> cappedsSecond =
        ByTimeGrader.getCapped(beforeCommitByGitHub, deadlineS, Instant.MAX);

    /*
     * To check while building that criteria are unique (for failing faster), as this is required
     * later.
     */
    final Set<Criterion> criteria = Sets.newLinkedHashSet();
    /*
     * Probably possible to get rid of this with a more elegant construction, but as a workaround we
     * use this to avoid two identical criteria.
     */
    final Set<Cappings> allCappings = Sets.newLinkedHashSet();
    final ImmutableSet.Builder<SubMarksTree> cappedBuilder = ImmutableSet.builder();
    for (GitHistorySimple cappedO : cappedsOriginal) {
      /*
       * Need to consider also the identical “second chance”, which may give more points (if others
       * have diffs, then only this one will have the original grade minus the penalty). Otherwise
       * attempt may be detrimental.
       *
       * Improvement: consider summing the latenesses.
       */
      final ImmutableSet.Builder<GitHistorySimple> cappedOAndS = ImmutableSet.builder();
      cappedOAndS.add(cappedO);
      cappedOAndS.addAll(cappedsSecond);
      for (GitHistorySimple cappedS : cappedOAndS.build()) {
        final MarksTree old = gO.grade(author, cappedO);
        final MarksTree second = gS.grade(author, cappedS);
        final GitPathRootSha lastO = ByTimeGrader.last(cappedO);
        final GitPathRootSha lastS = ByTimeGrader.last(cappedS);
        final Instant cappedIO = ByTimeGrader.cappedAt(cappedO);
        final Instant cappedIS = ByTimeGrader.cappedAt(cappedS);
        final Cappings cappings = new Cappings(cappedIO, cappedIS);
        if (allCappings.contains(cappings)) {
          LOGGER.debug("Be there, done that already: {}.", cappings);
          continue;
        }
        verify(!cappedIO.isAfter(capO), cappedIO.toString());
        final ImmutableSet<Path> javasOld =
            Files.find(lastO, Integer.MAX_VALUE, (p, a) -> matches(p))
                .collect(ImmutableSet.toImmutableSet());
        final ImmutableSet<Path> javasSecond =
            Files.find(lastS, Integer.MAX_VALUE, (p, a) -> matches(p))
                .collect(ImmutableSet.toImmutableSet());
        final Mark diffMark;
        checkState(javasOld.size() <= 1, javasOld);
        if (javasOld.size() == 1 && javasSecond.size() == 1) {
          final Path javaOld = Iterables.getOnlyElement(javasOld);
          final Path javaSecond = Iterables.getOnlyElement(javasSecond);
          final int diff = diff(javaOld, javaSecond);
          final double propOld = Double.min(15d, diff) / 15d;
          final String commentDiff =
              "Diff between '%s' and '%s' (%s ≠ lines / 20)".formatted(javaOld, javaSecond, diff);
          diffMark = Mark.given(propOld, commentDiff);
        } else {
          diffMark = Mark.one("Found multiple (or no) files to compare, could not compute diff: "
              + "original %s and second %s".formatted(javasOld, javasSecond));
        }

        final MarksTree merged =
            MarksTree.composite(ImmutableMap.of(C_DIFF, diffMark, C_OLD, old, C_SECOND, second));

        final String cappingAtO = "Capping original at " + cappedIO.atZone(zone).toString() + "; ";
        // final String cappingAtS = cappedsSecond.size() == 1 ? "no capping (second)"
        // : ("capping second at " + cappedIS.atZone(zone).toString());
        final String cappingAtS = "capping second at " + cappedIS.atZone(zone).toString();
        final String comment = cappingAtO + cappingAtS + commentGeneralCapped;
        final Criterion crit = Criterion.given(comment);
        LOGGER.debug("Adding crit {}.", crit);
        final boolean isNew = criteria.add(crit);
        verify(isNew, crit.toString());
        allCappings.add(cappings);
        cappedBuilder.add(SubMarksTree.given(crit, merged));
      }
    }

    final ImmutableSet<SubMarksTree> cappedGrades = cappedBuilder.build();
    final MarksTree byTimeGrade;
    if (cappedGrades.isEmpty()) {
      byTimeGrade = Mark.zero(String.format("No commit found%s", commentGeneralCapped));
    } else {
      byTimeGrade = MarksTree.composite(cappedGrades);
    }
    return byTimeGrade;
  }

  private boolean matches(Path p) {
    // final Pattern pattern = Pattern.compile("StringManiper ");
    // return p.getFileName() != null && p.getFileName().toString().endsWith(".java")
    // && pattern.matcher(IO_UNCHECKER.getUsing(() -> Files.readString(p))).find();
    return String.valueOf(p.getFileName()).equals("StringManiper.java");
  }

  private int diff(Path javaOld, Path javaSecond) {
    final ImmutableSet<String> contentOld =
        ImmutableSet.copyOf(IO_UNCHECKER.getUsing(() -> Files.readAllLines(javaOld)));
    final ImmutableSet<String> contentSecond =
        ImmutableSet.copyOf(IO_UNCHECKER.getUsing(() -> Files.readAllLines(javaSecond)));
    final ImmutableSet<String> diff =
        Sets.symmetricDifference(contentOld, contentSecond).immutableCopy();
    final int size = diff.size();
    LOGGER.debug("Content old: {}, content second: {}, diff: {}, size: {}.", contentOld,
        contentSecond, diff, size);
    return size;
  }

  @Override
  public GradeAggregator getAggregator() {
    final GradeAggregator oldAg = gO.getAggregator();
    final GradeAggregator secondAg = gS.getAggregator();
    verify(oldAg.equals(secondAg));
    final GradeAggregator oldDiff = GradeAggregator.parametric(C_OLD, C_DIFF,
        ImmutableMap.of(C_OLD, oldAg, C_DIFF, GradeAggregator.TRIVIAL), secondAg);
    return GradeAggregator.max(oldDiff);
  }
}
