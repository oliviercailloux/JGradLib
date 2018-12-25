package io.github.oliviercailloux.git.utils;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.stream.Stream;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonBuilderFactory;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonValue;
import javax.json.JsonWriter;
import javax.json.JsonWriterFactory;
import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import javax.json.bind.JsonbConfig;
import javax.json.bind.adapter.JsonbAdapter;
import javax.json.stream.JsonGenerator;

import com.google.common.collect.ImmutableMap;

public class JsonUtils {

	public static JsonArray asArray(Stream<JsonObject> stream, JsonBuilderFactory factory) {
		final JsonArrayBuilder builder = factory.createArrayBuilder();
		stream.forEachOrdered(builder::add);
		return builder.build();
	}

	public static JsonObject asJson(String data) {
		JsonObject json;
		try (JsonReader jr = Json.createReader(new StringReader(data))) {
			json = jr.readObject();
		}
		return json;
	}

	static public String asPrettyString(JsonValue json) {
		if (json == null) {
			return "null";
		}
		final StringWriter stringWriter = new StringWriter();
		final JsonWriterFactory writerFactory = Json
				.createWriterFactory(ImmutableMap.of(JsonGenerator.PRETTY_PRINTING, true));
		try (JsonWriter jsonWriter = writerFactory.createWriter(stringWriter)) {
			jsonWriter.write(json);
		}
		final String string = stringWriter.toString();
		return string;
	}

	public static JsonArrayBuilder addAllTo(JsonArrayBuilder builder, JsonArray content) {
		// TODO addall builder (obtain builder from content)
		for (JsonValue jsonValue : content) {
			builder.add(jsonValue);
		}
		return builder;
	}

	public static String serializeWithJsonB(Object source, @SuppressWarnings("rawtypes") JsonbAdapter... adapters) {
		final String asStr;
		try (Jsonb jsonb = JsonbBuilder.create(new JsonbConfig().withAdapters(adapters).withFormatting(true))) {
			asStr = jsonb.toJson(source);
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
		return asStr;
	}

	public static JsonObject asJsonBObject(Object source) {
		final String asStr = serializeWithJsonB(source);
		return asJson(asStr);
	}
}
