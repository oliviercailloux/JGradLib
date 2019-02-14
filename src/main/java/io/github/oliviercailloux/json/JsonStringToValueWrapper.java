package io.github.oliviercailloux.json;

public class JsonStringToValueWrapper {

	public static PrintableJsonValue wrapPrettyPrinted(String prettyPrinted) {
		return JsonValueGeneralWrapper.wrapPrettyPrinted(prettyPrinted);
	}

	public static PrintableJsonValue wrapRaw(String raw) {
		return JsonValueGeneralWrapper.wrapRaw(raw);
	}

	private JsonStringToValueWrapper() {
		/** Can’t be instantiated. */
	}

}
