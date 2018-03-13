package io.github.oliviercailloux.st_projects.utils;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.StringWriter;
import java.util.stream.Stream;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonValue;
import javax.json.JsonWriter;
import javax.json.JsonWriterFactory;
import javax.json.stream.JsonGenerator;

import com.google.common.collect.ImmutableMap;

public class JsonUtils {

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

	static public Stream<JsonObject> getContent(JsonObject connection) {
		final JsonArray nodes = connection.getJsonArray("nodes");
		checkArgument(connection.getInt("totalCount") == nodes.size());
		checkArgument(!connection.getJsonObject("pageInfo").getBoolean("hasNextPage"));
		final Stream<JsonObject> contents = nodes.stream().map(JsonValue::asJsonObject);
		return contents;
	}

}
