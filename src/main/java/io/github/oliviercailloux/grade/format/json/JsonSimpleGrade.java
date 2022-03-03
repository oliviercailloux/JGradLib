package io.github.oliviercailloux.grade.format.json;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.Predicates;
import com.google.common.base.VerifyException;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.github.oliviercailloux.git.git_hub.model.GitHubUsername;
import io.github.oliviercailloux.grade.AbsoluteAggregator;
import io.github.oliviercailloux.grade.CompositeMarksTree;
import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.Exam;
import io.github.oliviercailloux.grade.GradeAggregator;
import io.github.oliviercailloux.grade.GradeStructure.DefaultAggregation;
import io.github.oliviercailloux.grade.Mark;
import io.github.oliviercailloux.grade.MarkAggregator;
import io.github.oliviercailloux.grade.MarksTree;
import io.github.oliviercailloux.grade.MaxAggregator;
import io.github.oliviercailloux.grade.NormalizingStaticWeighter;
import io.github.oliviercailloux.grade.ParametricWeighter;
import io.github.oliviercailloux.grade.StaticWeighter;
import io.github.oliviercailloux.grade.old.GradeStructure;
import jakarta.json.Json;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.JsonValue.ValueType;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.adapter.JsonbAdapter;
import jakarta.json.bind.annotation.JsonbCreator;
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

	public static enum MarkAggregatorType {
		ParametricWeighter, NormalizingStaticWeighter, StaticWeighter, AbsoluteAggregator, MaxAggregator;
	}

	@JsonbPropertyOrder({ "type", "multiplied", "weighting", "weights" })
	public static record GenericMarkAggregator(MarkAggregatorType type, Optional<Criterion> multiplied,
			Optional<Criterion> weighting, Optional<Map<Criterion, Double>> weights) {
		@JsonbCreator
		public GenericMarkAggregator(MarkAggregatorType type, Optional<Criterion> multiplied,
				Optional<Criterion> weighting, Optional<Map<Criterion, Double>> weights) {
			this.type = type;
			this.multiplied = multiplied;
			this.weighting = weighting;
			this.weights = weights;

			final boolean hasTwoCrits = (multiplied.isPresent() && weighting.isPresent());
			final boolean hasNoCrits = (multiplied.isEmpty() && weighting.isEmpty());
			final boolean hasWeights = weights.isPresent();

			checkArgument((type == MarkAggregatorType.ParametricWeighter) == hasTwoCrits);
			checkArgument((type != MarkAggregatorType.ParametricWeighter) == hasNoCrits);
			checkArgument((type == MarkAggregatorType.NormalizingStaticWeighter
					|| type == MarkAggregatorType.StaticWeighter) == hasWeights);
			checkArgument((type == MarkAggregatorType.AbsoluteAggregator
					|| type == MarkAggregatorType.MaxAggregator) == (!hasWeights && hasNoCrits));
		}

		public GenericMarkAggregator(MarkAggregatorType type) {
			this(type, Optional.empty(), Optional.empty(), Optional.empty());
		}

		public GenericMarkAggregator(Criterion multiplied, Criterion weighting) {
			this(MarkAggregatorType.ParametricWeighter, Optional.of(multiplied), Optional.of(weighting),
					Optional.empty());
		}

		public GenericMarkAggregator(MarkAggregatorType type, Map<Criterion, Double> weights) {
			this(type, Optional.empty(), Optional.empty(), Optional.of(weights));
		}
	}

	private static MarksTree asMarksTree(JsonObject gradeObject) {
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
		final ImmutableMap<Criterion, MarksTree> subs = gradeObject.keySet().stream()
				.collect(ImmutableMap.toImmutableMap(Criterion::given, s -> asMarksTree(gradeObject.getJsonObject(s))));
		return MarksTree.composite(subs);
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

	private static final class JsonAdapterMarksTree
			implements JsonbAdapter<CompositeMarksTree, Map<String, MarksTree>> {
		@Override
		public Map<String, MarksTree> adaptToJson(CompositeMarksTree grade) {
			checkArgument(grade.isComposite());
			return grade.getCriteria().stream()
					.collect(ImmutableMap.toImmutableMap(Criterion::getName, grade::getTree));
		}

		@Override
		public CompositeMarksTree adaptFromJson(Map<String, MarksTree> structure) {
			return (CompositeMarksTree) MarksTree.composite(
					structure.keySet().stream().collect(ImmutableMap.toImmutableMap(Criterion::given, structure::get)));
		}
	}

	private static final class JsonAdapterJsonToMarksTree implements JsonbAdapter<MarksTree, JsonObject> {
		@Override
		public JsonObject adaptToJson(MarksTree tree) {
			return null;
		}

		@Override
		public MarksTree adaptFromJson(JsonObject tree) {
			return asMarksTree(tree);
		}
	}

	private static final class JsonAdapterMarkAggregator
			implements JsonbAdapter<MarkAggregator, GenericMarkAggregator> {
		@Override
		public GenericMarkAggregator adaptToJson(MarkAggregator aggregator) {
			if (aggregator instanceof ParametricWeighter p) {
				return new GenericMarkAggregator(p.multipliedCriterion(), p.weightingCriterion());
			}
			if (aggregator instanceof NormalizingStaticWeighter w) {
				return new GenericMarkAggregator(MarkAggregatorType.NormalizingStaticWeighter, w.weights());
			}
			if (aggregator instanceof StaticWeighter w) {
				return new GenericMarkAggregator(MarkAggregatorType.StaticWeighter, w.weights());
			}
			if (aggregator instanceof AbsoluteAggregator) {
				return new GenericMarkAggregator(MarkAggregatorType.AbsoluteAggregator);
			}
			if (aggregator instanceof MaxAggregator) {
				return new GenericMarkAggregator(MarkAggregatorType.MaxAggregator);
			}
			throw new VerifyException();
		}

		@Override
		public MarkAggregator adaptFromJson(GenericMarkAggregator from) {
			return switch (from.type) {
			case ParametricWeighter -> ParametricWeighter.given(from.multiplied.orElseThrow(),
					from.weighting.orElseThrow());
			case NormalizingStaticWeighter -> NormalizingStaticWeighter.given(from.weights.orElseThrow());
			case StaticWeighter -> StaticWeighter.given(from.weights.orElseThrow());
			case AbsoluteAggregator -> AbsoluteAggregator.INSTANCE;
			case MaxAggregator -> MaxAggregator.INSTANCE;
			};
		}
	}

	private static final class JsonAdapterExam
			implements JsonbAdapter<ImmutableMap<GitHubUsername, MarksTree>, Map<String, MarksTree>> {
		@Override
		public Map<String, MarksTree> adaptToJson(ImmutableMap<GitHubUsername, MarksTree> grades) {
			return grades.keySet().stream()
					.collect(ImmutableMap.toImmutableMap(GitHubUsername::getUsername, grades::get));
		}

		@Override
		public ImmutableMap<GitHubUsername, MarksTree> adaptFromJson(Map<String, MarksTree> grades) {
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

	public static String toJson(MarkAggregator aggregator) {
		final Jsonb jsonb = JsonHelper.getJsonb(new JsonCriterionToString(), new JsonMapAdapter<Double>() {
		}, new JsonAdapterMarkAggregator());
		return jsonb.toJson(aggregator);
	}

	public static MarkAggregator asMarkAggregator(String jsonAggregator) {
		final Jsonb jsonb = JsonHelper.getJsonb(new JsonCriterionToString(), new JsonMapAdapter<Double>() {
		}, new JsonAdapterMarkAggregator());
		return jsonb.fromJson(jsonAggregator, MarkAggregator.class);
	}

	public static String toJson(GradeAggregator aggregator) {
		final Jsonb jsonb = JsonHelper.getJsonb(new JsonCriterionToString(), new JsonMapAdapter<Double>() {
		}, new JsonMapAdapter<GradeStructure>() {
		}, new JsonAdapterGradeStructure());
		return jsonb.toJson(aggregator);
	}

	public static GradeStructure asStructure(String structureString) {
		final Jsonb jsonb = JsonHelper.getJsonb(new JsonCriterion(), new JsonMapAdapter<Double>() {
		}, new JsonMapAdapter<GradeStructure>() {
		}, new JsonAdapterGradeStructure());
		return jsonb.fromJson(structureString, GradeStructure.class);
	}

	public static String toJson(MarksTree marksTree) {
		final Jsonb jsonb = JsonHelper.getJsonb(new JsonCriterionToString(), new JsonMapAdapter<MarksTree>() {
		}, new JsonAdapterMarksTree());
		return jsonb.toJson(marksTree);
	}

	public static MarksTree asMarksTree(String gradeString) {
		final JsonObject l0 = Json.createReader(new StringReader(gradeString)).readObject();
		return asMarksTree(l0);
	}

	public static String toJson(Exam exam) {
		final Jsonb jsonb = JsonHelper.getJsonb(new JsonCriterionToString(), new JsonMapAdapter<Double>() {
		}, new JsonMapAdapter<GradeStructure>() {
		}, new JsonAdapterGradeStructure(), new JsonMapAdapter<MarksTree>() {
		}, new JsonAdapterMarksTree(), new JsonAdapterExam());
		return jsonb.toJson(exam);
	}

	public static Exam asExam(String examString) {
		final Jsonb jsonb = JsonHelper.getJsonb(new JsonCriterionToString(), new JsonMapAdapter<Double>() {
		}, new JsonMapAdapter<GradeStructure>() {
		}, new JsonAdapterGradeStructure(), new JsonMapAdapter<MarksTree>() {
		}, new JsonAdapterMarksTree(), new JsonAdapterExam(), new JsonAdapterJsonToMarksTree());
		return jsonb.fromJson(examString, Exam.class);
	}
}
