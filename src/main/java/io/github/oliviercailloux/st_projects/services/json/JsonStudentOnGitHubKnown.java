package io.github.oliviercailloux.st_projects.services.json;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.bind.adapter.JsonbAdapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.oliviercailloux.json.JsonbUtils;
import io.github.oliviercailloux.json.PrintableJsonObject;
import io.github.oliviercailloux.json.PrintableJsonObjectFactory;
import io.github.oliviercailloux.st_projects.model.StudentOnGitHubKnown;
import io.github.oliviercailloux.st_projects.model.StudentOnMyCourse;

public class JsonStudentOnGitHubKnown {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(JsonStudentOnGitHubKnown.class);

	public static PrintableJsonObject asJson(StudentOnGitHubKnown student) {
		final PrintableJsonObject mcJson = JsonbUtils.toJsonObject(student.asStudentOnMyCourse());
		LOGGER.debug("Created {}.", mcJson);
		final JsonObject json = Json.createObjectBuilder().add("gitHubUsername", student.getGitHubUsername())
				.addAll(Json.createObjectBuilder(mcJson)).build();
		return PrintableJsonObjectFactory.wrap(json);
	}

	public static StudentOnGitHubKnown asStudentOnGitHubKnown(JsonObject json) {
		final String gitHubUsername = json.getString("gitHubUsername");
		final StudentOnMyCourse mc = JsonbUtils.fromJson(json.toString(), StudentOnMyCourse.class);
		return StudentOnGitHubKnown.with(mc, gitHubUsername);
	}

	public static JsonbAdapter<StudentOnGitHubKnown, JsonObject> asAdapter() {
		return new JsonbAdapter<StudentOnGitHubKnown, JsonObject>() {
			@Override
			public JsonObject adaptToJson(StudentOnGitHubKnown obj) throws Exception {
				return asJson(obj);
			}

			@Override
			public StudentOnGitHubKnown adaptFromJson(JsonObject obj) throws Exception {
				return asStudentOnGitHubKnown(obj);
			}
		};
	}
}
