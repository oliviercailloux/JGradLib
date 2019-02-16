package io.github.oliviercailloux.st_projects.services.json;

import javax.json.JsonObject;
import javax.json.bind.adapter.JsonbAdapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.oliviercailloux.json.JsonbUtils;
import io.github.oliviercailloux.json.PrintableJsonObject;
import io.github.oliviercailloux.st_projects.model.Criterion;
import io.github.oliviercailloux.st_projects.model.Mark;

public class JsonMark {
	public static PrintableJsonObject asJson(Mark mark) {
		final JsonbAdapter<Criterion, JsonObject> crit = JsonCriterion.asAdapter();
		return JsonbUtils.toJsonObject(mark, crit);
	}

	public static Mark asMark(String json) {
		LOGGER.debug("Deser: {}.", json);
		return JsonbUtils.fromJson(json, Mark.class, JsonCriterion.asAdapter());
	}

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(JsonMark.class);

	public static Mark asMark(JsonObject json) {
		return asMark(json.toString());
	}

	public static JsonbAdapter<Mark, JsonObject> asAdapter() {
		return new JsonbAdapter<Mark, JsonObject>() {
			@Override
			public JsonObject adaptToJson(Mark obj) throws Exception {
				return asJson(obj);
			}

			@Override
			public Mark adaptFromJson(JsonObject obj) throws Exception {
				return asMark(obj);
			}
		};
	}
}
