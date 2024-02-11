package io.github.oliviercailloux.grade.comm.json;

import io.github.oliviercailloux.git.git_hub.model.GitHubUsername;
import io.github.oliviercailloux.grade.comm.InstitutionalStudent;
import io.github.oliviercailloux.grade.comm.StudentOnGitHubKnown;
import io.github.oliviercailloux.json.JsonbUtils;
import io.github.oliviercailloux.json.PrintableJsonObject;
import io.github.oliviercailloux.json.PrintableJsonObjectFactory;
import io.github.oliviercailloux.json.PrintableJsonValue;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.bind.adapter.JsonbAdapter;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JsonStudentOnGitHubKnown {
  @SuppressWarnings("unused")
  private static final Logger LOGGER = LoggerFactory.getLogger(JsonStudentOnGitHubKnown.class);

  public static PrintableJsonObject asJson(StudentOnGitHubKnown student) {
    final PrintableJsonObject mcJson = JsonbUtils.toJsonObject(student.getInstitutionalStudent());
    LOGGER.debug("Created {}.", mcJson);
    final JsonObject json = Json.createObjectBuilder()
        .add("gitHubUsername", student.getGitHubUsername().getUsername())
        .addAll(Json.createObjectBuilder(mcJson)).build();
    return PrintableJsonObjectFactory.wrapObject(json);
  }

  public static PrintableJsonValue asJsonFromList(List<StudentOnGitHubKnown> students) {
    return JsonbUtils.toJsonValue(students, asAdapter());
  }

  public static StudentOnGitHubKnown asStudentOnGitHubKnown(JsonObject json) {
    final GitHubUsername gitHubUsername = GitHubUsername.given(json.getString("gitHubUsername"));
    final InstitutionalStudent mc = JsonbUtils.fromJson(json.toString(), InstitutionalStudent.class);
    return StudentOnGitHubKnown.with(gitHubUsername, mc);
  }

  public static JsonbAdapter<StudentOnGitHubKnown, JsonObject> asAdapter() {
    return new JsonbAdapter<>() {
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
