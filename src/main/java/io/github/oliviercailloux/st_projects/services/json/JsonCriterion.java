package io.github.oliviercailloux.st_projects.services.json;

import static com.google.common.base.Preconditions.checkArgument;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.bind.adapter.JsonbAdapter;

import io.github.oliviercailloux.json.PrintableJsonObject;
import io.github.oliviercailloux.json.PrintableJsonObjectFactory;
import io.github.oliviercailloux.st_projects.model.Criterion;

public class JsonCriterion {
	public static PrintableJsonObject asJson(Criterion criterion) {
		checkArgument(criterion instanceof Enum<?>);
		final Enum<?> cEnum = (Enum<?>) criterion;
		final JsonObjectBuilder builder = Json.createObjectBuilder();
		builder.add("class", cEnum.getClass().getCanonicalName());
		builder.add("name", cEnum.name());
		return PrintableJsonObjectFactory.wrap(builder.build());
	}

	public static Criterion asCriterion(JsonObject json) {
		final String enumClassName = json.getString("class");
		final String enumInstanceName = json.getString("name");
		Class<?> enumTentativeClass;
		try {
			enumTentativeClass = Class.forName(enumClassName);
		} catch (ClassNotFoundException e) {
			throw new IllegalArgumentException(e);
		}
		@SuppressWarnings("rawtypes")
		final Class<? extends Enum> enumCl = enumTentativeClass.asSubclass(Enum.class);
		@SuppressWarnings({ "unchecked", "rawtypes" })
		final Enum s = Enum.valueOf(enumCl, enumInstanceName);
		return (Criterion) s;
	}

	public static JsonbAdapter<Criterion, JsonObject> asAdapter() {
//		return JsonUtils.getAdapter(JsonCriterion::asCriterion, JsonCriterion::asJson);
		return new JsonbAdapter<Criterion, JsonObject>() {
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
