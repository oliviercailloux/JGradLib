package io.github.oliviercailloux.grade.comm;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.MoreObjects;
import io.github.oliviercailloux.email.EmailAddressAndPersonal;
import io.github.oliviercailloux.git.github.model.GitHubUsername;
import java.util.Objects;
import java.util.Optional;

public class StudentOnGitHub {
  public static StudentOnGitHub with(String gitHubUsername) {
    return new StudentOnGitHub(GitHubUsername.given(gitHubUsername), Optional.empty());
  }

  public static StudentOnGitHub with(GitHubUsername gitHubUsername) {
    return new StudentOnGitHub(gitHubUsername, Optional.empty());
  }

  public static StudentOnGitHub with(GitHubUsername gitHubUsername,
      InstitutionalStudent institutionalStudent) {
    return new StudentOnGitHub(gitHubUsername, Optional.of(institutionalStudent));
  }

  public static StudentOnGitHub with(GitHubUsername gitHubUsername,
      Optional<InstitutionalStudent> institutionalStudentOpt) {
    return new StudentOnGitHub(gitHubUsername, institutionalStudentOpt);
  }

  private final GitHubUsername gitHubUsername;
  private final Optional<InstitutionalStudent> institutionalStudentOpt;

  private StudentOnGitHub(GitHubUsername gitHubUsername,
      Optional<InstitutionalStudent> institutionalStudentOpt) {
    this.gitHubUsername = checkNotNull(gitHubUsername);
    this.institutionalStudentOpt = checkNotNull(institutionalStudentOpt);
  }

  public GitHubUsername getGitHubUsername() {
    return gitHubUsername;
  }

  public Optional<Integer> getInstitutionalId() {
    return institutionalStudentOpt.map(InstitutionalStudent::getId);
  }

  public Optional<String> getInstitutionalUsername() {
    return institutionalStudentOpt.map(InstitutionalStudent::getUsername);
  }

  public Optional<EmailAddressAndPersonal> getEmail() {
    return institutionalStudentOpt.map(InstitutionalStudent::getEmail);
  }

  public boolean hasInstitutionalPart() {
    return institutionalStudentOpt.isPresent();
  }

  public InstitutionalStudent toInstitutionalStudent() {
    return institutionalStudentOpt.orElseThrow(IllegalStateException::new);
  }

  public StudentOnGitHubKnown asStudentOnGitHubKnown() {
    return StudentOnGitHubKnown.with(gitHubUsername,
        institutionalStudentOpt.orElseThrow(IllegalStateException::new));
  }

  @Override
  public boolean equals(Object o2) {
    if (!(o2 instanceof StudentOnGitHub)) {
      return false;
    }
    final StudentOnGitHub s2 = (StudentOnGitHub) o2;
    return gitHubUsername.equals(s2.gitHubUsername)
        && institutionalStudentOpt.equals(s2.institutionalStudentOpt);
  }

  @Override
  public int hashCode() {
    return Objects.hash(gitHubUsername, institutionalStudentOpt);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("GitHub username", gitHubUsername)
        .add("institutional", institutionalStudentOpt).toString();
  }
}
