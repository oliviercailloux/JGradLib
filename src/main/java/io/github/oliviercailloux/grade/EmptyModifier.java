package io.github.oliviercailloux.grade;

import java.time.Instant;

public class EmptyModifier implements GradeModifier {

	@Override
	public MarksTree modify(MarksTree original, Instant timeCap) {
		return original;
	}
}
