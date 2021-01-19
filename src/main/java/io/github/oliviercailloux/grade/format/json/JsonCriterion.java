package io.github.oliviercailloux.grade.format.json;

import javax.json.Json;
import javax.json.JsonString;
import javax.json.JsonValue;
import javax.json.JsonValue.ValueType;
import javax.json.bind.JsonbException;
import javax.json.bind.adapter.JsonbAdapter;

import io.github.oliviercailloux.grade.Criterion;

public class JsonCriterion implements JsonbAdapter<Criterion, JsonValue> {
	private static final JsonCriterion INSTANCE = new JsonCriterion();

	public static JsonbAdapter<Criterion, JsonValue> instance() {
		return INSTANCE;
	}

	@Override
	public JsonValue adaptToJson(Criterion criterion) {
		return Json.createValue(criterion.getName());
	}

	@Override
	public Criterion adaptFromJson(JsonValue str) throws JsonbException {
		if (!str.getValueType().equals(ValueType.STRING)) {
			throw new JsonbException("Unexpected criterion: " + str);
		}
		return Criterion.given(((JsonString) str).getString());
	}
}
