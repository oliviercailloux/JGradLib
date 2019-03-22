package io.github.oliviercailloux.grade.mycourse.json;

import java.util.List;

import javax.json.JsonObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.oliviercailloux.grade.mycourse.StudentOnMyCourse;
import io.github.oliviercailloux.json.JsonbUtils;
import io.github.oliviercailloux.json.PrintableJsonObject;
import io.github.oliviercailloux.json.PrintableJsonValue;

public class JsonStudentOnMyCourse {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(JsonStudentOnMyCourse.class);

	public static PrintableJsonObject asJson(StudentOnMyCourse student) {
		return JsonbUtils.toJsonObject(student);
	}

	public static PrintableJsonValue asJsonFromList(List<StudentOnMyCourse> students) {
		return JsonbUtils.toJsonValue(students);
	}

	public static StudentOnMyCourse asStudentOnMyCourse(JsonObject json) {
		return JsonbUtils.fromJson(json.toString(), StudentOnMyCourse.class);
	}
}
