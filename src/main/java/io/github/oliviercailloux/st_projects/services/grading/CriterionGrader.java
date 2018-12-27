package io.github.oliviercailloux.st_projects.services.grading;

import io.github.oliviercailloux.st_projects.model.CriterionGrade;
import io.github.oliviercailloux.st_projects.model.GradeCriterion;

public interface CriterionGrader<T extends GradeCriterion> {
	public CriterionGrade<T> grade();
}
