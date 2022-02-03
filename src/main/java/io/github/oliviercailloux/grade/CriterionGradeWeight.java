package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.MoreObjects;
import io.github.oliviercailloux.grade.WeightingGrade.WeightedGrade;
import jakarta.json.bind.annotation.JsonbCreator;
import jakarta.json.bind.annotation.JsonbProperty;
import jakarta.json.bind.annotation.JsonbPropertyOrder;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@JsonbPropertyOrder({ "criterion", "grade", "weight" })
public class CriterionGradeWeight {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(CriterionGradeWeight.class);

	@JsonbCreator
	public static CriterionGradeWeight from(@JsonbProperty("criterion") Criterion criterion,
			@JsonbProperty("grade") IGrade grade, @JsonbProperty("weight") double weight) {
		return new CriterionGradeWeight(criterion, grade, weight);
	}

	public static CriterionGradeWeight from(Criterion criterion, WeightedGrade weightedGrade) {
		return new CriterionGradeWeight(criterion, weightedGrade.getGrade(), weightedGrade.getWeight());
	}

	private final Criterion criterion;
	private final IGrade grade;
	private final double weight;

	private CriterionGradeWeight(Criterion criterion, IGrade grade, double weight) {
		this.criterion = checkNotNull(criterion);
		this.grade = checkNotNull(grade, criterion);
		this.weight = checkNotNull(weight, criterion);
		LOGGER.debug("Built {}, {}, {}.", criterion, grade, weight);
	}

	public Criterion getCriterion() {
		return criterion;
	}

	public IGrade getGrade() {
		return grade;
	}

	public double getWeight() {
		return weight;
	}

	@Override
	public boolean equals(Object o2) {
		if (!(o2 instanceof CriterionGradeWeight)) {
			return false;
		}
		final CriterionGradeWeight c2 = (CriterionGradeWeight) o2;
		return criterion.equals(c2.criterion) && grade.equals(c2.grade) && weight == c2.weight;
	}

	@Override
	public int hashCode() {
		return Objects.hash(criterion, grade, weight);
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this).add("criterion", criterion).add("grade", grade).add("weight", weight)
				.toString();
	}
}
