package io.github.oliviercailloux.grade;

import java.time.Instant;

public interface GradeModifier {

	MarksTree modify(MarksTree original, Instant timeCap);

}
