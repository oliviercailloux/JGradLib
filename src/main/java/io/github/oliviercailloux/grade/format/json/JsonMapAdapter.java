package io.github.oliviercailloux.grade.format.json;

import com.google.common.collect.ImmutableMap;
import io.github.oliviercailloux.grade.Criterion;
import jakarta.json.bind.JsonbException;
import jakarta.json.bind.adapter.JsonbAdapter;
import java.util.Map;

public class JsonMapAdapter<V> implements JsonbAdapter<Map<Criterion, V>, Map<String, Object>> {

	@Override
	public Map<String, Object> adaptToJson(Map<Criterion, V> criterion) {
		final ImmutableMap<String, Object> mapStr = criterion.keySet().stream()
				.collect(ImmutableMap.toImmutableMap(c -> c.getName(), criterion::get));
		return mapStr;
	}

	@Override
	public Map<Criterion, V> adaptFromJson(Map<String, Object> str) throws JsonbException {
		return ImmutableMap.of();
	}
}
