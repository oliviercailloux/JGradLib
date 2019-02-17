package io.github.oliviercailloux.st_projects.services.json;

import java.util.HashSet;
import java.util.Set;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;
import javax.json.bind.adapter.JsonbAdapter;

import com.google.common.collect.ImmutableSet;

import io.github.oliviercailloux.json.JsonbUtils;
import io.github.oliviercailloux.json.PrintableJsonObject;
import io.github.oliviercailloux.json.PrintableJsonObjectFactory;
import io.github.oliviercailloux.json.PrintableJsonValue;
import io.github.oliviercailloux.mycourse.StudentOnGitHub;
import io.github.oliviercailloux.mycourse.json.JsonStudentOnGitHub;
import io.github.oliviercailloux.st_projects.model.Mark;
import io.github.oliviercailloux.st_projects.model.Grade;

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

	public static PrintableJsonValue asJsonArray(Set<Grade> grades) {
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
		final Set<Grade> targetSet = new HashSet<Grade>() {
			/** Just for type! */
		};
		final Set<Grade> read = JsonbUtils.fromJson(json, targetSet.getClass().getGenericSuperclass(),
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
		return new JsonbAdapter<Grade, JsonObject>() {
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
