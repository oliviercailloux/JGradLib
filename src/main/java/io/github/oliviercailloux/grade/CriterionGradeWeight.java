package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Objects;

import javax.json.bind.annotation.JsonbCreator;
import javax.json.bind.annotation.JsonbProperty;
import javax.json.bind.annotation.JsonbPropertyOrder;
import javax.json.bind.annotation.JsonbTypeAdapter;

import com.google.common.base.MoreObjects;

import io.github.oliviercailloux.grade.json.JsonGradeAdapter;

@JsonbPropertyOrder({ "criterion", "grade", "weight" })
public class CriterionGradeWeight {
	public static CriterionGradeWeight from(Criterion criterion, IGrade grade, double weight) {
		return new CriterionGradeWeight(criterion, grade, weight);
	}

	@JsonbCreator
	public static CriterionGradeWeight fromJson(@JsonbProperty("criterion") Criterion criterion,
			@JsonbProperty("weight") double weight) {
		return new CriterionGradeWeight(criterion, Mark.given(1d, ""), weight);
	}

	private final Criterion criterion;
	@JsonbTypeAdapter(JsonGradeAdapter.class)
	public final IGrade grade;
	private final double weight;

	private CriterionGradeWeight(Criterion criterion, IGrade grade, double weight) {
		this.criterion = checkNotNull(criterion);
		this.grade = checkNotNull(grade);
		this.weight = checkNotNull(weight);
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
