package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.MoreObjects;
import java.io.Serializable;
import java.util.Objects;

@SuppressWarnings("serial")
public class SubMarksTree implements Serializable {

  public static SubMarksTree given(Criterion criterion, MarksTree grade) {
    return new SubMarksTree(criterion, grade);
  }

  private final Criterion criterion;
  private final MarksTree marksTree;

  protected SubMarksTree(Criterion criterion, MarksTree grade) {
    this.criterion = checkNotNull(criterion);
    this.marksTree = checkNotNull(grade);
  }

  public Criterion getCriterion() {
    return criterion;
  }

  public MarksTree getMarksTree() {
    return marksTree;
  }

  @Override
  public boolean equals(Object o2) {
    if (!(o2 instanceof SubMarksTree)) {
      return false;
    }
    final SubMarksTree t2 = (SubMarksTree) o2;
    return criterion.equals(t2.criterion) && marksTree.equals(t2.marksTree);
  }

  @Override
  public int hashCode() {
    return Objects.hash(criterion, marksTree);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("criterion", criterion).add("grade", marksTree)
        .toString();
  }
}
