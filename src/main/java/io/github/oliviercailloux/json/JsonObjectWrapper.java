package io.github.oliviercailloux.json;

import javax.json.JsonObject;

public class JsonObjectWrapper {

	public static PrintableJsonObject wrap(JsonObject content) {
		if (content instanceof PrintableJsonObject) {
			return (PrintableJsonObject) content;
		}
		return JsonObjectGeneralWrapper.wrapDelegate(content);
	}

}
