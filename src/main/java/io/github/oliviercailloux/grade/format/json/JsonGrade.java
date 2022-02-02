package io.github.oliviercailloux.grade.format.json;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableMap;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.GradeStructure;
import io.github.oliviercailloux.grade.GradeStructure.DefaultAggregation;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JsonGrade {
	private static class CriterionToStringAdapter extends TypeAdapter<Criterion> {
		@SuppressWarnings("unused")
		private static final Logger LOGGER = LoggerFactory.getLogger(JsonGrade.CriterionToStringAdapter.class);

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

	private static class MapCriterionDoubleToObjectAdapter extends TypeAdapter<Map<Criterion, Double>> {
		@SuppressWarnings("unused")
		private static final Logger LOGGER = LoggerFactory.getLogger(JsonGrade.CriterionToStringAdapter.class);

		@Override
		public void write(JsonWriter out, Map<Criterion, Double> map) throws IOException {
			LOGGER.info("Writing {}.", map);
			out.beginObject();
			for (Criterion criterion : map.keySet()) {
				out.name(criterion.getName());
				out.value(map.get(criterion));
			}
			out.endObject();
		}

		@Override
		public Map<Criterion, Double> read(JsonReader in) throws IOException {
			LOGGER.info("Reading map.");
			final JsonToken nextType = in.peek();
			checkState(nextType == JsonToken.STRING);
			in.beginObject();
			final ImmutableMap.Builder<Criterion, Double> builder = ImmutableMap.builder();
			while (in.peek() == JsonToken.NAME) {
				final String name = in.nextName();
				final Criterion criterion = Criterion.given(name);
				final double value = in.nextDouble();
				builder.put(criterion, value);
			}
			in.endObject();
			return builder.build();
		}

	}

	public static record GSR(DefaultAggregation defaultAggregation, Map<Criterion, Double> weights) {
	}

	public static String toJson(GradeStructure s) {
//		final Gson gson = new GsonBuilder().create();
//		final Gson gson = new GsonBuilder().registerTypeAdapter(Criterion.class, new CriterionToStringAdapter())
//				.create();
		@SuppressWarnings("serial")
		final Type type = new TypeToken<Map<Criterion, Double>>() {
		}.getType();
		final Gson gson = new GsonBuilder().registerTypeAdapter(type, new MapCriterionDoubleToObjectAdapter()).create();
		return gson.toJson(new GSR(s.getDefaultAggregation(), s.getFixedWeights()));
	}
}
