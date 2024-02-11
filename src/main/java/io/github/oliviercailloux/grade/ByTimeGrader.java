package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Verify.verify;

import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Maps;
import com.google.common.graph.Graph;
import io.github.oliviercailloux.git.filter.GitFilteringFs;
import io.github.oliviercailloux.git.filter.GitHistorySimple;
import io.github.oliviercailloux.git.github.model.GitHubUsername;
import io.github.oliviercailloux.gitjfs.Commit;
import io.github.oliviercailloux.gitjfs.GitPathRootRef;
import io.github.oliviercailloux.gitjfs.GitPathRootSha;
import io.github.oliviercailloux.gitjfs.GitPathRootShaCached;
import io.github.oliviercailloux.jaris.collections.CollectionUtils;
import io.github.oliviercailloux.jaris.throwing.TOptional;
import io.github.oliviercailloux.javagrade.testers.JavaMarkHelper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ByTimeGrader<X extends Exception> implements Grader<X> {

  public static class PreparedGrader<X extends Exception> {
    private final GitHistorySimple whole;
    private GitFsGrader<X> grader;
    private GitHubUsername author;
    private final GradeModifier penalizerModifier;
    private String commentGeneralCapped;
    private ZonedDateTime deadline;

    public PreparedGrader(GitFsGrader<X> grader, GitHubUsername author, GradeModifier modifier,
        GitHistorySimple whole, String commentGeneralCapped, ZonedDateTime deadline) {
      this.grader = grader;
      this.author = author;
      this.penalizerModifier = modifier;
      this.whole = checkNotNull(whole);
      this.commentGeneralCapped = commentGeneralCapped;
      this.deadline = deadline;
    }

    public MarksTree grade(Instant timeCap) throws X {
      try {
        final GitHistorySimple capped = whole.filtered(i -> !i.isAfter(timeCap));

        final MarksTree grade = grader.grade(capped);

        final Mark userGrade = DeadlineGrader.getUsernameGrade(whole, author).asNew();
        final MarksTree gradeWithUser =
            MarksTree.composite(ImmutableMap.of(C_USER_NAME, userGrade, C_GRADE, grade));

        return penalizerModifier.modify(gradeWithUser, timeCap);
      } catch (IOException e) {
        throw new IllegalStateException(e);
      }
    }

    public Criterion asCrit(Instant i) {
      return Criterion.given(asString(i));
    }

    public String asString(Instant i) {
      final String cappingAt = i.equals(Instant.MAX) ? "No capping"
          : i.equals(Instant.MIN) ? "Capping at MIN"
              : ("Capping at " + i.atZone(deadline.getZone()).toString());
      return cappingAt + commentGeneralCapped;
    }

    public String getCommentGeneralCapped() {
      return commentGeneralCapped;
    }

    public GitHistorySimple getWhole() {
      return whole;
    }
  }

  @SuppressWarnings("unused")
  private static final Logger LOGGER = LoggerFactory.getLogger(ByTimeGrader.class);

  private static final boolean IGNORE_FROM_GITHUB = true;

  public static final Criterion C_USER_NAME = Criterion.given("user.name");

  public static final Criterion C_GRADE = Criterion.given("Grade");

  public static <X extends Exception> ByTimeGrader<X> using(ZonedDateTime deadline,
      GitFsGrader<X> grader, GradeModifier penalizerModifier, double userGradeWeight) {
    return new ByTimeGrader<>(deadline, grader, penalizerModifier, userGradeWeight);
  }

  private final ZonedDateTime deadline;
  private final GitFsGrader<X> grader;
  private final GradeModifier penalizerModifier;

  private final double userGradeWeight;

  private ByTimeGrader(ZonedDateTime deadline, GitFsGrader<X> grader,
      GradeModifier penalizerModifier, double userGradeWeight) {
    this.deadline = checkNotNull(deadline);
    this.grader = checkNotNull(grader);
    this.penalizerModifier = checkNotNull(penalizerModifier);
    this.userGradeWeight = userGradeWeight;
  }

  @Override
  public MarksTree grade(GitHubUsername author, GitHistorySimple history) throws X {
    try {
      return gradeExc(author, history);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private MarksTree gradeExc(GitHubUsername author, GitHistorySimple history)
      throws IOException, X {
    final PreparedGrader<X> preparedGrader = prepared(author, history);

    final ImmutableSortedSet<Instant> consideredTimestamps =
        getTimestamps(preparedGrader.getWhole(), deadline.toInstant(), Instant.MAX);

    final ImmutableBiMap.Builder<Instant, MarksTree> byTimeBuilder = ImmutableBiMap.builder();
    for (Instant timeCap : consideredTimestamps) {
      final MarksTree perhapsPenalized = preparedGrader.grade(timeCap);
      byTimeBuilder.put(timeCap, perhapsPenalized);
    }
    final ImmutableBiMap<Instant, MarksTree> byTime = byTimeBuilder.build();

    final MarksTree byTimeGrade;
    if (byTime.isEmpty()) {
      byTimeGrade =
          Mark.zero(String.format("No commit found%s", preparedGrader.getCommentGeneralCapped()));
    } else {
      final ImmutableMap<Criterion, MarksTree> subsByTime = byTime.keySet().stream()
          .collect(ImmutableMap.toImmutableMap(i -> preparedGrader.asCrit(i), byTime::get));
      byTimeGrade = MarksTree.composite(subsByTime);
    }
    return byTimeGrade;
  }

  public PreparedGrader<X> prepared(GitHubUsername author, GitHistorySimple history)
      throws IOException {
    checkTimes(history, deadline.toInstant());
    final Optional<Instant> earliestTimeCommitByGitHub = earliestTimeCommitByGitHub(history);
    final GitHistorySimple beforeCommitByGitHub = TOptional.wrapping(earliestTimeCommitByGitHub)
        .map(t -> history.filtered(i -> i.isBefore(t))).orElse(history);
    final String commentGeneralCapped =
        earliestTimeCommitByGitHub.map(t -> "; ignored commits after "
            + t.atZone(deadline.getZone()).toString() + ", sent by GitHub").orElse("");

    final PreparedGrader<X> preparedGrader = new PreparedGrader<>(grader, author, penalizerModifier,
        beforeCommitByGitHub, commentGeneralCapped, deadline);
    return preparedGrader;
  }

  public static Optional<Instant> earliestTimeCommitByGitHub(GitHistorySimple history) {
    final Graph<GitPathRootShaCached> graph = history.graph();
    LOGGER.debug("Found {} commits (total).", graph.nodes().size());
    final ImmutableSet<Commit> commits = graph.nodes().stream().map(GitPathRootShaCached::getCommit)
        .collect(ImmutableSet.toImmutableSet());
    /* GitHub creates the very first commit when importing from a template. */
    final Optional<Instant> earliestTimeCommitByGitHub;
    if (IGNORE_FROM_GITHUB) {
      /*
       * Not sure why but the procedure here seems to keep commits by github that are not roots, and
       * get earliest timestamp.
       */
      final ImmutableSet<Commit> roots = history.roots().stream()
          .map(GitPathRootShaCached::getCommit).collect(ImmutableSet.toImmutableSet());
      final ImmutableSet<Commit> kept = commits.stream().filter(JavaMarkHelper::committerIsGitHub)
          .filter(c -> !roots.contains(c)).collect(ImmutableSet.toImmutableSet());
      final ImmutableSet<Instant> timestamps = kept.stream().map(Commit::id)
          .map(history::getTimestamp).collect(ImmutableSet.toImmutableSet());
      earliestTimeCommitByGitHub = timestamps.stream().min(Comparator.naturalOrder());
    } else {
      earliestTimeCommitByGitHub = Optional.empty();
    }
    return earliestTimeCommitByGitHub;
  }

  @Override
  public GradeAggregator getAggregator() {
    final GradeAggregator penalized = getPenalizedAggregator();
    final GradeAggregator maxAmongAttempts = GradeAggregator.max(penalized);
    return maxAmongAttempts;
  }

  public GradeAggregator getPenalizedAggregator() {
    final GradeAggregator main = getUserNamedAggregator(grader, userGradeWeight);
    final GradeAggregator penalized;
    final boolean withTimePenalty = penalizerModifier instanceof GradePenalizer;
    if (withTimePenalty) {
      penalized =
          GradeAggregator.parametric(GradePenalizer.C_MAIN, GradePenalizer.C_LATENESS, main);
    } else {
      penalized = main;
    }
    return penalized;
  }

  public static Instant cappedAt(GitHistorySimple capped) {
    // final ImmutableSet<GitPathRoot> leaves = IO_UNCHECKER.getUsing(capped::getRefs).stream()
    // .filter(n ->
    // capped.getGraph().successors(n).isEmpty()).collect(ImmutableSet.toImmutableSet());
    final ImmutableSet<GitPathRootShaCached> leaves = capped.leaves();
    final Instant timeCap = leaves.stream().map(r -> capped.getTimestamp(r))
        .max(Comparator.naturalOrder()).orElseThrow();
    return timeCap;
  }

  public static GitPathRootSha last(GitHistorySimple data) {
    // final ImmutableSet<GitPathRoot> leaves =
    // IO_UNCHECKER.getUsing(data::getRefs).stream()
    // .filter(n ->
    // data.getGraph().successors(n).isEmpty()).collect(ImmutableSet.toImmutableSet());
    final ImmutableSet<GitPathRootShaCached> leaves = data.leaves();
    final Comparator<GitPathRootShaCached> byDate = Comparator.comparing(r -> data.getTimestamp(r));
    final ImmutableSortedSet<GitPathRootShaCached> sortedLeaves =
        ImmutableSortedSet.copyOf(byDate, leaves);
    final GitPathRootSha leaf = sortedLeaves.last();
    return leaf;
  }

  private static GradeAggregator getUserNamedAggregator(GitFsGrader<?> grader,
      double userGradeWeight) {
    final GradeAggregator basis = grader.getAggregator();
    return GradeAggregator.staticAggregator(ImmutableMap.of(ByTimeGrader.C_USER_NAME,
        userGradeWeight, ByTimeGrader.C_GRADE, 1d - userGradeWeight),
        ImmutableMap.of(ByTimeGrader.C_GRADE, basis));
  }

  private static void checkTimes(GitHistorySimple history, Instant deadline) throws IOException {
    /*
     * Check whether has put late branches on every ending commits: some leaf has a pointer that is
     * not main or master. And it is late. OR rather simply use commit times and try to detect
     * inconsistencies: commit time on time but push date late.
     */
    final ImmutableSet<GitPathRootRef> refs = history.fs().refs();
    final ImmutableMap<GitPathRootRef, Instant> commitDates =
        CollectionUtils.toMap(refs, r -> r.getCommit().committerDate().toInstant());
    final ImmutableMap<GitPathRootRef, Instant> pushDates = CollectionUtils.toMap(refs,
        r -> history.getTimestamps().getOrDefault(r.getCommit().id(), Instant.MIN));
    final Map<GitPathRootRef, Instant> pushDatesLate =
        Maps.filterValues(pushDates, i -> i.isAfter(deadline));
    final Map<GitPathRootRef, Instant> commitsOnTime =
        Maps.filterValues(commitDates, i -> !i.isAfter(deadline));

    final Map<GitPathRootRef, Instant> contradictory =
        Maps.filterKeys(pushDatesLate, r -> commitsOnTime.containsKey(r));
    if (!contradictory.isEmpty()) {
      LOGGER.info("Commit times: {}.", commitDates);
      LOGGER.info("Push times: {}.", pushDates);
      LOGGER.info("Pushed late but committed on time: {}; commit times {}.", contradictory,
          Maps.filterKeys(commitsOnTime, r -> pushDatesLate.containsKey(r)));
    }
  }

  /**
   * Note that according to a note that I do not understand any more: Considers as “commit time”
   * every times given by the git history AND by the push dates (something may be pushed at some
   * date that does not appear in the git history, as it may have being overwritten by later
   * commits: in case the commits were all pushed at some time but a branch was later put on some
   * commit not ending the series, for example).
   *
   * @param history
   * @return the latest commit time that is on time, that is, the latest commit time within [MIN,
   *         deadline], if it exists, and the late commit times, that is, every times of commits
   *         falling in the range (deadline, max].
   */
  public static ImmutableSortedSet<Instant> getTimestamps(GitHistorySimple history,
      Instant deadline, Instant max) {
    final ImmutableSortedSet<Instant> timestamps = history.getTimestamps().values().stream()
        .collect(ImmutableSortedSet.toImmutableSortedSet(Comparator.naturalOrder()));
    final Instant latestCommitTimeOnTime =
        Optional.ofNullable(timestamps.floor(deadline)).orElse(Instant.MIN);
    /*
     * TODO We occasionally want to override this and return slightly more backwards in time when
     * wanting to grade two attempts of a given student, in case of a double grading.
     */
    // final Instant overridingDeadline =
    // LocalDateTime.parse("2023-04-20T23:59:59").atZone(ZoneId.of("Europe/Paris"))
    // .toInstant();
    // final Instant latestCommitTimeOnTime =
    // Optional.ofNullable(timestamps.floor(overridingDeadline))
    // .orElse(Instant.MIN);
    final ImmutableSortedSet<Instant> consideredTimestamps =
        timestamps.subSet(latestCommitTimeOnTime, true, max, true);
    LOGGER.debug("From history {} and {} with deadline {} and max {}, using latest {}: {}.",
        history, timestamps, deadline, max, max, latestCommitTimeOnTime, consideredTimestamps);
    verify(consideredTimestamps.isEmpty() == history.graph().nodes().isEmpty());
    verify(consideredTimestamps.isEmpty() || !consideredTimestamps.last().isAfter(max));
    return consideredTimestamps;
  }

  public static ImmutableSet<GitHistorySimple> getCapped(GitHistorySimple history, Instant deadline,
      Instant max) {
    return getTimestamps(history, deadline, max).stream().map(c -> cap(history, c))
        .collect(ImmutableSet.toImmutableSet());
  }

  private static GitHistorySimple cap(GitHistorySimple history, Instant cap) {
    try {
      final GitFilteringFs filteredFs =
          GitFilteringFs.filter(history.fs(), c -> !history.getTimestamp(c.id()).isAfter(cap));
      final GitHistorySimple filtered =
          GitHistorySimple.create(filteredFs, history.getTimestamps());
      final Instant last = ByTimeGrader.cappedAt(filtered);
      verify(!last.isAfter(cap));
      return filtered;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
