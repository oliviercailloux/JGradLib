package io.github.oliviercailloux.grade.format.json;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.math.DoubleMath;
import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.CriterionGradeWeight;
import io.github.oliviercailloux.grade.IGrade;
import io.github.oliviercailloux.grade.Patch;
import io.github.oliviercailloux.grade.WeightingGrade;
import io.github.oliviercailloux.grade.old.Mark;
import io.github.oliviercailloux.json.JsonbUtils;
import io.github.oliviercailloux.json.PrintableJsonObject;
import io.github.oliviercailloux.json.PrintableJsonObjectFactory;
import jakarta.json.JsonObject;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;
import jakarta.json.bind.JsonbException;
import jakarta.json.bind.adapter.JsonbAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JsonGrade implements JsonbAdapter<IGrade, JsonObject> {
	private static final JsonGrade INSTANCE = new JsonGrade();

	public static JsonbAdapter<IGrade, JsonObject> instance() {
		return INSTANCE;
	}

	@Override
	public JsonObject adaptToJson(IGrade grade) {
		return INSTANCE.instanceAsJson(grade);
	}

	@Override
	public IGrade adaptFromJson(JsonObject json) throws JsonbException {
		return asGrade(json);
	}

	@SuppressWarnings("unused")
	static final Logger LOGGER = LoggerFactory.getLogger(JsonGrade.class);

	public static PrintableJsonObject asJson(IGrade grade) {
		return create().instanceAsJson(grade);
	}

	public static Mark asMark(JsonObject json) {
		return create().instanceAsMark(json);
	}

	public static WeightingGrade asWeightingGrade(JsonObject json) {
		return create().instanceAsWeightingGrade(json);
	}

	public static IGrade asGrade(String json) {
		return create().instanceAsGrade(PrintableJsonObjectFactory.wrapString(json));
	}

	public static IGrade asGrade(JsonObject json) {
		return create().instanceAsGrade(json);
	}

	public static JsonbAdapter<IGrade, JsonObject> asAdapter() {
		return create().instanceAsAdapter();
	}

	public static JsonGrade create() {
		return new JsonGrade();
	}

	public static Patch asPatch(String json) {
		return JsonbUtils.fromJson(json, Patch.class, JsonGrade.instance(), JsonCriterion.instance());
	}

	public static PrintableJsonObject patchAsJson(Patch patch) {
		return JsonbUtils.toJsonObject(patch, JsonGrade.instance(), JsonCriterion.instance());
	}

	private final Jsonb jsonb;

	private final Jsonb jsonbWithThisAdapter;

	private JsonGrade() {
		jsonb = JsonbBuilder.create(
				new JsonbConfig().withAdapters(JsonCriterion.instance(), toCriterionGradeWeightAdapter())
						.withFormatting(true));
		jsonbWithThisAdapter = JsonbBuilder.create(new JsonbConfig()
				.withAdapters(JsonCriterion.instance(), instanceAsAdapter()).withFormatting(true));
	}

	public PrintableJsonObject instanceAsJson(IGrade grade) {
		return JsonbUtils.toJsonObject(grade, JsonCriterion.instance());
	}

	public Mark instanceAsMark(JsonObject json) {
		return jsonb.fromJson(json.toString(), Mark.class);
	}

	public WeightingGrade instanceAsWeightingGrade(JsonObject json) {
		final WeightingGrade grade = jsonb.fromJson(json.toString(), WeightingGrade.class);
		if (json.containsKey("points")) {
			final double sourcePoints = json.getJsonNumber("points").doubleValue();
			checkArgument(DoubleMath.fuzzyEquals(sourcePoints, grade.getPoints(), 1e-4),
					"Computed " + grade.getPoints() + "; read " + sourcePoints + " - " + json);
		}
		return grade;
	}

	public IGrade instanceAsGrade(JsonObject json) {
		if (!json.containsKey("subGrades")) {
			return instanceAsMark(json);
		}
		final WeightingGrade weighting = instanceAsWeightingGrade(json);
		return weighting;
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
		final JsonbAdapter<Criterion, ?> criteriaAdapter = JsonCriterion.instance();

		return new JsonbAdapter<>() {

			@Override
			public JsonObject adaptToJson(CriterionGradeWeight obj) throws Exception {
				return JsonbUtils.toJsonObject(obj, criteriaAdapter, instanceAsAdapter());
			}

			@Override
			public CriterionGradeWeight adaptFromJson(JsonObject obj) throws Exception {
				LOGGER.debug("Adapting from: {}.", obj);
				return jsonbWithThisAdapter.fromJson(obj.toString(), CriterionGradeWeight.class);
			}
		};
	}
}
