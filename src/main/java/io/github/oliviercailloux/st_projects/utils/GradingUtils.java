package io.github.oliviercailloux.st_projects.utils;

import java.util.stream.Collectors;

import com.google.common.collect.ImmutableList;

import io.github.oliviercailloux.st_projects.model.Criterion;
import io.github.oliviercailloux.st_projects.model.CriterionGrade;

public class GradingUtils {

	public static CriterionGrade getGradeFromSuccesses(Criterion criterion, ImmutableList<Boolean> successes) {
		final double spread = criterion.getMaxPoints() - criterion.getMinPoints();
		final double pointsPerSuccess = spread / successes.size();
		final double points = successes.stream().collect(Collectors.summingDouble((b) -> b ? pointsPerSuccess : 0));
		final CriterionGrade grade = CriterionGrade.of(criterion, points + criterion.getMinPoints(), "");
		return grade;
	}

}
