package io.github.oliviercailloux.grade;

import com.google.common.base.MoreObjects;
import java.util.Objects;

abstract class GradeStructureAbstractImpl implements GradeStructure {
	@Override
	public boolean equals(Object o2) {
		if (!(o2 instanceof GradeStructureAbstractImpl)) {
			return false;
		}
		final GradeStructureAbstractImpl t2 = (GradeStructureAbstractImpl) o2;
		return getDefaultAggregation().equals(t2.getDefaultAggregation()) && getAbsolutes().equals(t2.getAbsolutes())
				&& getFixedWeights().equals(t2.getFixedWeights()) && getSubStructures().equals(t2.getSubStructures());
	}

	@Override
	public int hashCode() {
		return Objects.hash(getDefaultAggregation(), getAbsolutes(), getFixedWeights(), getSubStructures());
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this).add("default aggregation", getDefaultAggregation())
				.add("absolutes", getAbsolutes()).add("fixed weights", getFixedWeights())
				.add("sub structures", getSubStructures()).toString();
	}
}
