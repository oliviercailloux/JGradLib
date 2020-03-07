package io.github.oliviercailloux.grade.format.json;

import static com.google.common.base.Preconditions.checkArgument;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;
import javax.json.bind.adapter.JsonbAdapter;

import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.json.PrintableJsonObject;
import io.github.oliviercailloux.json.PrintableJsonObjectFactory;

public class JsonCriterion {
	public static PrintableJsonObject asJson(Criterion criterion) {
		final JsonObjectBuilder builder = Json.createObjectBuilder();
		if (criterion instanceof Enum<?>) {
			final Enum<?> cEnum = (Enum<?>) criterion;
			builder.add("class", cEnum.getClass().getName());
		}
		builder.add("name", criterion.getName());
		return PrintableJsonObjectFactory.wrapObject(builder.build());
	}

	public static Criterion asCriterion(JsonObject json) {
		checkArgument(!json.containsKey("class") || !json.get("class").equals(JsonValue.NULL));
		final String enumClassName = json.getString("class", null);
		final String criterionName = json.getString("name");
		if (enumClassName != null) {
			final Class<?> enumTentativeClass;
			try {
				enumTentativeClass = Class.forName(enumClassName);
			} catch (ClassNotFoundException e) {
				throw new IllegalArgumentException(e);
			}
			@SuppressWarnings("rawtypes")
			final Class<? extends Enum> enumCl = enumTentativeClass.asSubclass(Enum.class);
			@SuppressWarnings({ "unchecked", "rawtypes" })
			final Enum s = Enum.valueOf(enumCl, criterionName);
			return (Criterion) s;
		}
		return Criterion.given(criterionName);
	}

	public static JsonbAdapter<Criterion, JsonObject> asAdapter() {
		return new JsonbAdapter<>() {
			@Override
			public JsonObject adaptToJson(Criterion obj) throws Exception {
				return asJson(obj);
			}

			@Override
			public Criterion adaptFromJson(JsonObject obj) throws Exception {
				return asCriterion(obj);
			}
		};
	}
}
