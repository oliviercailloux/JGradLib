package io.github.oliviercailloux.grade;

public class SubMark extends SubGrade {
	public static SubMark given(Criterion criterion, Mark mark) {
		return new SubMark(criterion, mark);
	}

	private SubMark(Criterion criterion, Mark mark) {
		super(criterion, mark);
	}

	@Override
	public Mark getGrade() {
		return (Mark) super.getGrade();
	}
}
