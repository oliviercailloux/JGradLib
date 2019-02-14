package io.github.oliviercailloux.st_projects.utils;

import java.util.stream.Collectors;

import com.google.common.collect.ImmutableList;

import io.github.oliviercailloux.st_projects.model.Criterion;
import io.github.oliviercailloux.st_projects.model.Mark;

public class GradingUtils {

	public static Mark getGradeFromSuccesses(Criterion criterion, ImmutableList<Boolean> successes) {
		final double spread = criterion.getMaxPoints() - criterion.getMinPoints();
		final double pointsPerSuccess = spread / successes.size();
		final double points = successes.stream().collect(Collectors.summingDouble((b) -> b ? pointsPerSuccess : 0));
		final Mark grade = Mark.of(criterion, points + criterion.getMinPoints(), "");
		return grade;
	}

}
