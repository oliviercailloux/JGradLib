package io.github.oliviercailloux.grade.format.json;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ImmutableMap;
import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.GradeStructure;
import io.github.oliviercailloux.grade.GradeStructure.DefaultAggregation;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.adapter.JsonbAdapter;
import jakarta.json.bind.annotation.JsonbPropertyOrder;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JsonSimpleGrade {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(JsonSimpleGrade.class);

	private static final class JsonAdapterGradeStructure implements JsonbAdapter<GradeStructure, GSR> {
		@Override
		public GSR adaptToJson(GradeStructure structure) {
			LOGGER.info("Adapting to GSR from {}.", structure);
			final DefaultAggregation defaultAggregation = structure.getDefaultAggregation();
			if (defaultAggregation == DefaultAggregation.ABSOLUTE) {
				return new GSR(defaultAggregation, Optional.of(structure.getFixedWeights()), structure.getAbsolutes(),
						structure.getSubStructures());
			}
			checkArgument(structure.getFixedWeights().isEmpty());
			return new GSR(defaultAggregation, Optional.empty(), structure.getAbsolutes(),
					structure.getSubStructures());
		}

		@Override
		public GradeStructure adaptFromJson(GSR structure) {
			LOGGER.info("Adapting from: {}.", structure);
			if (structure.defaultAggregation == DefaultAggregation.ABSOLUTE) {
				checkArgument(structure.absolutes.isEmpty(), "Not yet supported.");
				return GradeStructure.givenWeights(structure.weights.orElse(ImmutableMap.of()), structure.subs);
			}
			checkArgument(Optional.ofNullable(structure.weights).isEmpty());
			return GradeStructure.maxWithGivenAbsolutes(structure.absolutes, structure.subs);
		}
	}

	@JsonbPropertyOrder({ "defaultAggregation", "weights", "absolutes", "subs" })
	public static class GSR {
		public DefaultAggregation defaultAggregation;

		public Optional<Map<Criterion, Double>> weights;
		public Set<Criterion> absolutes;
		public Map<Criterion, GradeStructure> subs;

		public GSR() {
		}

		public GSR(DefaultAggregation defaultAggregation, Optional<Map<Criterion, Double>> weights,
				Set<Criterion> absolutes, Map<Criterion, GradeStructure> subs) {
			this.defaultAggregation = defaultAggregation;
			this.weights = weights;
			this.absolutes = absolutes;
			this.subs = subs;
		}

		public DefaultAggregation getDefaultAggregation() {
			return defaultAggregation;
		}

		public void setDefaultAggregation(DefaultAggregation defaultAggregation) {
			this.defaultAggregation = defaultAggregation;
		}

		public Optional<Map<Criterion, Double>> getWeights() {
			return weights;
		}

		public void setWeights(Optional<Map<Criterion, Double>> weights) {
			this.weights = weights;
		}

		public Set<Criterion> getAbsolutes() {
			return absolutes;
		}

		public void setAbsolutes(Set<Criterion> absolutes) {
			this.absolutes = absolutes;
		}

		public Map<Criterion, GradeStructure> getSubs() {
			return subs;
		}

		public void setSubs(Map<Criterion, GradeStructure> subs) {
			this.subs = subs;
		}

	}

	public static String toJson(GradeStructure structure) {
		final Jsonb jsonb = JsonHelper.getJsonb(new JsonCriterion(), new JsonMapAdapter<Double>() {
		}, new JsonMapAdapter<GradeStructure>() {
		}, new JsonAdapterGradeStructure());
		return jsonb.toJson(structure);
	}

	public static GradeStructure asStructure(String structureString) {
		final Jsonb jsonb = JsonHelper.getJsonb(new JsonCriterion(), new JsonMapAdapter<Double>() {
		}, new JsonMapAdapter<GradeStructure>() {
		}, new JsonAdapterGradeStructure());
		return jsonb.fromJson(structureString, GradeStructure.class);
	}
}
