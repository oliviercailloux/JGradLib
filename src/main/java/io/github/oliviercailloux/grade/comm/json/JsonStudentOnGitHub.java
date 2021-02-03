package io.github.oliviercailloux.grade.comm.json;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.bind.adapter.JsonbAdapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.oliviercailloux.git.git_hub.model.GitHubUsername;
import io.github.oliviercailloux.grade.comm.StudentOnGitHub;
import io.github.oliviercailloux.grade.comm.StudentOnGitHubKnown;
import io.github.oliviercailloux.json.PrintableJsonObject;
import io.github.oliviercailloux.json.PrintableJsonObjectFactory;

public class JsonStudentOnGitHub {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(JsonStudentOnGitHub.class);

	public static PrintableJsonObject asJson(StudentOnGitHub student) {
		if (student.hasInstitutionalPart()) {
			return JsonStudentOnGitHubKnown.asJson(student.asStudentOnGitHubKnown());
		}

		final JsonObject json = Json.createObjectBuilder()
				.add("gitHubUsername", student.getGitHubUsername().getUsername()).build();
		return PrintableJsonObjectFactory.wrapObject(json);
	}

	public static StudentOnGitHub asStudentOnGitHub(JsonObject json) {
		if (json.containsKey("myCourseUsername")) {
			final StudentOnGitHubKnown known = JsonStudentOnGitHubKnown.asStudentOnGitHubKnown(json);
			return StudentOnGitHub.with(known.getGitHubUsername(), known.getInstitutionalStudent());
		}
		final String gitHubUsername = json.getString("gitHubUsername");
		return StudentOnGitHub.with(GitHubUsername.given(gitHubUsername));
	}

	public static JsonbAdapter<StudentOnGitHub, JsonObject> asAdapter() {
		return new JsonbAdapter<>() {
			@Override
			public JsonObject adaptToJson(StudentOnGitHub obj) throws Exception {
				return asJson(obj);
			}

			@Override
			public StudentOnGitHub adaptFromJson(JsonObject obj) throws Exception {
				return asStudentOnGitHub(obj);
			}
		};
	}
}