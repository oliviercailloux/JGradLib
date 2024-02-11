package io.github.oliviercailloux.grade.format.json;

import io.github.oliviercailloux.grade.Criterion;
import jakarta.json.Json;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.JsonValue.ValueType;
import jakarta.json.bind.JsonbException;
import jakarta.json.bind.adapter.JsonbAdapter;

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
