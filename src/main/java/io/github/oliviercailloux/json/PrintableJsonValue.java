package io.github.oliviercailloux.json;

import javax.json.JsonValue;

public interface PrintableJsonValue extends JsonValue {
	/**
	 * The json text, pretty printed, not starting with '\n'.
	 */
	@Override
	public String toString();

	/**
	 * The json text in condensed form.
	 */
	public String toRawString();
}
