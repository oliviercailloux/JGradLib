package io.github.oliviercailloux.grade.comm.json;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.common.collect.ImmutableMap;
import io.github.oliviercailloux.email.EmailAddress;
import io.github.oliviercailloux.git.github.model.GitHubUsername;
import io.github.oliviercailloux.grade.comm.InstitutionalStudent;
import io.github.oliviercailloux.grade.comm.StudentOnGitHub;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class JsonStudentsReaderTests {
  @SuppressWarnings("unused")
  private static final Logger LOGGER = LoggerFactory.getLogger(JsonStudentsReaderTests.class);

  @Test
  void test() throws Exception {
    final String json = Files.readString(Path.of(getClass().getResource("Students.json").toURI()));
    final JsonStudents read = JsonStudents.from(json);
    assertEquals(getInst(), read.getInstitutionalStudentsById());
    assertEquals(getGH(), read.getStudentsByGitHubUsername());
  }

  private ImmutableMap<Integer, InstitutionalStudent> getInst() {
    return ImmutableMap.of(1, getInst1(), 2190401, getInst2());
  }

  private InstitutionalStudent getInst1() {
    return InstitutionalStudent.withU(1, "u", "f", "l", EmailAddress.given("e@example.com"));
  }

  private InstitutionalStudent getInst2() {
    return InstitutionalStudent.withU(2190401, "unknown", "", "last",
        EmailAddress.given("first.last@example.eu"));
  }

  private ImmutableMap<GitHubUsername, StudentOnGitHub> getGH() {
    final GitHubUsername gu = GitHubUsername.given("g u");
    final GitHubUsername o = GitHubUsername.given("only g");
    return ImmutableMap.of(gu, StudentOnGitHub.with(gu, getInst2()), o, StudentOnGitHub.with(o));
  }
}
