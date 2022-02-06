package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.MoreObjects;
import java.util.Objects;

public class SubGrade {

	public static SubGrade given(Criterion criterion, MarksTree grade) {
		return new SubGrade(criterion, grade);
	}

	private final Criterion criterion;
	private final MarksTree marksTree;

	protected SubGrade(Criterion criterion, MarksTree grade) {
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
		if (!(o2 instanceof SubGrade)) {
			return false;
		}
		final SubGrade t2 = (SubGrade) o2;
		return criterion.equals(t2.criterion) && marksTree.equals(t2.marksTree);
	}

	@Override
	public int hashCode() {
		return Objects.hash(criterion, marksTree);
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this).add("criterion", criterion).add("grade", marksTree).toString();
	}
}
