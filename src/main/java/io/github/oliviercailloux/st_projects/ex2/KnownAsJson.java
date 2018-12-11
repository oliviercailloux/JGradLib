package io.github.oliviercailloux.st_projects.ex2;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import javax.json.bind.JsonbConfig;
import javax.json.bind.adapter.JsonbAdapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.oliviercailloux.st_projects.model.StudentOnGitHubKnown;
import io.github.oliviercailloux.st_projects.model.StudentOnMyCourse;
import io.github.oliviercailloux.st_projects.utils.JsonUtils;

public class KnownAsJson implements JsonbAdapter<StudentOnGitHubKnown, JsonObject> {

	@Override
	public JsonObject adaptToJson(StudentOnGitHubKnown student) {
		final JsonObject mcJson = JsonUtils.asJsonBObject(student.asStudentOnMyCourse());
		LOGGER.info("Created {}.", mcJson);
		return Json.createObjectBuilder().add("gitHubUsername", student.getGitHubUsername())
				.addAll(Json.createObjectBuilder(mcJson)).build();
	}

	@Override
	public StudentOnGitHubKnown adaptFromJson(JsonObject studentKnown) {
		final String gitHubUsername = studentKnown.getString("gitHubUsername");
		final StudentOnMyCourse mc;
		try (Jsonb jsonb = JsonbBuilder.create(new JsonbConfig())) {
			mc = jsonb.fromJson(studentKnown.toString(), StudentOnMyCourse.class);
			LOGGER.info("Deserialized: {}.", mc);
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}

		return StudentOnGitHubKnown.with(mc, gitHubUsername);
	}

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(KnownAsJson.class);

}
