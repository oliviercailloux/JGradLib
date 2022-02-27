package io.github.oliviercailloux.grade;

import static io.github.oliviercailloux.grade.CriteriaPathTestsHelper.p1;
import static io.github.oliviercailloux.grade.CriteriaPathTestsHelper.p11;
import static io.github.oliviercailloux.grade.CriteriaPathTestsHelper.p12;
import static io.github.oliviercailloux.grade.CriteriaPathTestsHelper.p2;
import static io.github.oliviercailloux.grade.CriteriaPathTestsHelper.p21;
import static io.github.oliviercailloux.grade.CriteriaPathTestsHelper.p22;
import static io.github.oliviercailloux.grade.CriterionTestsHelper.c1;
import static io.github.oliviercailloux.grade.CriterionTestsHelper.c2;
import static io.github.oliviercailloux.grade.CriterionTestsHelper.c21;
import static io.github.oliviercailloux.grade.CriterionTestsHelper.c22;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.Test;

public class GradeAggregatorTests {
	@Test
	void testAggregate() throws Exception {
		final GradeAggregator c1A = GradeAggregator.max();
		final GradeAggregator c2A = GradeAggregator.staticAggregator(ImmutableMap.of(c21, 1d, c22, 2d),
				ImmutableMap.of());
		final GradeAggregator aggregator = GradeAggregator.max(ImmutableMap.of(c1, c1A, c2, c2A));
		final MarksTree marks = MarksTreeTestsHelper.get1_11And1_12And2_21And2_22();
		final Grade grade = Grade.given(aggregator, marks);
		assertEquals(1d, grade.getGrade(p11).getMark().getPoints());
		assertEquals(0d, grade.getGrade(p12).getMark().getPoints());
		assertEquals(1d, grade.getGrade(c1).getMark().getPoints());
		assertEquals(1d, grade.getGrade(p1).getMark().getPoints());
		assertEquals(1d, grade.getGrade(p21).getMark().getPoints());
		assertEquals(0d, grade.getGrade(p22).getMark().getPoints());
		assertEquals(1d / 3d, grade.getGrade(c2).getMark().getPoints(), 1e-6d);
		assertEquals(1d / 3d, grade.getGrade(p2).getMark().getPoints(), 1e-6d);
		assertEquals(1d, grade.getMark().getPoints());
	}
}
