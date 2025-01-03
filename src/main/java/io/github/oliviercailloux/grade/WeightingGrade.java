package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Verify.verify;

import com.google.common.base.MoreObjects;
import com.google.common.base.Verify;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.math.DoubleMath;
import io.github.oliviercailloux.grade.old.GradeStructure;
import io.github.oliviercailloux.grade.old.Mark;
import jakarta.json.bind.annotation.JsonbCreator;
import jakarta.json.bind.annotation.JsonbProperty;
import jakarta.json.bind.annotation.JsonbPropertyOrder;
import jakarta.json.bind.annotation.JsonbTransient;
import jakarta.json.bind.annotation.JsonbVisibility;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * containing positive points only and at one weights per sub-grade, at least one of which must be
 * strictly positive. May order the grades (we may not want all positive then all negative but
 * interleaving!). The positive weights are normalized internally. The negative weights must be in
 * [−1, 0). A negative weight represents the prop. of the total points that can be lost on the
 * corresponding criterion. Example: weight is −2/20, grade is 1 (means no penalty) or 0.5 (means
 * −1/20) or 0 (means −2/20). A sub grade in the map may be an AdditiveGrade (even if it’s a
 * penalty).
 *
 * This object authorizes zero weight because this can convey useful information. Assume a grade is
 * a weighted sum of two exercices, with a weight that depends on something that can become zero for
 * some students. Then the information of how good the second sub-grade is is useful, even if the
 * weight is zero. (I had a case where a student could re-do an exercice, but the second attempt
 * would have a low weight if the second attempt differed much from her first attempt, and possibly
 * being zero; to prevent students from simply submitting a friend’s solution instead of correcting
 * their own.) Another example is a test that shouldn’t count for points (for example, because the
 * wording indicates that this aspect will not be considered) but whose result is considered
 * informative feedback for the student anyway.
 *
 * {weights: {@code Map<CriterionAndPoints, Double>}} (non empty, all non null), this implementation
 * has only the (normalized) weights and the marks and a comment, and generates the points.
 *
 * As the penalty has an absolute meaning, it is necessary that this object knows the best possible
 * marks. As a convention, it is considered to be one for all sub-grades.
 *
 * Note that single children are not forbidden: this permits a node with two sub-criterion to remove
 * one without losing the remaining criterion.
 *
 * @author Olivier Cailloux
 *
 */
@JsonbPropertyOrder({"points", "comment", "subGrades"})
@JsonbVisibility(MethodVisibility.class)
public class WeightingGrade implements IGrade {
  @SuppressWarnings("unused")
  private static final Logger LOGGER = LoggerFactory.getLogger(WeightingGrade.class);

  public static class PathGradeWeight {
    public static PathGradeWeight given(CriteriaPath path, IGrade grade, double weight) {
      return new PathGradeWeight(path, grade, weight);
    }

    private final CriteriaPath path;
    private final IGrade grade;
    private final double weight;

    private PathGradeWeight(CriteriaPath path, IGrade grade, double weight) {
      this.path = checkNotNull(path);
      this.grade = checkNotNull(grade);
      this.weight = weight;
    }

    public CriteriaPath getPath() {
      return path;
    }

    public IGrade getGrade() {
      return grade;
    }

    public double getWeight() {
      return weight;
    }

    @Override
    public boolean equals(Object o2) {
      if (!(o2 instanceof WeightingGrade.PathGradeWeight)) {
        return false;
      }
      final WeightingGrade.PathGradeWeight t2 = (WeightingGrade.PathGradeWeight) o2;
      return path.equals(t2.path) && grade.equals(t2.grade) && weight == t2.weight;
    }

