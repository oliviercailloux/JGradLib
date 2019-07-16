package io.github.oliviercailloux.grade.json;

import javax.json.JsonObject;
import javax.json.bind.adapter.JsonbAdapter;

import io.github.oliviercailloux.grade.IGrade;

public final class JsonGradeAdapter implements JsonbAdapter<IGrade, JsonObject> {
	@Override
	public JsonObject adaptToJson(IGrade obj) throws Exception {
		return JsonGrade.asJson(obj);
	}

	@Override
	public IGrade adaptFromJson(JsonObject obj) throws Exception {
		return JsonGrade.asGrade(obj);
	}
}