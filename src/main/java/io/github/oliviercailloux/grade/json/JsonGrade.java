package io.github.oliviercailloux.grade.json;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;
import javax.json.bind.adapter.JsonbAdapter;

import com.google.common.collect.ImmutableSet;

import io.github.oliviercailloux.grade.Grade;
import io.github.oliviercailloux.grade.Mark;
import io.github.oliviercailloux.grade.mycourse.StudentOnGitHub;
import io.github.oliviercailloux.grade.mycourse.json.JsonStudentOnGitHub;
import io.github.oliviercailloux.json.JsonbUtils;
import io.github.oliviercailloux.json.PrintableJsonObject;
import io.github.oliviercailloux.json.PrintableJsonObjectFactory;
import io.github.oliviercailloux.json.PrintableJsonValue;

public class JsonGrade {
	public static PrintableJsonObject asJson(Grade grade) {
		final JsonObjectBuilder builder = Json.createObjectBuilder();
		builder.add("student", JsonStudentOnGitHub.asJson(grade.getStudent()));
		final JsonArrayBuilder marksBuilder = Json.createArrayBuilder();
		{
			final ImmutableSet<Mark> marks = grade.getMarks().values();
			for (Mark mark : marks) {
				final PrintableJsonObject markJson = JsonMark.asJson(mark);
				marksBuilder.add(Json.createObjectBuilder(markJson));
			}
		}
		builder.add("marks", marksBuilder);
		return PrintableJsonObjectFactory.wrapObject(builder.build());
//		return JsonUtils.serializeWithJsonB(grade, JsonStudentOnGitHub.asAdapter(), JsonCriterion.asAdapter());
	}

	public static PrintableJsonValue asJsonArray(Collection<Grade> grades) {
		return JsonbUtils.toJsonValue(grades, JsonGrade.asAdapter());
	}

	public static Grade asGrade(String json) {
		/**
		 * We proceed manually for now: the below code <a
		 * href=https://github.com/eclipse-ee4j/yasson/issues/201>bugs</a>.
		 */
//		return JsonUtils.deserializeWithJsonB(json, StudentGrade.class, JsonStudentOnGitHub.asAdapter(),
//				JsonCriterion.asAdapter());
		return asGrade(PrintableJsonObjectFactory.wrapUnknownStringForm(json));
	}

	public static ImmutableSet<Grade> asGrades(String json) {
		@SuppressWarnings("serial")
//		final Set<Grade> targetType = new HashSet<>() {
//			/** Just for type! */
//		};
		final List<Grade> targetType = new ArrayList<>() {
			/** Just for type! */
		};
		/**
		 * Need to read into a list then only copy into a set: if read directly into a
		 * set, Json does not maintain the ordering.
		 */
		final List<Grade> read = JsonbUtils.fromJson(json, targetType.getClass().getGenericSuperclass(),
				JsonGrade.asAdapter());
		return ImmutableSet.copyOf(read);
	}

	public static Grade asGrade(JsonObject json) {
//		return asGrade(json.toString());
		final StudentOnGitHub student = JsonStudentOnGitHub.asStudentOnGitHub(json.getJsonObject("student"));
		final ImmutableSet<Mark> marks;
		{
			final ImmutableSet.Builder<Mark> marksBuilder = ImmutableSet.builder();
			final JsonArray jsonMarks = json.getJsonArray("marks");
			for (JsonValue jsonMark : jsonMarks) {
				final JsonObject jsonMarkObj = jsonMark.asJsonObject();
				final Mark mark = JsonMark.asMark(jsonMarkObj);
				marksBuilder.add(mark);
			}
			marks = marksBuilder.build();
		}
		return Grade.of(student, marks);
	}

	public static JsonbAdapter<Grade, JsonObject> asAdapter() {
		return new JsonbAdapter<>() {
			@Override
			public JsonObject adaptToJson(Grade obj) throws Exception {
				return asJson(obj);
			}

			@Override
			public Grade adaptFromJson(JsonObject obj) throws Exception {
				return asGrade(obj);
			}
		};
	}
}
