package io.github.oliviercailloux.grade.format.json;

import io.github.oliviercailloux.grade.Criterion;
import jakarta.json.bind.JsonbException;
import jakarta.json.bind.adapter.JsonbAdapter;

public class JsonCriterionToString implements JsonbAdapter<Criterion, String> {
  private static final JsonCriterionToString INSTANCE = new JsonCriterionToString();

  public static JsonbAdapter<Criterion, String> instance() {
    return INSTANCE;
  }

  @Override
  public String adaptToJson(Criterion criterion) {
    return criterion.getName();
  }

  @Override
  public Criterion adaptFromJson(String str) throws JsonbException {
    return Criterion.given(str);
  }
}
