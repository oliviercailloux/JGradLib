package io.github.oliviercailloux.grade.format.json;

import com.google.common.collect.ImmutableMap;
import io.github.oliviercailloux.grade.Criterion;
import jakarta.json.bind.JsonbException;
import jakarta.json.bind.adapter.JsonbAdapter;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JsonMapAdapter<V> implements JsonbAdapter<Map<Criterion, V>, Map<String, V>> {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(JsonMapAdapter.class);

	public static <V> Map<String, V> toStringKeys(Map<Criterion, V> criterion) {
		return criterion.keySet().stream().collect(ImmutableMap.toImmutableMap(Criterion::getName, criterion::get));
	}

	public static <V> Map<Criterion, V> toCriterionKeys(Map<String, V> str) {
		return str.keySet().stream().collect(ImmutableMap.toImmutableMap(Criterion::given, str::get));
	}

	@Override
	public Map<String, V> adaptToJson(Map<Criterion, V> criterion) {
		return toStringKeys(criterion);
	}

	@Override
	public Map<Criterion, V> adaptFromJson(Map<String, V> str) throws JsonbException {
		return toCriterionKeys(str);
	}
}
