package io.github.oliviercailloux.json;

public class JsonStringToObjectWrapper {

	public static PrintableJsonObject wrapPrettyPrinted(String prettyPrinted) {
		return JsonObjectGeneralWrapper.wrapPrettyPrinted(prettyPrinted);
	}

	public static PrintableJsonObject wrapRaw(String raw) {
		return JsonObjectGeneralWrapper.wrapRaw(raw);
	}

	private JsonStringToObjectWrapper() {
		/** Can’t be instantiated. */
	}

	public static PrintableJsonObject wrapUnknown(String unknownForm) {
		return JsonObjectGeneralWrapper.wrapUnknown(unknownForm);
	}

}
