package io.github.oliviercailloux.mycourse.json;

import javax.json.JsonObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.oliviercailloux.json.JsonbUtils;
import io.github.oliviercailloux.json.PrintableJsonObject;
import io.github.oliviercailloux.mycourse.StudentOnMyCourse;

public class JsonStudentOnMyCourse {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(JsonStudentOnMyCourse.class);

	public static PrintableJsonObject asJson(StudentOnMyCourse student) {
		return JsonbUtils.toJsonObject(student);
	}

	public static StudentOnMyCourse asStudentOnMyCourse(JsonObject json) {
		return JsonbUtils.fromJson(json.toString(), StudentOnMyCourse.class);
	}
}
