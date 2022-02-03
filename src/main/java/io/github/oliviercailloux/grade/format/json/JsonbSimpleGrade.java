package io.github.oliviercailloux.grade.format.json;

import static com.google.common.base.Preconditions.checkArgument;

import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.GradeStructure;
import io.github.oliviercailloux.grade.GradeStructure.DefaultAggregation;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;
import jakarta.json.bind.adapter.JsonbAdapter;
import jakarta.json.bind.annotation.JsonbPropertyOrder;
import jakarta.json.bind.config.PropertyVisibilityStrategy;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class JsonbSimpleGrade {
	@JsonbPropertyOrder({ "defaultAggregation", "weights", "absolutes", "subs" })
	public static record GSR(DefaultAggregation defaultAggregation, Optional<Map<Criterion, Double>> weights,
			Set<Criterion> absolutes, Map<Criterion, GradeStructure> subs) {
	}

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

		final Jsonb jsonb = JsonbBuilder.create(
				new JsonbConfig().withFormatting(true).withPropertyVisibilityStrategy(propertyVisibilityStrategy)
						.withAdapters(new JsonCriterion()).withAdapters(new JsonMapAdapter<Double>() {
						}).withAdapters(new JsonMapAdapter<GradeStructure>() {
						}).withAdapters(new JsonbAdapter<GradeStructure, GSR>() {
							@Override
							public GSR adaptToJson(GradeStructure structure) {
								final DefaultAggregation defaultAggregation = structure.getDefaultAggregation();
								if (defaultAggregation == DefaultAggregation.ABSOLUTE) {
									return new GSR(defaultAggregation, Optional.of(structure.getFixedWeights()),
											structure.getAbsolutes(), structure.getSubStructures());
								}
								checkArgument(structure.getFixedWeights().isEmpty());
								return new GSR(defaultAggregation, Optional.empty(), structure.getAbsolutes(),
										structure.getSubStructures());
							}

							@Override
							public GradeStructure adaptFromJson(GSR structure) {
								return null;
							}
						}));
		return jsonb.toJson(structure);
	}
}
