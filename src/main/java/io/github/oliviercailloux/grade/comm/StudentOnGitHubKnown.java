package io.github.oliviercailloux.grade.comm;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.MoreObjects;
import io.github.oliviercailloux.email.EmailAddressAndPersonal;
import io.github.oliviercailloux.git.github.model.GitHubUsername;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StudentOnGitHubKnown {
  @SuppressWarnings("unused")
  private static final Logger LOGGER = LoggerFactory.getLogger(StudentOnGitHubKnown.class);

  public static StudentOnGitHubKnown with(GitHubUsername gitHubUsername,
      InstitutionalStudent institutionalStudent) {
    return new StudentOnGitHubKnown(gitHubUsername, institutionalStudent);
  }

  private GitHubUsername gitHubUsername;
  private InstitutionalStudent institutionalStudent;

  private StudentOnGitHubKnown(GitHubUsername gitHubUsername,
      InstitutionalStudent studentOnMyCourse) {
    this.institutionalStudent = checkNotNull(studentOnMyCourse);
    this.gitHubUsername = checkNotNull(gitHubUsername);
  }

  public GitHubUsername getGitHubUsername() {
    return gitHubUsername;
  }

  public int getInstitutionalId() {
    return institutionalStudent.getId();
  }

  public String getInstitutionalUsername() {
    return institutionalStudent.getUsername();
  }

  public String getFirstName() {
    return institutionalStudent.getFirstName();
  }

  public String getLastName() {
    return institutionalStudent.getLastName();
  }

  public EmailAddressAndPersonal getEmail() {
    return institutionalStudent.getEmail();
  }

  public StudentOnGitHub asStudentOnGitHub() {
    return StudentOnGitHub.with(gitHubUsername, institutionalStudent);
  }

  public InstitutionalStudent getInstitutionalStudent() {
    return institutionalStudent;
  }

  @Override
  public boolean equals(Object o2) {
    if (!(o2 instanceof StudentOnGitHubKnown)) {
      return false;
    }
    final StudentOnGitHubKnown s2 = (StudentOnGitHubKnown) o2;
    return gitHubUsername.equals(s2.gitHubUsername)
        && institutionalStudent.equals(s2.institutionalStudent);
  }

  @Override
  public int hashCode() {
    return Objects.hash(gitHubUsername, institutionalStudent);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("GitHub username", gitHubUsername)
        .add("onMyCourse", institutionalStudent).toString();
  }
}
