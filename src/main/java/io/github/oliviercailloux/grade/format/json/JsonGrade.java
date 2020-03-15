package io.github.oliviercailloux.grade.format.json;

import static com.google.common.base.Preconditions.checkArgument;

import javax.json.JsonObject;
import javax.json.bind.adapter.JsonbAdapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.math.DoubleMath;

import io.github.oliviercailloux.grade.CriterionGradeWeight;
import io.github.oliviercailloux.grade.IGrade;
import io.github.oliviercailloux.grade.Mark;
import io.github.oliviercailloux.grade.WeightingGrade;
import io.github.oliviercailloux.json.JsonbUtils;
import io.github.oliviercailloux.json.PrintableJsonObject;

public class JsonGrade {
	@SuppressWarnings("unused")
	static final Logger LOGGER = LoggerFactory.getLogger(JsonGrade.class);

	public static PrintableJsonObject asJson(IGrade grade) {
		return JsonbUtils.toJsonObject(grade, JsonCriterion.asAdapter());
	}

	public static Mark asMark(JsonObject json) {
		return JsonbUtils.fromJson(json.toString(), Mark.class, JsonCriterion.asAdapter());
	}

	public static WeightingGrade asWeightingGrade(JsonObject json) {
		final WeightingGrade grade = JsonbUtils.fromJson(json.toString(), WeightingGrade.class,
				JsonCriterion.asAdapter(), toCriterionGradeWeightAdapter());
		final double sourcePoints = json.getJsonNumber("points").doubleValue();
		checkArgument(DoubleMath.fuzzyEquals(sourcePoints, grade.getPoints(), 1e-4),
				"Computed " + grade.getPoints() + "; read " + sourcePoints + " - " + json);
		return grade;
	}

	static JsonbAdapter<CriterionGradeWeight, JsonObject> toCriterionGradeWeightAdapter() {
		return new JsonbAdapter<>() {
			@Override
			public JsonObject adaptToJson(CriterionGradeWeight obj) throws Exception {
				return JsonbUtils.toJsonObject(obj, JsonCriterion.asAdapter(), JsonGrade.asAdapter());
			}

			@Override
			public CriterionGradeWeight adaptFromJson(JsonObject obj) throws Exception {
				LOGGER.debug("Adapting from: {}.", obj);
				return JsonbUtils.fromJson(obj.toString(), CriterionGradeWeight.class, JsonCriterion.asAdapter(),
						JsonGrade.asAdapter());
			}
		};
	}

	public static IGrade asGrade(JsonObject json) {
		if (!json.containsKey("subGrades")) {
			return asMark(json);
		}
		return asWeightingGrade(json);
	}

	public static JsonbAdapter<IGrade, JsonObject> asAdapter() {
		return new JsonbAdapter<>() {
			@Override
			public JsonObject adaptToJson(IGrade obj) throws Exception {
				return JsonGrade.asJson(obj);
			}

			@Override
			public IGrade adaptFromJson(JsonObject obj) throws Exception {
				return JsonGrade.asGrade(obj);
			}
		};
	}
}
