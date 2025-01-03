package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.github.oliviercailloux.grade.IGrade.CriteriaPath;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

/**
 * Set as public as a temporary workaround for Json serialization.
 */
@SuppressWarnings("serial")
public class CompositeMarksTree implements MarksTree {

  public static CompositeMarksTree givenGrades(Map<Criterion, ? extends MarksTree> subGrades) {
    return new CompositeMarksTree(subGrades.keySet().stream().collect(
        ImmutableMap.toImmutableMap(c -> c, c -> SubMarksTree.given(c, subGrades.get(c)))));
  }

  public static CompositeMarksTree givenSubGrades(Map<Criterion, SubMarksTree> subGrades) {
    return new CompositeMarksTree(subGrades);
  }

  public static CompositeMarksTree givenSubGrades(Set<? extends SubMarksTree> subGrades) {
    return new CompositeMarksTree(subGrades.stream()
        .collect(ImmutableMap.toImmutableMap(SubMarksTree::getCriterion, s -> s)));
  }

  /**
   * not empty; values contain either CompositeGrade or Mark instances
   */
  private final ImmutableMap<Criterion, SubMarksTree> subGrades;

  private CompositeMarksTree(Map<Criterion, SubMarksTree> subGrades) {
    this.subGrades = ImmutableMap.copyOf(subGrades);
    checkArgument(!subGrades.isEmpty());
    checkArgument(
        subGrades.keySet().stream().allMatch(c -> subGrades.get(c).getCriterion().equals(c)));
  }

  @Override
  public boolean isMark() {
    return false;
  }

  @Override
  public boolean isComposite() {
    return true;
  }

  @Override
  public ImmutableSet<Criterion> getCriteria() {
    return subGrades.keySet();
  }

  @Override
  public MarksTree getTree(Criterion criterion) {
    return getSubGrade(criterion).getMarksTree();
  }

  private SubMarksTree getSubGrade(Criterion criterion) {
    if (!subGrades.containsKey(criterion)) {
      throw new NoSuchElementException();
    }
    return subGrades.get(criterion);
  }

  @Override
  public ImmutableSet<CriteriaPath> getPathsToMarks() {
    final ImmutableSet.Builder<CriteriaPath> builder = ImmutableSet.builder();
    for (Criterion criterion : subGrades.keySet()) {
      getTree(criterion).getPathsToMarks().stream().map(p -> p.withPrefix(criterion))
          .forEach(builder::add);
    }
    return builder.build();
  }

  @Override
  public boolean hasPath(CriteriaPath path) {
    if (path.isRoot()) {
      return true;
    }
    final Criterion next = path.getHead();
    if (!getCriteria().contains(next)) {
      return false;
    }
    return getTree(next).hasPath(path.withoutHead());
  }

  @Override
  public MarksTree getTree(CriteriaPath path) {
    if (path.isRoot()) {
      return this;
    }
    return getTree(path.getHead()).getTree(path.withoutHead());
  }

  @Override
  public Mark getMark(CriteriaPath path) {
    checkArgument(!path.isRoot());
    return getTree(path.getHead()).getMark(path.withoutHead());
  }

  @Override
  public boolean equals(Object o2) {
    if (!(o2 instanceof CompositeMarksTree)) {
      return false;
    }
    final CompositeMarksTree t2 = (CompositeMarksTree) o2;
    return subGrades.equals(t2.subGrades);
  }

  @Override
  public int hashCode() {
    return Objects.hash(subGrades);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("subGrades", subGrades).toString();
  }
}
