package io.github.oliviercailloux.grade.format.json;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.GradeStructure;
import io.github.oliviercailloux.grade.GradeStructure.DefaultAggregation;
import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JsonGrade {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(JsonGrade.class);

	private static class CriterionToStringAdapter extends TypeAdapter<Criterion> {

		@Override
		public void write(JsonWriter out, Criterion criterion) throws IOException {
			LOGGER.info("Writing {}.", criterion);
			out.value(criterion.getName());
		}

		@Override
		public Criterion read(JsonReader in) throws IOException {
			LOGGER.info("Reading.");
			final JsonToken nextType = in.peek();
			checkState(nextType == JsonToken.STRING);
			return Criterion.given(in.nextString());
		}

	}

	private static class MapCriterionToObjectAdapter<T> extends TypeAdapter<Map<Criterion, T>> {

		private final TypeAdapter<T> delegate;

		public MapCriterionToObjectAdapter(TypeAdapter<T> delegate) {
			this.delegate = checkNotNull(delegate);
		}

		@Override
		public void write(JsonWriter out, Map<Criterion, T> map) throws IOException {
			LOGGER.info("Writing {}.", map);
			out.beginObject();
			for (Criterion criterion : map.keySet()) {
				out.name(criterion.getName());
				delegate.write(out, map.get(criterion));
			}
			out.endObject();
		}

		@Override
		public Map<Criterion, T> read(JsonReader in) throws IOException {
			LOGGER.info("Reading map.");
			final JsonToken nextType = in.peek();
			checkState(nextType == JsonToken.STRING);
			in.beginObject();
			final ImmutableMap.Builder<Criterion, T> builder = ImmutableMap.builder();
			while (in.peek() == JsonToken.NAME) {
				final String name = in.nextName();
				final Criterion criterion = Criterion.given(name);
				final T value = delegate.read(in);
				builder.put(criterion, value);
			}
			in.endObject();
			return builder.build();
		}

	}

	private static class GradeStructureToObjectAdapter extends TypeAdapter<GradeStructure> {

		private final TypeAdapter<GSR> delegate;

		public GradeStructureToObjectAdapter(TypeAdapter<GSR> delegate) {
			this.delegate = checkNotNull(delegate);
		}

		@Override
		public void write(JsonWriter out, GradeStructure structure) throws IOException {
			delegate.write(out, new GSR(structure.getDefaultAggregation(), structure.getFixedWeights(),
					ImmutableSet.copyOf(structure.getSubStructures().values())));
		}

		@Override
		public GradeStructure read(JsonReader in) throws IOException {
			return GradeStructure.givenWeights(delegate.read(in).weights, ImmutableMap.of());
		}

	}

	private static class GradeStructureToObjectAdapterFactory implements TypeAdapterFactory {

		@SuppressWarnings("unchecked")
		@Override
		public <T> TypeAdapter<T> create(Gson gson, com.google.gson.reflect.TypeToken<T> type) {
			if (!GradeStructure.class.isAssignableFrom(type.getRawType())) {
				LOGGER.info("Asked about {}, returning null.", type);
				return null;
			}
			LOGGER.info("Asked about {}, returning something.", type);
			return (TypeAdapter<T>) new GradeStructureToObjectAdapter(gson.getAdapter(GSR.class));
		}

	}

	private static class MapCriterionToObjectAdapterFactory implements TypeAdapterFactory {

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
			final TypeAdapter<T> adapter = gson.getAdapter((com.google.gson.reflect.TypeToken<T>) t);
			return (TypeAdapter<T>) new MapCriterionToObjectAdapter<>(adapter);
		}

	}

	private static record GSR(DefaultAggregation defaultAggregation, Map<Criterion, Double> weights,
			Set<GradeStructure> subs) {
	}

	public static String toJson(GradeStructure s) {
//		final Gson gson = new GsonBuilder().create();
//		final Gson gson = new GsonBuilder().registerTypeAdapter(Criterion.class, new CriterionToStringAdapter())
//				.create();
		@SuppressWarnings("serial")
		final Type type = new TypeToken<Map<Criterion, Double>>() {
		}.getType();
		final Gson gson = new GsonBuilder().registerTypeAdapterFactory(new MapCriterionToObjectAdapterFactory())
				.registerTypeAdapterFactory(new GradeStructureToObjectAdapterFactory()).create();
		return gson.toJson(s);
	}
}
