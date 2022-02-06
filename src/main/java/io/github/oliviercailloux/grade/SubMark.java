package io.github.oliviercailloux.grade;

public class SubMark extends SubGrade {
	public static SubMark given(Criterion criterion, Mark mark) {
		return new SubMark(criterion, mark);
	}

	private SubMark(Criterion criterion, Mark mark) {
		super(criterion, mark);
	}

	@Override
	public Mark getMarksTree() {
		return (Mark) super.getMarksTree();
	}

	public double getPoints() {
		return getMarksTree().getPoints();
	}
}
