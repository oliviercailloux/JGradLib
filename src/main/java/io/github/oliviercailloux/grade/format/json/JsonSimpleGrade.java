package io.github.oliviercailloux.grade.format.json;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.Predicates;
import com.google.common.base.VerifyException;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.github.oliviercailloux.git.git_hub.model.GitHubUsername;
import io.github.oliviercailloux.grade.CompositeGrade;
import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.Exam;
import io.github.oliviercailloux.grade.Grade;
import io.github.oliviercailloux.grade.GradeStructure;
import io.github.oliviercailloux.grade.GradeStructure.DefaultAggregation;
import io.github.oliviercailloux.grade.Mark;
import jakarta.json.Json;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.JsonValue.ValueType;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.adapter.JsonbAdapter;
import jakarta.json.bind.annotation.JsonbPropertyOrder;
import java.io.StringReader;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JsonSimpleGrade {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(JsonSimpleGrade.class);

	private static Grade asGrade(JsonObject gradeObject) {
		checkArgument(!gradeObject.isEmpty());
		final ValueType valueType = gradeObject.get(gradeObject.keySet().iterator().next()).getValueType();
		final boolean isFinal = switch (valueType) {
		case STRING:
		case NUMBER:
		case TRUE:
		case FALSE:
		case NULL:
			yield true;
		case OBJECT:
		case ARRAY:
			yield false;
		default:
			throw new VerifyException("Unexpected value: " + valueType);
		};

		if (isFinal) {
			checkArgument(gradeObject.size() == 2);
			checkArgument(gradeObject.keySet().equals(ImmutableSet.of("points", "comment")));
			final JsonValue pointsValue = gradeObject.get("points");
			checkArgument(pointsValue.getValueType() == ValueType.NUMBER);
			final JsonValue commentValue = gradeObject.get("comment");
			checkArgument(commentValue.getValueType() == ValueType.STRING);
			final double points = ((JsonNumber) pointsValue).doubleValue();
			final String comment = ((JsonString) commentValue).getString();
			return new Mark(points, comment);
		}

		checkArgument(gradeObject.values().stream().map(JsonValue::getValueType)
				.allMatch(Predicates.equalTo(ValueType.OBJECT)));
		final ImmutableMap<Criterion, Grade> subs = gradeObject.keySet().stream()
				.collect(ImmutableMap.toImmutableMap(Criterion::given, s -> asGrade(gradeObject.getJsonObject(s))));
		return Grade.composite(subs);
	}

	private static final class JsonAdapterGradeStructure implements JsonbAdapter<GradeStructure, GSR> {
		@Override
		public GSR adaptToJson(GradeStructure structure) {
			LOGGER.info("Adapting to GSR from {}.", structure);
			final DefaultAggregation defaultAggregation = structure.getDefaultAggregation();
			if (defaultAggregation == DefaultAggregation.ABSOLUTE) {
				checkArgument(structure.getDefaultSubStructure().isEmpty());
				return new GSR(defaultAggregation, Optional.of(structure.getFixedWeights()), structure.getAbsolutes(),
						Optional.empty(), structure.getSubStructures());
			}
			checkArgument(structure.getFixedWeights().isEmpty());
			return new GSR(defaultAggregation, Optional.empty(), structure.getAbsolutes(),
					structure.getDefaultSubStructure(), structure.getSubStructures());
		}

		@Override
		public GradeStructure adaptFromJson(GSR structure) {
			LOGGER.info("Adapting from: {}.", structure);
			if (structure.defaultAggregation == DefaultAggregation.ABSOLUTE) {
				checkArgument(structure.absolutes.isEmpty(), "Not yet supported.");
				checkArgument(Optional.ofNullable(structure.getDefaultSubStructure()).isEmpty());
				return GradeStructure.givenWeights(structure.weights.orElse(ImmutableMap.of()), structure.subs);
			}
			checkArgument(Optional.ofNullable(structure.weights).isEmpty());
			return GradeStructure.maxWithDefault(structure.absolutes,
					Optional.ofNullable(structure.getDefaultSubStructure()).orElse(Optional.empty()), structure.subs);
		}
	}

	private static final class JsonAdapterGrade implements JsonbAdapter<CompositeGrade, Map<String, Grade>> {
		@Override
		public Map<String, Grade> adaptToJson(CompositeGrade grade) {
			checkArgument(grade.isComposite());
			return grade.getCriteria().stream()
					.collect(ImmutableMap.toImmutableMap(Criterion::getName, grade::getGrade));
		}

		@Override
		public CompositeGrade adaptFromJson(Map<String, Grade> structure) {
			return (CompositeGrade) Grade.composite(
					structure.keySet().stream().collect(ImmutableMap.toImmutableMap(Criterion::given, structure::get)));
		}
	}

	private static final class JsonAdapterJsonToGrade implements JsonbAdapter<Grade, JsonObject> {
		@Override
		public JsonObject adaptToJson(Grade grade) {
			return null;
		}

		@Override
		public Grade adaptFromJson(JsonObject structure) {
			return asGrade(structure);
		}
	}

	private static final class JsonAdapterExam
			implements JsonbAdapter<ImmutableMap<GitHubUsername, Grade>, Map<String, Grade>> {
		@Override
		public Map<String, Grade> adaptToJson(ImmutableMap<GitHubUsername, Grade> grades) {
			return grades.keySet().stream()
					.collect(ImmutableMap.toImmutableMap(GitHubUsername::getUsername, grades::get));
		}

		@Override
		public ImmutableMap<GitHubUsername, Grade> adaptFromJson(Map<String, Grade> grades) {
			return grades.keySet().stream().collect(ImmutableMap.toImmutableMap(GitHubUsername::given, grades::get));
		}
	}

	@JsonbPropertyOrder({ "defaultAggregation", "weights", "absolutes", "subs" })
	public static class GSR {
		public DefaultAggregation defaultAggregation;

		public Optional<Map<Criterion, Double>> weights;
		public Set<Criterion> absolutes;
		public Optional<GradeStructure> defaultSubStructure;
		public Map<Criterion, GradeStructure> subs;

		public GSR() {
		}

		public GSR(DefaultAggregation defaultAggregation, Optional<Map<Criterion, Double>> weights,
				Set<Criterion> absolutes, Optional<GradeStructure> defaultSubStructure,
				Map<Criterion, GradeStructure> subs) {
			this.defaultAggregation = defaultAggregation;
			this.weights = weights;
			this.absolutes = absolutes;
			this.defaultSubStructure = defaultSubStructure;
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

		public Optional<GradeStructure> getDefaultSubStructure() {
			return defaultSubStructure;
		}

		public void setDefaultSubStructure(Optional<GradeStructure> defaultSubStructure) {
			this.defaultSubStructure = defaultSubStructure;
		}

		public Map<Criterion, GradeStructure> getSubs() {
			return subs;
		}

		public void setSubs(Map<Criterion, GradeStructure> subs) {
			this.subs = subs;
		}

	}

	public static String toJson(GradeStructure structure) {
		final Jsonb jsonb = JsonHelper.getJsonb(new JsonCriterionToString(), new JsonMapAdapter<Double>() {
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

	public static String toJson(Grade grade) {
		final Jsonb jsonb = JsonHelper.getJsonb(new JsonCriterionToString(), new JsonMapAdapter<Grade>() {
		}, new JsonAdapterGrade());
		return jsonb.toJson(grade);
	}

	public static Grade asGrade(String gradeString) {
		final JsonObject l0 = Json.createReader(new StringReader(gradeString)).readObject();
		return asGrade(l0);
	}

	public static String toJson(Exam exam) {
		final Jsonb jsonb = JsonHelper.getJsonb(new JsonCriterionToString(), new JsonMapAdapter<Double>() {
		}, new JsonMapAdapter<GradeStructure>() {
		}, new JsonAdapterGradeStructure(), new JsonMapAdapter<Grade>() {
		}, new JsonAdapterGrade(), new JsonAdapterExam());
		return jsonb.toJson(exam);
	}

	public static Exam asExam(String examString) {
		final Jsonb jsonb = JsonHelper.getJsonb(new JsonCriterionToString(), new JsonMapAdapter<Double>() {
		}, new JsonMapAdapter<GradeStructure>() {
		}, new JsonAdapterGradeStructure(), new JsonMapAdapter<Grade>() {
		}, new JsonAdapterGrade(), new JsonAdapterExam(), new JsonAdapterJsonToGrade());
		return jsonb.fromJson(examString, Exam.class);
	}
}
