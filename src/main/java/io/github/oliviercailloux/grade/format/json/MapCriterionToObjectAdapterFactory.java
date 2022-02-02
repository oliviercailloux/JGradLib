package io.github.oliviercailloux.grade.format.json;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableMap;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import io.github.oliviercailloux.grade.Criterion;
import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MapCriterionToObjectAdapterFactory implements TypeAdapterFactory {

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(JsonGrade.class);

	private static class MapToObjectAdapter<K, V> extends TypeAdapter<Map<K, V>> {

		private final Function<K, String> keyToString;
		private final Function<String, K> stringToKey;
		private final TypeAdapter<V> delegate;

		public MapToObjectAdapter(Function<K, String> keyToString, Function<String, K> stringToKey,
				TypeAdapter<V> delegate) {
			this.keyToString = checkNotNull(keyToString);
			this.stringToKey = checkNotNull(stringToKey);
			this.delegate = checkNotNull(delegate);
		}

		@Override
		public void write(JsonWriter out, Map<K, V> map) throws IOException {
			LOGGER.info("Writing {}.", map);
			out.beginObject();
			for (K key : map.keySet()) {
				out.name(keyToString.apply(key));
				delegate.write(out, map.get(key));
			}
			out.endObject();
		}

		@Override
		public Map<K, V> read(JsonReader in) throws IOException {
			LOGGER.info("Reading map.");
			final JsonToken nextType = in.peek();
			checkState(nextType == JsonToken.STRING);
			in.beginObject();
			final ImmutableMap.Builder<K, V> builder = ImmutableMap.builder();
			while (in.peek() == JsonToken.NAME) {
				final String name = in.nextName();
				final K key = stringToKey.apply(name);
				final V value = delegate.read(in);
				builder.put(key, value);
			}
			in.endObject();
			return builder.build();
		}

	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> TypeAdapter<T> create(Gson gson, com.google.gson.reflect.TypeToken<T> type) {
		if (!Map.class.isAssignableFrom(type.getRawType())) {
			LOGGER.info("Asked about {}, returning null.", type);
			return null;
		}
		final ParameterizedType p = (ParameterizedType) type.getType();
		final Type typeKey = p.getActualTypeArguments()[0];
		if (!typeKey.equals(TypeToken.of(Criterion.class).getType())) {
			LOGGER.info("Asked about key {}, returning null.", typeKey);
			return null;
		}
		final Type typeValue = p.getActualTypeArguments()[1];
		LOGGER.info("Asked about value {}, returning something.", typeValue);
		final com.google.gson.reflect.TypeToken<T> t = (com.google.gson.reflect.TypeToken<T>) com.google.gson.reflect.TypeToken
				.get(typeValue);
		final TypeAdapter<T> adapter;
		try {
			adapter = gson.getAdapter(t);
		} catch (@SuppressWarnings("unused") IllegalArgumentException e) {
			return null;
		}
		final TypeAdapter<Map<Criterion, T>> newAdapter = new MapToObjectAdapter<>(Criterion::getName, Criterion::given,
				adapter);
		return (TypeAdapter<T>) newAdapter;
	}

}