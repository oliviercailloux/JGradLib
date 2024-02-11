package io.github.oliviercailloux.grade;

import com.google.common.base.MoreObjects;
import java.util.Objects;

/**
 * Rejects nothing. Binds the result within [−1, 1].
 */
public final class AbsoluteAggregator implements PerCriterionWeighter {
  public static final AbsoluteAggregator INSTANCE = new AbsoluteAggregator();

  @Override
  public double weight(Criterion criterion) throws AggregatorException {
    return 1d;
  }

  @Override
  public boolean equals(Object o2) {
    if (!(o2 instanceof AbsoluteAggregator)) {
      return false;
    }
    return true;
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.getClass());
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).toString();
  }
}
