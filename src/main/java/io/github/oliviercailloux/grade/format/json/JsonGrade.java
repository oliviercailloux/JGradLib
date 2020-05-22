package io.github.oliviercailloux.grade.format.json;

import static com.google.common.base.Preconditions.checkArgument;

import javax.json.JsonObject;
import javax.json.bind.adapter.JsonbAdapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.math.DoubleMath;

import io.github.oliviercailloux.grade.Criterion;
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
		return usingSophisticatedCriteria().instanceAsJson(grade);
	}

	public static Mark asMark(JsonObject json) {
		return usingSophisticatedCriteria().instanceAsMark(json);
	}

	public static WeightingGrade asWeightingGrade(JsonObject json) {
		return JsonGrade.usingSophisticatedCriteria().instanceAsWeightingGrade(json);
	}

	public static IGrade asGrade(JsonObject json) {
		return usingSophisticatedCriteria().instanceAsGrade(json);
	}

	public static JsonbAdapter<IGrade, JsonObject> asAdapter() {
		return usingSophisticatedCriteria().instanceAsAdapter();
	}

	public static JsonGrade usingSimpleCriteria() {
		return new JsonGrade(true);
	}

	public static JsonGrade usingSophisticatedCriteria() {
		return new JsonGrade(false);
	}

	private final boolean simpleCriteria;

	private JsonGrade(boolean simpleCriteria) {
		this.simpleCriteria = simpleCriteria;
	}

	public PrintableJsonObject instanceAsJson(IGrade grade) {
		return JsonbUtils.toJsonObject(grade, getCriteriaAdapter());
	}

	public Mark instanceAsMark(JsonObject json) {
		return JsonbUtils.fromJson(json.toString(), Mark.class, getCriteriaAdapter());
	}

	public WeightingGrade instanceAsWeightingGrade(JsonObject json) {
		final WeightingGrade grade = JsonbUtils.fromJson(json.toString(), WeightingGrade.class, getCriteriaAdapter(),
				toCriterionGradeWeightAdapter());
		final double sourcePoints = json.getJsonNumber("points").doubleValue();
		checkArgument(DoubleMath.fuzzyEquals(sourcePoints, grade.getPoints(), 1e-4),
				"Computed " + grade.getPoints() + "; read " + sourcePoints + " - " + json);
		return grade;
	}

	public IGrade instanceAsGrade(JsonObject json) {
		if (!json.containsKey("subGrades")) {
			return instanceAsMark(json);
		}
		return instanceAsWeightingGrade(json);
	}

	public JsonbAdapter<IGrade, JsonObject> instanceAsAdapter() {
		return new JsonbAdapter<>() {
			@Override
			public JsonObject adaptToJson(IGrade obj) throws Exception {
				return instanceAsJson(obj);
			}

			@Override
			public IGrade adaptFromJson(JsonObject obj) throws Exception {
				return instanceAsGrade(obj);
			}
		};
	}

	JsonbAdapter<CriterionGradeWeight, JsonObject> toCriterionGradeWeightAdapter() {
		final JsonbAdapter<Criterion, JsonObject> criteriaAdapter = getCriteriaAdapter();

		return new JsonbAdapter<>() {

			@Override
			public JsonObject adaptToJson(CriterionGradeWeight obj) throws Exception {
				return JsonbUtils.toJsonObject(obj, criteriaAdapter, instanceAsAdapter());
			}

			@Override
			public CriterionGradeWeight adaptFromJson(JsonObject obj) throws Exception {
				LOGGER.debug("Adapting from: {}.", obj);
				return JsonbUtils.fromJson(obj.toString(), CriterionGradeWeight.class, criteriaAdapter,
						instanceAsAdapter());
			}
		};
	}

	private JsonbAdapter<Criterion, JsonObject> getCriteriaAdapter() {
		final JsonbAdapter<Criterion, JsonObject> criteriaAdapter = simpleCriteria ? JsonCriterion.asSimpleAdapter()
				: JsonCriterion.asAdapter();
		return criteriaAdapter;
	}

}
