package io.github.oliviercailloux.grade.format.json;

import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.GradeStructure;
import io.github.oliviercailloux.grade.GradeStructure.DefaultAggregation;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;
import jakarta.json.bind.annotation.JsonbPropertyOrder;
import jakarta.json.bind.config.PropertyVisibilityStrategy;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

public class JsonbSimpleGrade {
	public static String toJson(GradeStructure structure) {
		final PropertyVisibilityStrategy propertyVisibilityStrategy = new PropertyVisibilityStrategy() {
			@Override
			public boolean isVisible(Field field) {
				return true;
			}

			@Override
			public boolean isVisible(Method method) {
				return false;
			}
		};

		final Jsonb jsonb = JsonbBuilder
				.create(new JsonbConfig().withPropertyVisibilityStrategy(propertyVisibilityStrategy)
						.withAdapters(new JsonCriterion()).withAdapters(new JsonMapAdapter<Double>() {
						}));
		@JsonbPropertyOrder({ "defaultAggregation", "weights", "subs" })
		record GSR(DefaultAggregation defaultAggregation, Map<Criterion, Double> weights,
				Map<Criterion, GradeStructure> subs) {
		}
		return jsonb.toJson(
				new GSR(structure.getDefaultAggregation(), structure.getFixedWeights(), structure.getSubStructures()));
	}
}
