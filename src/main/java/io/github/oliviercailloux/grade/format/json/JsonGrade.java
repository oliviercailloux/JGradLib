package io.github.oliviercailloux.grade.format.json;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableMap;
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
import java.util.Map;
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

	private static class GradeStructureToObjectAdapterFactory implements TypeAdapterFactory {

		private static record GSR(DefaultAggregation defaultAggregation, Map<Criterion, Double> weights,
				Map<Criterion, GradeStructure> subs) {
		}

		private static class GradeStructureToObjectAdapter extends TypeAdapter<GradeStructure> {

			private final TypeAdapter<GSR> delegate;

			public GradeStructureToObjectAdapter(TypeAdapter<GSR> delegate) {
				this.delegate = checkNotNull(delegate);
			}

			@Override
			public void write(JsonWriter out, GradeStructure structure) throws IOException {
				delegate.write(out, new GSR(structure.getDefaultAggregation(), structure.getFixedWeights(),
						structure.getSubStructures()));
			}

			@Override
			public GradeStructure read(JsonReader in) throws IOException {
				return GradeStructure.givenWeights(delegate.read(in).weights, ImmutableMap.of());
			}

		}

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

	public static String toJson(GradeStructure s) {
		final Gson gson = new GsonBuilder().registerTypeAdapterFactory(new MapCriterionToObjectAdapterFactory())
				.registerTypeAdapterFactory(new GradeStructureToObjectAdapterFactory()).create();
		return gson.toJson(s);
	}
}
