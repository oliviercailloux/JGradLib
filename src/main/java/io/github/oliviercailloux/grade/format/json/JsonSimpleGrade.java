package io.github.oliviercailloux.grade.format.json;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ImmutableMap;
import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.Exam;
import io.github.oliviercailloux.grade.Grade;
import io.github.oliviercailloux.grade.GradeStructure;
import io.github.oliviercailloux.grade.GradeStructure.DefaultAggregation;
import io.github.oliviercailloux.grade.IGrade.GradePath;
import io.github.oliviercailloux.grade.Mark;
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

	private static final class JsonAdapterGrade implements JsonbAdapter<Grade, GradeRecord> {
		@Override
		public GradeRecord adaptToJson(Grade grade) {
			if (grade.isMark()) {
				return new GradeRecord(grade.getMark(GradePath.ROOT));
			}
			return new GradeRecord(
					grade.getCriteria().stream().collect(ImmutableMap.toImmutableMap(c -> c, grade::getGrade)));
		}

		@Override
		public Grade adaptFromJson(GradeRecord structure) {
			final boolean hasPoints = structure.points != null;
			final boolean hasComments = structure.comments != null;
			final boolean hasSubs = structure.grades != null;
			checkArgument(hasPoints == hasComments);
			checkArgument(hasPoints != hasSubs);
			if (hasPoints) {
				return new Mark(structure.points, structure.comments);
			}
			return Grade.composite(structure.grades);
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

	@JsonbPropertyOrder({ "mark", "grades" })
	public static class GradeRecord {
		public Double points;
		public String comments;
		public Map<Criterion, Grade> grades;

		public GradeRecord() {
			points = null;
			comments = null;
			grades = null;
		}

		public GradeRecord(Mark mark) {
			this();
			this.points = mark.points();
			this.comments = mark.comment();
		}

		public GradeRecord(Map<Criterion, Grade> grades) {
			this();
			this.grades = grades;
		}

		public Double getPoints() {
			return points;
		}

		public void setPoints(double points) {
			this.points = points;
		}

		public String getComments() {
			return comments;
		}

		public void setComments(String comments) {
			this.comments = comments;
		}

		public Map<Criterion, Grade> getGrades() {
			return grades;
		}

		public void setGrades(Map<Criterion, Grade> grades) {
			this.grades = grades;
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

	public static String toJson(Grade grade) {
		final Jsonb jsonb = JsonHelper.getJsonb(new JsonCriterion(), new JsonMapAdapter<Double>() {
		}, new JsonMapAdapter<Grade>() {
		}, new JsonAdapterGrade());
		return jsonb.toJson(grade);
	}

	public static String toJson(Exam exam) {
		final Jsonb jsonb = JsonHelper.getJsonb(new JsonCriterion(), new JsonMapAdapter<Double>() {
		}, new JsonMapAdapter<GradeStructure>() {
		}, new JsonAdapterGradeStructure());
		return jsonb.toJson(exam);
	}
}
