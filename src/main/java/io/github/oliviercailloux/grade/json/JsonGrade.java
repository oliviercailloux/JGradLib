package io.github.oliviercailloux.grade.json;

import static com.google.common.base.Preconditions.checkArgument;

import javax.json.JsonObject;
import javax.json.bind.adapter.JsonbAdapter;

import com.google.common.math.DoubleMath;

import io.github.oliviercailloux.grade.IGrade;
import io.github.oliviercailloux.grade.Mark;
import io.github.oliviercailloux.grade.WeightingGrade;
import io.github.oliviercailloux.json.JsonbUtils;
import io.github.oliviercailloux.json.PrintableJsonObject;

public class JsonGrade {

	public static PrintableJsonObject asJson(IGrade grade) {
		return JsonbUtils.toJsonObject(grade, JsonCriterion.asAdapter());
	}

	public static Mark asMark(JsonObject json) {
		return JsonbUtils.fromJson(json.toString(), Mark.class, JsonCriterion.asAdapter());
	}

	public static WeightingGrade asWeightingGrade(JsonObject json) {
		final WeightingGrade grade = JsonbUtils.fromJson(json.toString(), WeightingGrade.class,
				JsonCriterion.asAdapter());
		final double sourcePoints = json.getJsonNumber("points").doubleValue();
		checkArgument(DoubleMath.fuzzyEquals(sourcePoints, grade.getPoints(), 1e-4));
		return grade;
	}

	public static IGrade asGrade(JsonObject json) {
		if (!json.containsKey("subGrades")) {
			return asMark(json);
		}
		return asWeightingGrade(json);
	}

	public static JsonbAdapter<IGrade, JsonObject> asAdapter() {
		return new JsonGradeAdapter();
	}
}
