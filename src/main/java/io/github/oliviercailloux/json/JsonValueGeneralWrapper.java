package io.github.oliviercailloux.json;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.Optional;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonValue;
import javax.json.JsonWriter;
import javax.json.JsonWriterFactory;
import javax.json.stream.JsonGenerator;

import com.google.common.collect.ImmutableMap;

/**
 * Has at least one of delegate, prettyPrinted or raw form when created,
 * initializes the rest as needed.
 *
 * @author Olivier Cailloux
 *
 */
class JsonValueGeneralWrapper implements PrintableJsonValue {

	private JsonValue delegate;
	private String prettyPrinted;
	private String raw;

	private JsonValueGeneralWrapper(Optional<String> prettyPrinted, Optional<String> raw,
			Optional<JsonValue> delegate) {
		assert prettyPrinted.isPresent() || raw.isPresent() || delegate.isPresent();

		if (prettyPrinted.isPresent()) {
			this.prettyPrinted = prettyPrinted.get();
			checkArgument(!this.prettyPrinted.startsWith("\n"));
		} else {
			this.prettyPrinted = null;
		}

		if (raw.isPresent()) {
			this.raw = raw.get();
			checkArgument(!this.raw.startsWith("\n"));
		} else {
			this.raw = null;
		}

		if (delegate.isPresent()) {
			this.delegate = delegate.get();
		} else {
			this.delegate = null;
		}
	}

	private JsonValueGeneralWrapper(String unknownForm) {
		this(Optional.empty(), Optional.empty(), Optional.of(asJsonValue(requireNonNull(unknownForm))));
	}

	@Override
	public boolean equals(Object obj) {
		return getDelegate().equals(obj);
	}

	@Override
	public ValueType getValueType() {
		return getDelegate().getValueType();
	}

	@Override
	public int hashCode() {
		return getDelegate().hashCode();
	}

	@Override
	public String toRawString() {
		if (raw == null) {
			raw = getDelegate().toString();
		}

		return raw;
	}

	@Override
	public String toString() {
		if (prettyPrinted == null) {
			prettyPrinted = asPrettyString(getDelegate());
		}

		return prettyPrinted;
	}

	public static JsonValueGeneralWrapper wrapPrettyPrinted(String prettyPrinted) {
		return new JsonValueGeneralWrapper(Optional.of(prettyPrinted), Optional.empty(), Optional.empty());
	}

	JsonValue getDelegate() {
		if (delegate == null) {
			if (raw != null) {
				delegate = asJsonValue(raw);
			} else {
				assert prettyPrinted != null;
				delegate = asJsonValue(prettyPrinted);
			}
		}

		return delegate;
	}

	@Override
	public JsonArray asJsonArray() {
		return getDelegate().asJsonArray();
	}

	@Override
	public JsonObject asJsonObject() {
		return getDelegate().asJsonObject();
	}

	private static JsonValue asJsonValue(String data) {
		final JsonValue json;
		try (JsonReader jr = Json.createReader(new StringReader(data))) {
			json = jr.readValue();
		}
		return json;
	}

	static private String asPrettyString(JsonValue json) {
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
		assert string.startsWith("\n");
		return string.substring(1);
	}

	public static JsonValueGeneralWrapper wrapDelegate(JsonValue delegate) {
		return new JsonValueGeneralWrapper(Optional.empty(), Optional.empty(), Optional.of(delegate));
	}

	public static JsonValueGeneralWrapper wrapRaw(String raw) {
		return new JsonValueGeneralWrapper(Optional.empty(), Optional.of(raw), Optional.empty());
	}

	public static JsonValueGeneralWrapper wrapUnknown(String unknownForm) {
		return new JsonValueGeneralWrapper(unknownForm);
	}

}
