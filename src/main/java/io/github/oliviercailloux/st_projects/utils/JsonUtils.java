package io.github.oliviercailloux.st_projects.utils;

import java.io.StringWriter;

import javax.json.Json;
import javax.json.JsonValue;
import javax.json.JsonWriter;
import javax.json.JsonWriterFactory;
import javax.json.stream.JsonGenerator;

import com.google.common.collect.ImmutableMap;

public class JsonUtils {

	static public String asPrettyString(JsonValue json) {
		final StringWriter stringWriter = new StringWriter();
		final JsonWriterFactory writerFactory = Json
				.createWriterFactory(ImmutableMap.of(JsonGenerator.PRETTY_PRINTING, true));
		try (JsonWriter jsonWriter = writerFactory.createWriter(stringWriter)) {
			jsonWriter.write(json);
		}
		final String string = stringWriter.toString();
		return string;
	}

}
