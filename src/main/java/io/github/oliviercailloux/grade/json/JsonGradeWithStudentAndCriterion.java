package io.github.oliviercailloux.grade.json;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;
import javax.json.bind.adapter.JsonbAdapter;

import com.google.common.collect.ImmutableSet;

import io.github.oliviercailloux.grade.AnonymousGrade;
import io.github.oliviercailloux.grade.CriterionAndMark;
import io.github.oliviercailloux.grade.GradeWithStudentAndCriterion;
import io.github.oliviercailloux.grade.mycourse.StudentOnGitHub;
import io.github.oliviercailloux.grade.mycourse.StudentOnGitHubKnown;
import io.github.oliviercailloux.grade.mycourse.json.JsonStudentOnGitHub;
import io.github.oliviercailloux.grade.mycourse.json.JsonStudentOnGitHubKnown;
import io.github.oliviercailloux.json.JsonbUtils;
import io.github.oliviercailloux.json.PrintableJsonObject;
import io.github.oliviercailloux.json.PrintableJsonObjectFactory;
import io.github.oliviercailloux.json.PrintableJsonValue;
import io.github.oliviercailloux.json.PrintableJsonValueFactory;

public class JsonGradeWithStudentAndCriterion {
	public static PrintableJsonObject asJson(StudentOnGitHubKnown student, AnonymousGrade grade) {
		final JsonObjectBuilder builder = Json.createObjectBuilder();
		builder.add("student", JsonStudentOnGitHubKnown.asJson(student));
		final JsonArrayBuilder marksBuilder = Json.createArrayBuilder();
		{
			final ImmutableSet<GradeWithStudentAndCriterion> marks = grade.getMarks().values();
			for (GradeWithStudentAndCriterion mark : marks) {
				final PrintableJsonObject markJson = JsonMarkWithCriterion.asJson((CriterionAndMark) mark);
				marksBuilder.add(Json.createObjectBuilder(markJson));
			}
		}
		builder.add("marks", marksBuilder);
		return PrintableJsonObjectFactory.wrapObject(builder.build());
//		return JsonUtils.serializeWithJsonB(grade, JsonStudentOnGitHub.asAdapter(), JsonCriterion.asAdapter());
	}

	public static PrintableJsonValue asJsonArrayWithStudents(Map<StudentOnGitHubKnown, AnonymousGrade> grades) {
		final JsonArrayBuilder builder = Json.createArrayBuilder();
		for (Entry<StudentOnGitHubKnown, AnonymousGrade> entry : grades.entrySet()) {
			final StudentOnGitHubKnown student = entry.getKey();
			final AnonymousGrade grade = entry.getValue();
			builder.add(asJson(student, grade));
		}
		return PrintableJsonValueFactory.wrapValue(builder.build());
	}

	public static GradeWithStudentAndCriterion asGrade(String json) {
		/**
		 * We proceed manually for now: the below code <a
		 * href=https://github.com/eclipse-ee4j/yasson/issues/201>bugs</a>.
		 */
//		return JsonUtils.deserializeWithJsonB(json, StudentGrade.class, JsonStudentOnGitHub.asAdapter(),
//				JsonCriterion.asAdapter());
		return asGrade(PrintableJsonObjectFactory.wrapString(json));
	}

	public static ImmutableSet<GradeWithStudentAndCriterion> asGrades(String json) {
		@SuppressWarnings("serial")
//		final Set<Grade> targetType = new HashSet<>() {
//			/** Just for type! */
//		};
		final List<GradeWithStudentAndCriterion> targetType = new ArrayList<>() {
			/** Just for type! */
		};
		/**
		 * Need to read into a list then only copy into a set: if read directly into a
		 * set, Json does not maintain the ordering.
		 */
		final List<GradeWithStudentAndCriterion> read = JsonbUtils.fromJson(json,
				targetType.getClass().getGenericSuperclass(), JsonGradeWithStudentAndCriterion.asAdapter());
		return ImmutableSet.copyOf(read);
	}

	public static GradeWithStudentAndCriterion asGrade(JsonObject json) {
//		return asGrade(json.toString());
		final StudentOnGitHub student = JsonStudentOnGitHub.asStudentOnGitHub(json.getJsonObject("student"));
		final ImmutableSet<CriterionAndMark> marks;
		{
			final ImmutableSet.Builder<CriterionAndMark> marksBuilder = ImmutableSet.builder();
			final JsonArray jsonMarks = json.getJsonArray("marks");
			for (JsonValue jsonMark : jsonMarks) {
				final JsonObject jsonMarkObj = jsonMark.asJsonObject();
				final CriterionAndMark mark = JsonMarkWithCriterion.asMark(jsonMarkObj);
				marksBuilder.add(mark);
			}
			marks = marksBuilder.build();
		}
		return GradeWithStudentAndCriterion.of(student, marks);
	}

	public static JsonbAdapter<GradeWithStudentAndCriterion, JsonObject> asAdapter() {
		return new JsonbAdapter<>() {
			@Override
			public JsonObject adaptToJson(GradeWithStudentAndCriterion obj) throws Exception {
				return asJson(obj);
			}

			@Override
			public GradeWithStudentAndCriterion adaptFromJson(JsonObject obj) throws Exception {
				return asGrade(obj);
			}
		};
	}

	public static PrintableJsonObject asJson(GradeWithStudentAndCriterion grade) {
		final JsonObjectBuilder builder = Json.createObjectBuilder();
		builder.add("student", JsonStudentOnGitHub.asJson(grade.getStudent()));
		final JsonArrayBuilder marksBuilder = Json.createArrayBuilder();
		{
			final ImmutableSet<GradeWithStudentAndCriterion> marks = grade.getMarks().values();
			for (GradeWithStudentAndCriterion mark : marks) {
				final PrintableJsonObject markJson = JsonMarkWithCriterion.asJson((CriterionAndMark) mark);
				marksBuilder.add(Json.createObjectBuilder(markJson));
			}
		}
		builder.add("marks", marksBuilder);
		return PrintableJsonObjectFactory.wrapObject(builder.build());
		// return JsonUtils.serializeWithJsonB(grade, JsonStudentOnGitHub.asAdapter(),
		// JsonCriterion.asAdapter());
	}

	public static PrintableJsonValue asJsonArray(Collection<GradeWithStudentAndCriterion> grades) {
		return JsonbUtils.toJsonValue(grades, JsonGradeWithStudentAndCriterion.asAdapter());
	}
}
