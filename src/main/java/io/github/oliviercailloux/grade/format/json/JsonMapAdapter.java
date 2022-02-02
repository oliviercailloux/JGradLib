package io.github.oliviercailloux.grade.format.json;

import com.google.common.collect.ImmutableMap;
import io.github.oliviercailloux.grade.Criterion;
import jakarta.json.Json;
import jakarta.json.JsonValue;
import jakarta.json.JsonValue.ValueType;
import jakarta.json.bind.JsonbException;
import jakarta.json.bind.adapter.JsonbAdapter;
import java.util.Map;

public class JsonMapAdapter<V> implements JsonbAdapter<Map<Criterion, V>, JsonValue> {

	@Override
	public JsonValue adaptToJson(Map<Criterion, V> criterion) {
		final ImmutableMap<String, Object> mapStr = criterion.keySet().stream()
				.collect(ImmutableMap.toImmutableMap(c -> c.getName(), criterion::get));
		return Json.createObjectBuilder(mapStr).build();
	}

	@Override
	public Map<Criterion, V> adaptFromJson(JsonValue str) throws JsonbException {
		if (!str.getValueType().equals(ValueType.OBJECT)) {
			throw new JsonbException("Unexpected criterion map: " + str);
		}
		return ImmutableMap.of();
	}
}
