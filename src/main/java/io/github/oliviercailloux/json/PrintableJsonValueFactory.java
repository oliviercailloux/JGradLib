package io.github.oliviercailloux.json;

import javax.json.JsonValue;

public class PrintableJsonValueFactory {

	public static PrintableJsonValue wrapPrettyPrintedString(String prettyPrinted) {
		return JsonValueGeneralWrapper.wrapPrettyPrinted(prettyPrinted);
	}

	public static PrintableJsonValue wrapRawString(String raw) {
		return JsonValueGeneralWrapper.wrapRaw(raw);
	}

	private PrintableJsonValueFactory() {
		/** Can’t be instantiated. */
	}

	public static PrintableJsonValue wrapUnknownStringForm(String unknownForm) {
		return JsonValueGeneralWrapper.wrapUnknown(unknownForm);
	}

	public static PrintableJsonValue wrapValue(JsonValue value) {
		if (value instanceof PrintableJsonValue) {
			return (PrintableJsonValue) value;
		}
		return JsonValueGeneralWrapper.wrapDelegate(value);
	}

}