    @Override
    public int hashCode() {
      return Objects.hash(path, grade, weight);
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this).add("Path", path).add("Grade", grade)
          .add("Weight", weight).toString();
    }
  }

  public static class WeightedGrade {
    public static WeightedGrade given(IGrade grade, double weight) {
      if (grade instanceof Mark) {
        final Mark mark = (Mark) grade;
        return new WeightedMark(mark, weight);
      }
      return new WeightedGrade(grade, weight);
    }

    public static WeightedGrade given(Map<Criterion, WeightedGrade> subGrades) {
      final WeightingGrade aggregated = WeightingGrade.fromWeightedGrades(subGrades);
      return WeightedGrade.given(aggregated,
          subGrades.values().stream().mapToDouble(WeightedGrade::getWeight).sum());
    }

    private final IGrade grade;
    private final double weight;

    protected WeightedGrade(IGrade grade, double weight) {
      this.grade = checkNotNull(grade);
      this.weight = weight;
      checkArgument(Double.isFinite(weight));
      checkArgument(weight >= 0d);
    }

    public IGrade getGrade() {
      return grade;
    }

    public double getWeight() {
      return weight;
    }

    public double getAbsolutePoints() {
      return weight * grade.getPoints();
    }

    public WeightedMark getWeightedMark(CriteriaPath path) {
      final WeightedMark weightedMark = grade.getWeightedMark(path);
      return WeightedMark.given(weightedMark.getGrade(), weight * weightedMark.getWeight());
    }

    @Override
    public boolean equals(Object o2) {
      if (!(o2 instanceof WeightingGrade.WeightedGrade)) {
        return false;
      }
      final WeightingGrade.WeightedGrade t2 = (WeightingGrade.WeightedGrade) o2;
      return grade.equals(t2.grade) && weight == t2.weight;
    }

    @Override
    public int hashCode() {
      return Objects.hash(grade, weight);
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this).add("Grade", grade).add("Weight", weight).toString();
    }
  }

  public static class WeightedMark extends WeightedGrade {
    public static WeightedMark given(Mark mark, double weight) {
      return new WeightedMark(mark, weight);
    }

    private WeightedMark(Mark mark, double weight) {
      super(mark, weight);
    }

    @Override
    public Mark getGrade() {
      return (Mark) super.getGrade();
    }
  }

  /**
   * @param grades its key set iteration order is used to determine the order of the sub-grades.
   * @param weights must have the same keys as the grades (but the iteration order is not used).
   */
  public static WeightingGrade from(Map<Criterion, ? extends IGrade> grades,
      Map<Criterion, Double> weights) {
    return from(grades, weights, "");
  }

  public static WeightingGrade from(Map<Criterion, ? extends IGrade> grades,
      Map<Criterion, Double> weights, String comment) {
    return new WeightingGrade(grades, weights, comment);
  }

  public static WeightingGrade from(Collection<CriterionGradeWeight> grades) {
    return from(grades, "");
  }

  /**
   * @param grades its iteration order is used to determine the order of the sub-grades.
   */
  public static WeightingGrade from(Collection<CriterionGradeWeight> grades, String comment) {
    final ImmutableMap<Criterion, IGrade> gradesByCriterion = grades.stream()
        .collect(ImmutableMap.toImmutableMap((g) -> g.getCriterion(), (g) -> g.getGrade()));
    final ImmutableMap<Criterion, Double> weights = grades.stream()
        .collect(ImmutableMap.toImmutableMap((g) -> g.getCriterion(), (g) -> g.getWeight()));
    return new WeightingGrade(gradesByCriterion, weights, comment);
  }

  /**
   * @param grades each of these grades will have an absolute weight given by its weight
   *        divided by the sum of all weights ; non empty; keys must be unrelated (if one is parent
   *        of another entry there is no way to use both grades!)
   * @return a mark iff the map key set is the singleton ROOT and the (single) weighted grade is a
   *         weighted mark
   */
  public static IGrade from(Map<CriteriaPath, WeightedGrade> grades) {
    return from(grades, false);
  }

  private static IGrade from(Map<CriteriaPath, WeightedGrade> grades, boolean changeZeroChildren) {
    checkArgument(!grades.isEmpty());

    final Map<CriteriaPath, WeightedGrade> modifiableGrades = new LinkedHashMap<>(grades);
    LOGGER.debug("Initialized modifiable as: {}.", modifiableGrades);

    final GradeStructure structure = GradeStructure.given(grades.keySet());
    checkArgument(structure.getLeaves().equals(grades.keySet()));

    /*
     * We will populate modifiable grades from “right to left”, from children nodes to parent nodes,
     * until reaching the root node. Note that we can’t simply stop when the map has only one
     * remaining entry: at some point there may remain a single key "[a/b/c]", for example.
     */
    while (!modifiableGrades.keySet().contains(CriteriaPath.ROOT)) {
      /*
       * We need to consider the highest depths first, because we want all siblings to be aggregated
       * already.
       */
      final CriteriaPath remainingPath =
          modifiableGrades.keySet().stream().max(Comparator.comparing(CriteriaPath::size)).get();
      LOGGER.debug("Considering {}.", remainingPath);
      verify(!remainingPath.isRoot());
      final CriteriaPath parent = remainingPath.withoutTail();
      verify(!modifiableGrades.containsKey(parent));
      final ImmutableSet<CriteriaPath> childrenPaths = structure.getSuccessorPaths(parent);
      {
        /* Modifiable grades has entries for all children nodes. */
        final ImmutableMap<Criterion, WeightedGrade> childrenWGrades = childrenPaths.stream()
            .collect(ImmutableMap.toImmutableMap(CriteriaPath::getTail, modifiableGrades::get));
        final boolean allZeroes = childrenWGrades.values().stream()
            .mapToDouble(WeightedGrade::getWeight).allMatch(w -> w == 0d);
        final ImmutableMap<Criterion, WeightedGrade> nonZeroesChildrenWGrades;
        if (allZeroes) {
          checkArgument(changeZeroChildren,
              "Path has only zero-weight children, but I can’t rectify it: " + parent);
          nonZeroesChildrenWGrades = ImmutableMap.copyOf(
              Maps.transformValues(childrenWGrades, g -> WeightedGrade.given(g.getGrade(), 1d)));
        } else {
          nonZeroesChildrenWGrades = childrenWGrades;
        }

        final WeightedGrade aggregated =
            WeightedGrade.given(fromWeightedGrades(nonZeroesChildrenWGrades),
                childrenWGrades.values().stream().mapToDouble(WeightedGrade::getWeight).sum());
        modifiableGrades.put(parent, aggregated);
        LOGGER.debug("Added to {} aggregated {}.", parent, aggregated);
      }
      childrenPaths.stream().forEach(modifiableGrades::remove);
    }

    verify(modifiableGrades.keySet().equals(ImmutableSet.of(CriteriaPath.ROOT)));

    final IGrade grade = Iterables.getOnlyElement(modifiableGrades.values()).getGrade();
    final ImmutableSet<CriteriaPath> expectedLeaves = grades.keySet().stream()
        .flatMap(
            p -> grades.get(p).getGrade().toTree().getLeaves().stream().map(l -> l.withPrefix(p)))
        .collect(ImmutableSet.toImmutableSet());
    verify(grade.toTree().getLeaves().equals(expectedLeaves));
    return grade;
  }

  /**
   * @param weightedGrades its key set iteration order is used to determine the order of the
   *        sub-grades.
   */
  public static WeightingGrade fromWeightedGrades(Map<Criterion, WeightedGrade> weightedGrades) {
    return from(Maps.toMap(weightedGrades.keySet(), c -> weightedGrades.get(c).getGrade()),
        Maps.toMap(weightedGrades.keySet(), c -> weightedGrades.get(c).getWeight()), "");
  }

  /**
   * @param grades its iteration order is used to determine the order of the sub-grades.
   */
  @JsonbCreator
  public static WeightingGrade fromList(
      @JsonbProperty("subGrades") List<CriterionGradeWeight> grades,
      @JsonbProperty("comment") String comment) {
    /*
     * The list type (rather than set) is required for json to deserialize in the right order.
     */
    return from(grades, comment);
  }

  public static WeightingGrade proportional(Criterion c1, IGrade g1, Criterion c2, IGrade g2) {
    return proportional(c1, g1, c2, g2, "");
  }

  public static WeightingGrade proportional(Criterion c1, IGrade g1, Criterion c2, IGrade g2,
      String comment) {
    return WeightingGrade.from(ImmutableMap.of(c1, g1, c2, g2), ImmutableMap.of(c1, 0.5d, c2, 0.5d),
        comment);
  }

  public static WeightingGrade proportional(Criterion c1, IGrade g1, Criterion c2, IGrade g2,
      Criterion c3, IGrade g3) {
    return proportional(c1, g1, c2, g2, c3, g3, "");
  }

  public static WeightingGrade proportional(Criterion c1, IGrade g1, Criterion c2, IGrade g2,
      Criterion c3, IGrade g3, String comment) {
    return WeightingGrade.from(ImmutableMap.of(c1, g1, c2, g2, c3, g3),
        ImmutableMap.of(c1, 1d / 3d, c2, 1d / 3d, c3, 1d / 3d), comment);
  }

  public static IGrade withZeroesRectified(Map<CriteriaPath, WeightedGrade> grades) {
    return from(grades, true);
  }

  private static final double MAX_MARK = 1d;

  /**
   * Not empty. This key set equals the key set of the weights.
   */
  private final ImmutableMap<Criterion, IGrade> subGrades;

  /**
   * The positive ones sum to one. No zero values (TODO not sure!).
   */
  private final ImmutableMap<Criterion, Double> weights;

  private final String comment;

  private WeightingGrade(Map<Criterion, ? extends IGrade> subGrades, Map<Criterion, Double> weights,
      String comment) {
    checkArgument(weights.values().stream().allMatch(d -> Double.isFinite(d)));
    checkArgument(weights.values().stream().anyMatch(d -> d > 0d),
        "Must have at least one non-zero weight.");
    checkArgument(
        subGrades.values().stream().allMatch(g -> 0d <= g.getPoints() && g.getPoints() <= 1d));
    checkArgument(subGrades.keySet().equals(weights.keySet()),
        String.format("Sub grades have keys: %s, weights have keys: %s, diff: %s",
            subGrades.keySet(), weights.keySet(),
            Sets.symmetricDifference(subGrades.keySet(), weights.keySet())));
    final double sumPosWeights =
        weights.values().stream().filter(d -> d > 0d).collect(Collectors.summingDouble(d -> d));
    verify(sumPosWeights > 0d);
    /* See JsonGradeTests#gradePrecisionRoundTrip. */
    final double effectiveNormalizer;
    if (DoubleMath.fuzzyEquals(1.0d, sumPosWeights, 1e-8d)) {
      effectiveNormalizer = 1.0d;
    } else {
      effectiveNormalizer = sumPosWeights;
    }
    /*
     * I iterate over the sub grades key set in order to guarantee iteration order of the weights
     * reflects the order of the sub-grades.
     */
    this.weights = subGrades.keySet().stream().collect(ImmutableMap.toImmutableMap(c -> c,
        c -> weights.get(c) > 0d ? weights.get(c) / effectiveNormalizer : weights.get(c)));
    this.subGrades = ImmutableMap.copyOf(subGrades);
    this.comment = checkNotNull(comment);
  }

  @Override
  public double getPoints() {
    final double positivePoints =
        weights.entrySet().stream().filter((e) -> e.getValue() > 0d).map(Entry::getKey).collect(
            Collectors.summingDouble((c) -> subGrades.get(c).getPoints() * weights.get(c)));
    final double negativePoints = weights.entrySet().stream().filter((e) -> e.getValue() < 0d)
        .map(Entry::getKey).collect(Collectors
            .summingDouble((c) -> (MAX_MARK - subGrades.get(c).getPoints()) * weights.get(c)));
    Verify.verify(negativePoints <= 0d);
    final double totalPoints = Math.max(0d, positivePoints + negativePoints);
    Verify.verify(0d <= totalPoints && totalPoints <= 1d);
    return totalPoints;
  }

  @Override
  public String getComment() {
    return comment;
  }

  @JsonbTransient
  @Override
  public ImmutableMap<Criterion, IGrade> getSubGrades() {
    return subGrades;
  }

  /**
   * @return iterates in the order of the sub-grades.
   */
  @Override
  @JsonbProperty("subGrades")
  public ImmutableSet<CriterionGradeWeight> getSubGradesAsSet() {
    return subGrades.keySet().stream()
        .map((c) -> CriterionGradeWeight.from(c, subGrades.get(c), weights.get(c)))
        .collect(ImmutableSet.toImmutableSet());
  }

  @JsonbTransient
  public ImmutableSet<PathGradeWeight> getAllSubGrades() {
    return toTree().getPaths().stream()
        .map(p -> PathGradeWeight.given(p, getGrade(p).get(), getWeight(p)))
        .collect(ImmutableSet.toImmutableSet());
  }

  /**
   * @return the weights, such that the positive weights sum to one, and not empty. Iterates in the
   *         order of the sub-grades.
   */
  @Override
  @JsonbTransient
  public ImmutableMap<Criterion, Double> getWeights() {
    return weights;
  }

  /**
   * 1 for a weighting grade whose subgrades are all marks.
   */
  @Override
  public IGrade limitedDepth(int depth) {
    checkArgument(depth >= 0);
    if (depth == 0) {
      return Mark.given(getPoints(), getComment());
    }
    return WeightingGrade.from(
        subGrades.keySet().stream().collect(
            ImmutableMap.toImmutableMap(c -> c, c -> subGrades.get(c).limitedDepth(depth - 1))),
        weights);
  }

  @Override
  public IGrade withComment(String newComment) {
    return new WeightingGrade(subGrades, weights, newComment);
  }

  @Override
  public WeightingGrade withSubGrade(Criterion criterion, IGrade newSubGrade) {
    return new WeightingGrade(GradeUtils.withUpdatedEntry(subGrades, criterion, newSubGrade),
        weights, comment);
  }

  @Override
  public boolean equals(Object o2) {
    if (!(o2 instanceof IGrade)) {
      return false;
    }
    IGrade g2 = (IGrade) o2;
    return getPoints() == g2.getPoints() && getComment().equals(g2.getComment())
        && getSubGrades().equals(g2.getSubGrades());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getPoints(), getComment(), getSubGrades());
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("points", getPoints()).add("comment", getComment())
        .add("subGrades", getSubGradesAsSet()).toString();
  }
}
