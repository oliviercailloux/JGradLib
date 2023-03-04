package io.github.oliviercailloux.grade.format.json;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Verify.verifyNotNull;

import com.google.common.base.Predicates;
import com.google.common.base.VerifyException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import io.github.oliviercailloux.git.git_hub.model.GitHubUsername;
import io.github.oliviercailloux.grade.AbsoluteAggregator;
import io.github.oliviercailloux.grade.CompositeMarksTree;
import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.Exam;
import io.github.oliviercailloux.grade.Grade;
import io.github.oliviercailloux.grade.GradeAggregator;
import io.github.oliviercailloux.grade.Mark;
import io.github.oliviercailloux.grade.MarkAggregator;
import io.github.oliviercailloux.grade.MarksTree;
import io.github.oliviercailloux.grade.MaxAggregator;
import io.github.oliviercailloux.grade.MinAggregator;
import io.github.oliviercailloux.grade.NormalizingStaticWeighter;
import io.github.oliviercailloux.grade.OwaAggregator;
import io.github.oliviercailloux.grade.ParametricWeighter;
import io.github.oliviercailloux.grade.StaticWeighter;
import io.github.oliviercailloux.grade.VoidAggregator;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.JsonValue.ValueType;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.adapter.JsonbAdapter;
import jakarta.json.bind.annotation.JsonbCreator;
import jakarta.json.bind.annotation.JsonbPropertyOrder;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JsonSimpleGrade {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(JsonSimpleGrade.class);

	public static enum MarkAggregatorType {
		ParametricWeighter, VoidAggregator, NormalizingStaticWeighter, StaticWeighter, AbsoluteAggregator,
		MinAggregator, MaxAggregator, OwaAggregator;
	}

	@JsonbPropertyOrder({ "type", "multiplied", "weighting", "weights" })
	public static record GenericMarkAggregator(MarkAggregatorType type, Optional<Criterion> multiplied,
			Optional<Criterion> weighting, Optional<Map<Criterion, Double>> weights,
			/**
			 * Should think about coherence with map (which is optional instead of empty to
			 * mark absence); Eclipse does not like an Optional here.
			 */
			Optional<List<Double>> simpleWeights) {

		@JsonbCreator
		public GenericMarkAggregator(MarkAggregatorType type, Optional<Criterion> multiplied,
				Optional<Criterion> weighting, Optional<Map<Criterion, Double>> weights,
				Optional<List<Double>> simpleWeights) {
			this.type = type;
			this.multiplied = multiplied;
			this.weighting = weighting;
			this.weights = weights;
			this.simpleWeights = simpleWeights;

			final boolean hasTwoCrits = (multiplied.isPresent() && weighting.isPresent());
			final boolean hasNoCrits = (multiplied.isEmpty() && weighting.isEmpty());
			final boolean hasWeights = weights.isPresent();
			final boolean hasSimpleWeights = simpleWeights != null && !simpleWeights.isEmpty();

			checkArgument((type == MarkAggregatorType.ParametricWeighter) == hasTwoCrits);
			checkArgument((type != MarkAggregatorType.ParametricWeighter) == hasNoCrits);
			checkArgument((type == MarkAggregatorType.NormalizingStaticWeighter
					|| type == MarkAggregatorType.StaticWeighter) == hasWeights);
			checkArgument((type == MarkAggregatorType.OwaAggregator) == hasSimpleWeights);
		}

		public GenericMarkAggregator(MarkAggregatorType type) {
			this(type, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
		}

		public GenericMarkAggregator(Criterion multiplied, Criterion weighting) {
			this(MarkAggregatorType.ParametricWeighter, Optional.of(multiplied), Optional.of(weighting),
					Optional.empty(), Optional.empty());
		}

		public GenericMarkAggregator(MarkAggregatorType type, Map<Criterion, Double> weights) {
			this(type, Optional.empty(), Optional.empty(), Optional.of(weights), Optional.empty());
		}

		public GenericMarkAggregator(List<Double> simpleWeights) {
			this(MarkAggregatorType.OwaAggregator, Optional.empty(), Optional.empty(), Optional.empty(),
					Optional.of(simpleWeights));
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

	private static final class JsonAdapterGradeAggregator
			implements JsonbAdapter<GradeAggregator, GenericGradeAggregator> {
		@Override
		public GenericGradeAggregator adaptToJson(GradeAggregator aggregator) {
			final ImmutableMap<Criterion, ? extends GradeAggregator> subs = aggregator.getSpecialSubAggregators();
			final Map<Criterion, ? extends GradeAggregator> nonTrivialSubs = Maps.filterEntries(subs,
					e -> !e.getValue().equals(GradeAggregator.TRIVIAL));
			final Map<Criterion, GenericGradeAggregator> transformedNonTrivialSubs = Maps
					.transformValues(nonTrivialSubs, this::adaptToJson);
			final GradeAggregator defaultSub = aggregator.getDefaultSubAggregator();
			verifyNotNull(defaultSub);
			final Optional<GradeAggregator> nonTrivialDefaultSub = defaultSub.equals(GradeAggregator.TRIVIAL)
					? Optional.empty()
					: Optional.of(defaultSub);
			final Optional<GenericGradeAggregator> transformedNonTrivialDefaultSub = nonTrivialDefaultSub
					.map(this::adaptToJson);
			return new GenericGradeAggregator(aggregator.getMarkAggregator(), transformedNonTrivialDefaultSub,
					JsonMapAdapter.toStringKeys(transformedNonTrivialSubs));
		}

		@Override
		public GradeAggregator adaptFromJson(GenericGradeAggregator aggregator) {
			final Map<Criterion, GenericGradeAggregator> subs = JsonMapAdapter.toCriterionKeys(aggregator.subs);
			final Map<Criterion, GradeAggregator> transformedSubs = Maps.transformValues(subs, this::adaptFromJson);
			final MarkAggregator markAggregator = aggregator.markAggregator;
			if (markAggregator.equals(VoidAggregator.INSTANCE)) {
				checkArgument(subs.isEmpty());
				return GradeAggregator.TRIVIAL;
			}
			return GradeAggregator.given(markAggregator, transformedSubs,
					aggregator.defaultSub.map(this::adaptFromJson).orElse(GradeAggregator.TRIVIAL));
		}
	}

	private static final class JsonAdapterGrade implements JsonbAdapter<Grade, GenericGrade> {

		@Override
		public GenericGrade adaptToJson(Grade grade) throws Exception {
			return new GenericGrade(grade.toMarksTree(), grade.toAggregator());
		}

		@Override
		public Grade adaptFromJson(GenericGrade grade) throws Exception {
			return Grade.given(grade.aggregator, grade.marks);
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
			if (aggregator instanceof VoidAggregator) {
				return new GenericMarkAggregator(MarkAggregatorType.VoidAggregator);
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
			if (aggregator instanceof MinAggregator) {
				return new GenericMarkAggregator(MarkAggregatorType.MinAggregator);
			}
			if (aggregator instanceof MaxAggregator) {
				return new GenericMarkAggregator(MarkAggregatorType.MaxAggregator);
			}
			if (aggregator instanceof OwaAggregator o) {
				return new GenericMarkAggregator(o.weights());
			}
			throw new VerifyException();
		}

		@Override
		public MarkAggregator adaptFromJson(GenericMarkAggregator from) {
			return switch (from.type) {
			case ParametricWeighter ->
				ParametricWeighter.given(from.multiplied.orElseThrow(), from.weighting.orElseThrow());
			case VoidAggregator -> VoidAggregator.INSTANCE;
			case NormalizingStaticWeighter -> NormalizingStaticWeighter.given(from.weights.orElseThrow());
			case StaticWeighter -> StaticWeighter.given(from.weights.orElseThrow());
			case AbsoluteAggregator -> AbsoluteAggregator.INSTANCE;
			case MinAggregator -> MinAggregator.INSTANCE;
			case MaxAggregator -> MaxAggregator.INSTANCE;
			case OwaAggregator -> OwaAggregator.given(from.simpleWeights.orElse(ImmutableList.of()));
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

	@JsonbPropertyOrder({ "markAggregator", "defaultSub", "subs" })
	public static record GenericGradeAggregator(MarkAggregator markAggregator,
			Optional<GenericGradeAggregator> defaultSub, Map<String, GenericGradeAggregator> subs) {
	}

	@JsonbPropertyOrder({ "marks", "aggregator" })
	public static record GenericGrade(MarksTree marks, GradeAggregator aggregator) {

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
		}, new JsonAdapterMarkAggregator(), new JsonAdapterGradeAggregator());
		return jsonb.toJson(aggregator);
	}

	public static String toJson(Set<GradeAggregator> aggregators) {
		final Jsonb jsonb = JsonHelper.getJsonb(new JsonCriterionToString(), new JsonMapAdapter<Double>() {
		}, new JsonAdapterMarkAggregator(), new JsonAdapterGradeAggregator());
		return jsonb.toJson(aggregators);
	}

	public static GradeAggregator asAggregator(String aggregatorString) {
		final Jsonb jsonb = JsonHelper.getJsonb(new JsonCriterion(), new JsonMapAdapter<Double>() {
		}, new JsonAdapterMarkAggregator(), new JsonAdapterGradeAggregator());
		return jsonb.fromJson(aggregatorString, GradeAggregator.class);
	}

	public static String toJson(MarksTree marksTree) {
		final Jsonb jsonb = JsonHelper.getJsonb(new JsonCriterionToString(), new JsonMapAdapter<MarksTree>() {
		}, new JsonAdapterMarksTree());
		return jsonb.toJson(marksTree);
	}

	public static MarksTree asMarksTree(String treeString) {
		final Jsonb jsonb = JsonHelper.getJsonb(new JsonAdapterJsonToMarksTree());
		return jsonb.fromJson(treeString, MarksTree.class);
	}

	public static String toJson(Grade grade) {
		final Jsonb jsonb = JsonHelper.getJsonb(new JsonCriterionToString(), new JsonMapAdapter<MarksTree>() {
		}, new JsonAdapterMarksTree(), new JsonMapAdapter<Double>() {
		}, new JsonAdapterMarkAggregator(), new JsonAdapterGradeAggregator(), new JsonAdapterGrade());
		return jsonb.toJson(grade);
	}

	public static Grade asGrade(String gradeString) {
		final Jsonb jsonb = JsonHelper.getJsonb(new JsonAdapterJsonToMarksTree(), new JsonCriterion(),
				new JsonMapAdapter<Double>() {
				}, new JsonAdapterMarkAggregator(), new JsonAdapterGradeAggregator(), new JsonAdapterGrade());
		return jsonb.fromJson(gradeString, Grade.class);
	}

	public static String toJson(Exam exam) {
		final Jsonb jsonb = JsonHelper.getJsonb(new JsonCriterionToString(), new JsonMapAdapter<MarksTree>() {
		}, new JsonAdapterMarksTree(), new JsonMapAdapter<Double>() {
		}, new JsonAdapterMarkAggregator(), new JsonAdapterGradeAggregator(), new JsonAdapterGrade(),
				new JsonAdapterExam());
		return jsonb.toJson(exam);
	}

	public static Exam asExam(String examString) {
		final Jsonb jsonb = JsonHelper.getJsonb(new JsonAdapterJsonToMarksTree(), new JsonCriterion(),
				new JsonMapAdapter<Double>() {
				}, new JsonAdapterMarkAggregator(), new JsonAdapterGradeAggregator(), new JsonAdapterGrade(),
				new JsonAdapterExam());
		return jsonb.fromJson(examString, Exam.class);
	}
}
