package io.github.oliviercailloux.grade;

public class SubMark extends SubMarksTree {
	public static SubMark given(Criterion criterion, Mark mark) {
		return new SubMark(criterion, mark);
	}

	private SubMark(Criterion criterion, Mark mark) {
		super(criterion, mark);
	}

	@Override
	@Deprecated
	public Mark getMarksTree() {
		return toMark();
	}

	public Mark toMark() {
		return (Mark) super.getMarksTree();
	}

	public double getPoints() {
		return toMark().getPoints();
	}

	public String comment() {
		return toMark().getComment();
	}
}
