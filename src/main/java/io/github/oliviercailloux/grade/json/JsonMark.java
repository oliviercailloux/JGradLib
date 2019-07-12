package io.github.oliviercailloux.grade.json;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.bind.adapter.JsonbAdapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.CriterionAndMark;
import io.github.oliviercailloux.json.JsonbUtils;
import io.github.oliviercailloux.json.PrintableJsonObject;
import io.github.oliviercailloux.json.PrintableJsonObjectFactory;

public class JsonMark {
	public static PrintableJsonObject asJson(CriterionAndMark mark) {
		final JsonbAdapter<Criterion, JsonObject> crit = JsonCriterion.asAdapter();
		/**
		 * TODO this is now much too subtle for JSON-B because of the current f-up state
		 * of the grade.
		 */
//		return JsonbUtils.toJsonObject(mark, crit);
		try {
			final JsonObject json = Json.createObjectBuilder().add("criterion", crit.adaptToJson(mark.getCriterion()))
					.add("points", mark.getPoints()).add("comment", mark.getComment()).build();
			return PrintableJsonObjectFactory.wrapObject(json);
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	public static CriterionAndMark asMark(String json) {
		LOGGER.debug("Deser: {}.", json);
		return JsonbUtils.fromJson(json, CriterionAndMark.class, JsonCriterion.asAdapter());
	}

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(JsonMark.class);

	public static CriterionAndMark asMark(JsonObject json) {
		return asMark(json.toString());
	}

	public static JsonbAdapter<CriterionAndMark, JsonObject> asAdapter() {
		return new JsonbAdapter<>() {
			@Override
			public JsonObject adaptToJson(CriterionAndMark obj) throws Exception {
				return asJson(obj);
			}

			@Override
			public CriterionAndMark adaptFromJson(JsonObject obj) throws Exception {
				return asMark(obj);
			}
		};
	}
}
