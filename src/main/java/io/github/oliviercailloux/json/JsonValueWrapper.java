package io.github.oliviercailloux.json;

import javax.json.JsonValue;

public class JsonValueWrapper {

	public static PrintableJsonValue wrap(JsonValue value) {
		if (value instanceof PrintableJsonValue) {
			return (PrintableJsonValue) value;
		}
		return JsonValueGeneralWrapper.wrapDelegate(value);
	}

	private JsonValueWrapper() {
		/** Can’t be instantiated. */
	}

}
