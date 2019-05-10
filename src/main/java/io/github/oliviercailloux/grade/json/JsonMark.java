package io.github.oliviercailloux.grade.json;

import javax.json.JsonObject;
import javax.json.bind.adapter.JsonbAdapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.Grade;
import io.github.oliviercailloux.json.JsonbUtils;
import io.github.oliviercailloux.json.PrintableJsonObject;

public class JsonMark {
	public static PrintableJsonObject asJson(Grade mark) {
		final JsonbAdapter<Criterion, JsonObject> crit = JsonCriterion.asAdapter();
		return JsonbUtils.toJsonObject(mark, crit);
	}

	public static Grade asMark(String json) {
		LOGGER.debug("Deser: {}.", json);
		return JsonbUtils.fromJson(json, Grade.class, JsonCriterion.asAdapter());
	}

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(JsonMark.class);

	public static Grade asMark(JsonObject json) {
		return asMark(json.toString());
	}

	public static JsonbAdapter<Grade, JsonObject> asAdapter() {
		return new JsonbAdapter<>() {
			@Override
			public JsonObject adaptToJson(Grade obj) throws Exception {
				return asJson(obj);
			}

			@Override
			public Grade adaptFromJson(JsonObject obj) throws Exception {
				return asMark(obj);
			}
		};
	}
}
