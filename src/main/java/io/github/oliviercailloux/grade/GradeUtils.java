package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Verify.verify;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.graph.ValueGraph;
import io.github.oliviercailloux.gitjfs.Commit;
import io.github.oliviercailloux.gitjfs.GitPathRoot;
import io.github.oliviercailloux.gitjfs.GitPathRootRef;
import io.github.oliviercailloux.gitjfs.GitPathRootSha;
import io.github.oliviercailloux.gitjfs.GitPathRootShaCached;
import io.github.oliviercailloux.grade.IGrade.CriteriaPath;
import io.github.oliviercailloux.grade.WeightingGrade.WeightedGrade;
import io.github.oliviercailloux.grade.old.GradeStructure;
import io.github.oliviercailloux.jaris.exceptions.CheckedStream;
import io.github.oliviercailloux.jaris.throwing.TFunction;
import io.github.oliviercailloux.jaris.throwing.TPredicate;
import java.io.IOException;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import org.eclipse.jgit.lib.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GradeUtils {
  @SuppressWarnings("unused")
  private static final Logger LOGGER = LoggerFactory.getLogger(GradeUtils.class);

  /**
   * Is a weighting grade iff the given root has at least one successor in the tree or the grade for
   * the root is a weighting grade.
   */
  public static IGrade toGrade(Criterion root, ValueGraph<Criterion, Double> staticTree,
      Map<Criterion, IGrade> leafGrades) {
    final Set<Criterion> successors = staticTree.successors(root);
    if (successors.isEmpty()) {
      return leafGrades.get(root);
    }
    final ImmutableSet<CriterionGradeWeight> cgws = successors.stream()
        .map(c -> CriterionGradeWeight.from(c, toGrade(c, staticTree, leafGrades),
            staticTree.edgeValue(root, c).get()))
        .collect(ImmutableSet.toImmutableSet());
    return WeightingGrade.from(cgws);
  }

  static ImmutableMap<Criterion, IGrade> withUpdatedEntry(Map<Criterion, IGrade> subGrades,
      Criterion criterion, IGrade newSubGrade) {
    checkArgument(subGrades.keySet().contains(criterion));

    return subGrades.entrySet().stream().collect(ImmutableMap.toImmutableMap(Map.Entry::getKey,
        e -> e.getKey().equals(criterion) ? newSubGrade : e.getValue()));
  }

  public static IGrade toOwa(IGrade grade, List<Double> increasingWeights) {
    if (grade.getPoints() == 0d) {
      return grade;
    }

    final GradeStructure tree = grade.toTree();

    final ImmutableSortedSet<CriteriaPath> increasingPaths = ImmutableSortedSet
        .copyOf(Comparator.comparing((CriteriaPath p) -> grade.getMark(p).getPoints())
            .thenComparing(p -> p.toString()), tree.getLeaves());
    LOGGER.debug("Increasing: {}.", increasingPaths);
    verify(increasingPaths.size() == increasingWeights.size(), grade.toString());

    final ImmutableMap.Builder<CriteriaPath, WeightedGrade> builder = ImmutableMap.builder();
    final Iterator<CriteriaPath> pathsIterator = increasingPaths.iterator();
    final Iterator<Double> weightsIterator = increasingWeights.iterator();
    while (pathsIterator.hasNext() || weightsIterator.hasNext()) {
      final CriteriaPath path = pathsIterator.next();
      builder.put(path, WeightedGrade.given(grade.getMark(path), weightsIterator.next()));
    }
    return WeightingGrade.from(builder.build()).withComment(grade.getComment());
  }

  public static boolean allAndSomeCommitMatch(Set<Commit> commits, Predicate<Commit> test) {
    return !commits.isEmpty() && commits.stream().allMatch(test);
  }

  public static Mark allAndSomePathsMatchCommit(Set<? extends GitPathRootShaCached> commits,
      Predicate<Commit> test) {
    final boolean pass =
        !commits.isEmpty() && commits.stream().map(p -> p.getCommit()).allMatch(test);
    return Mark.binary(pass);
  }

  public static Mark anyMatch(Set<GitPathRootShaCached> commits,
      TPredicate<? super GitPathRootShaCached, IOException> test) throws IOException {
    final boolean pass =
        CheckedStream.<GitPathRootShaCached, IOException>from(commits).anyMatch(test);
    return Mark.binary(pass);
  }

  public static Mark anyRefMatch(Set<GitPathRootRef> refs,
      TPredicate<GitPathRootRef, IOException> test) throws IOException {
    final boolean pass = CheckedStream.<GitPathRootRef, IOException>from(refs).anyMatch(test);
    return Mark.binary(pass);
  }

  public static IGrade getBestGrade(Set<GitPathRootShaCached> paths,
      TFunction<Optional<GitPathRootShaCached>, IGrade, IOException> grader, double bestPossible)
      throws IOException {
    final TFunction<Optional<GitPathRootShaCached>, Double, IOException> scorer =
        r -> grader.apply(r).getPoints();
    final Optional<GitPathRootShaCached> best =
        getCommitMaximizing(paths, r -> scorer.apply(Optional.of(r)), bestPossible);
    return grader.apply(best);
  }

  private static <FO extends Comparable<FO>> Optional<GitPathRootShaCached> getCommitMaximizing(
      Set<GitPathRootShaCached> paths, TFunction<GitPathRootShaCached, FO, IOException> scorer,
      FO bestPossible) throws IOException {
    /**
     * As grading sometimes takes a lot of time (e.g. 3 seconds to grade "eclipse-compile" because
     * it requires a find all, currently very slow), it is important to stop early if possible. In
     * the "Eclipse" case, the full search would require typically checking 6 commits, 3 seconds
     * each.
     */
    GitPathRootShaCached bestInput = null;
    FO bestScore = null;
    for (GitPathRootShaCached r : (Iterable<GitPathRootShaCached>) paths.stream()::iterator) {
      final FO score = scorer.apply(r);
      checkArgument(score != null);
      if (bestScore == null || bestScore.compareTo(score) < 0) {
        bestScore = score;
        bestInput = r;
      }
      LOGGER.debug("Considering {}, obtained score {}, current best {}.", r, score, bestScore);
      if (bestScore.equals(bestPossible)) {
        break;
      }
    }
    return Optional.ofNullable(bestInput);
  }

  public static ImmutableSet<GitPathRootRef> getRefsTo(Set<GitPathRootRef> refs,
      GitPathRootSha target) throws IOException {
    final ObjectId targetId = target.getStaticCommitId();
    final TPredicate<GitPathRootRef, IOException> rightTarget =
        GitGrader.Predicates.compose(GitPathRoot::getCommit, c -> c.id().equals(targetId));
    return CheckedStream.<GitPathRootRef, IOException>from(refs).filter(rightTarget)
        .collect(ImmutableSet.toImmutableSet());
  }
}
