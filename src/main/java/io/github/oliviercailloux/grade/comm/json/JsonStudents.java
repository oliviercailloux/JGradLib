package io.github.oliviercailloux.grade.comm.json;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Verify.verify;

import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import io.github.oliviercailloux.email.EmailAddress;
import io.github.oliviercailloux.git.github.model.GitHubUsername;
import io.github.oliviercailloux.grade.comm.InstitutionalStudent;
import io.github.oliviercailloux.grade.comm.StudentOnGitHub;
import io.github.oliviercailloux.grade.comm.StudentOnGitHubKnown;
import io.github.oliviercailloux.json.JsonbUtils;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.bind.JsonbException;
import jakarta.json.bind.adapter.JsonbAdapter;
import jakarta.json.bind.annotation.JsonbTypeAdapter;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class JsonStudents {
  @JsonbTypeAdapter(JsonStudentAdapter.class)
  public static class JsonStudentEntry {
    public static int counter = 0;

    public static JsonStudentEntry given(GitHubUsername gitHubUsername, EmailAddress email) {
      /* Temporary workaround: we invent ids. */
      final ImmutableList<String> separated = ImmutableList.copyOf(email.getAddress().split("@"));
      verify(separated.size() == 2);
      final String firstPart = separated.get(0);
      final ImmutableList<String> split = ImmutableList.copyOf(firstPart.split("\\."));
      checkArgument(split.size() == 2, firstPart, split);
      return new JsonStudentEntry(Optional.of(gitHubUsername), Optional.of(++counter), firstPart,
          split.get(0), split.get(1), Optional.of(email));
    }

    public static JsonStudentEntry given(Optional<GitHubUsername> gitHubUsername,
        Optional<Integer> institutionalId, String institutionalUsername, String firstName,
        String lastName, Optional<EmailAddress> email) {
      return new JsonStudentEntry(gitHubUsername, institutionalId, institutionalUsername, firstName,
          lastName, email);
    }

    private final Optional<GitHubUsername> gitHubUsername;
    private final Optional<Integer> institutionalId;
    private final String institutionalUsername;
    private final String firstName;
    private final String lastName;
    private final Optional<EmailAddress> email;

    private JsonStudentEntry(Optional<GitHubUsername> gitHubUsername,
        Optional<Integer> institutionalId, String institutionalUsername, String firstName,
        String lastName, Optional<EmailAddress> email) {
      this.gitHubUsername = checkNotNull(gitHubUsername);
      this.institutionalId = checkNotNull(institutionalId);
      this.institutionalUsername = checkNotNull(institutionalUsername);
      this.firstName = checkNotNull(firstName);
      this.lastName = checkNotNull(lastName);
      this.email = checkNotNull(email);
    }

    public Optional<GitHubUsername> getGitHubUsername() {
      return gitHubUsername;
    }

    public Optional<StudentOnGitHub> toStudentOnGitHub() {
      if (gitHubUsername.isPresent()) {
        return Optional.of(StudentOnGitHub.with(gitHubUsername.get(), getInstitutionalStudent()));
      }
      return Optional.empty();
    }

    public Optional<Integer> getInstitutionalId() {
      return institutionalId;
    }

    public String getInstitutionalUsername() {
      return institutionalUsername;
    }

    public String getFirstName() {
      return firstName;
    }

    public String getLastName() {
      return lastName;
    }

    public Optional<EmailAddress> getEmail() {
      return email;
    }

    public Optional<InstitutionalStudent> getInstitutionalStudent() {
      if (institutionalId.isPresent() && !institutionalUsername.isEmpty() && email.isPresent()) {
        return Optional.of(InstitutionalStudent.withU(institutionalId.get(), institutionalUsername,
            firstName, lastName, email.get()));
      }
      return Optional.empty();
    }

    public Optional<StudentOnGitHubKnown> toStudentOnGitHubKnown() {
      if (gitHubUsername.isPresent() && getInstitutionalStudent().isPresent()) {
        return Optional
            .of(StudentOnGitHubKnown.with(gitHubUsername.get(), getInstitutionalStudent().get()));
      }
      return Optional.empty();
    }

    @Override
    public boolean equals(Object o2) {
      if (!(o2 instanceof JsonStudents.JsonStudentEntry)) {
        return false;
      }
      final JsonStudents.JsonStudentEntry t2 = (JsonStudents.JsonStudentEntry) o2;
      return gitHubUsername.equals(t2.gitHubUsername) && institutionalId.equals(t2.institutionalId)
          && institutionalUsername.equals(t2.institutionalUsername)
          && firstName.equals(t2.firstName) && lastName.equals(t2.lastName)
          && email.equals(t2.email);
    }

    @Override
    public int hashCode() {
      return Objects.hash(gitHubUsername, institutionalId, institutionalUsername, firstName,
          lastName, email);
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this).add("gitHubUsername", gitHubUsername)
          .add("institutionalId", institutionalId)
          .add("institutionalUsername", institutionalUsername).add("firstName", firstName)
          .add("lastName", lastName).add("email", email).toString();
    }
  }

  public static class JsonStudentAdapter implements JsonbAdapter<JsonStudentEntry, JsonObject> {

    @Override
    public JsonObject adaptToJson(JsonStudentEntry student) {
      final JsonObjectBuilder builder = Json.createObjectBuilder();
      if (student.getGitHubUsername().isPresent()) {
        builder.add("gitHubUsername", student.getGitHubUsername().get().getUsername());
      }
      if (student.getInstitutionalId().isPresent()) {
        builder.add("institutionalId", student.getInstitutionalId().get());
      }
      if (!student.getInstitutionalUsername().isEmpty()) {
        builder.add("institutionalUsername", student.getInstitutionalUsername());
      }
      if (!student.getFirstName().isEmpty()) {
        builder.add("firstName", student.getFirstName());
      }
      if (!student.getLastName().isEmpty()) {
        builder.add("lastName", student.getLastName());
      }
      if (student.getEmail().isPresent()) {
        builder.add("email", student.getEmail().get().getAddress());
      }
      return builder.build();
    }

    @Override
    public JsonStudentEntry adaptFromJson(JsonObject json) throws JsonbException {
      final Optional<GitHubUsername> gh =
          Optional.ofNullable(Strings.emptyToNull(json.getString("gitHubUsername", "")))
              .map(GitHubUsername::given);
      final Optional<Integer> id =
          json.containsKey("institutionalId") ? Optional.of(json.getInt("institutionalId"))
              : Optional.empty();
      final String inst = json.getString("institutionalUsername", "");
      final String firstName = json.getString("firstName", "");
      final String lastName = json.getString("lastName", "");
      final Optional<EmailAddress> email = Optional
          .ofNullable(Strings.emptyToNull(json.getString("email", ""))).map(EmailAddress::given);

      return JsonStudentEntry.given(gh, id, inst, firstName, lastName, email);
    }
  }

  public static JsonStudents from(String json) {
    @SuppressWarnings("serial")
    final Set<JsonStudentEntry> type = new LinkedHashSet<>() {};
    return new JsonStudents(JsonbUtils.fromJson(json, type.getClass().getGenericSuperclass(),
        new JsonStudentAdapter()));
  }

  public static String toJson(Set<JsonStudentEntry> students) {
    return JsonbUtils.toJsonObject(students, new JsonStudentAdapter()).toString();
  }

  private final ImmutableSet<JsonStudentEntry> entries;

  private JsonStudents(Set<JsonStudentEntry> entries) {
    this.entries = ImmutableSet.copyOf(entries);
    /*
     * Just to check that these are unique.
     */
    entries.stream().filter(e -> e.getGitHubUsername().isPresent())
        .collect(ImmutableBiMap.toImmutableBiMap(e -> e.getGitHubUsername(), e -> e));
    entries.stream().filter(e -> e.getInstitutionalId().isPresent())
        .collect(ImmutableBiMap.toImmutableBiMap(e -> e.getInstitutionalId(), e -> e));
    entries.stream().filter(e -> !e.getInstitutionalUsername().isEmpty())
        .collect(ImmutableBiMap.toImmutableBiMap(e -> e.getInstitutionalUsername(), e -> e));
    entries.stream().filter(e -> e.getEmail().isPresent())
        .collect(ImmutableBiMap.toImmutableBiMap(e -> e.getEmail(), e -> e));
  }

  public ImmutableBiMap<GitHubUsername, StudentOnGitHub> getStudentsByGitHubUsername() {
    return entries.stream().map(JsonStudentEntry::toStudentOnGitHub).filter(Optional::isPresent)
        .map(Optional::get)
        .collect(ImmutableBiMap.toImmutableBiMap(StudentOnGitHub::getGitHubUsername, s -> s));
  }

  public ImmutableBiMap<GitHubUsername, StudentOnGitHubKnown> getStudentsKnownByGitHubUsername() {
    return entries.stream().map(JsonStudentEntry::toStudentOnGitHubKnown)
        .filter(Optional::isPresent).map(Optional::get)
        .collect(ImmutableBiMap.toImmutableBiMap(StudentOnGitHubKnown::getGitHubUsername, s -> s));
  }

  public ImmutableSet<Integer> getInstitutionalIds() {
    return entries.stream().map(JsonStudentEntry::getInstitutionalId).filter(Optional::isPresent)
        .map(Optional::get).collect(ImmutableSet.toImmutableSet());
  }

  public ImmutableSet<String> getInstitutionalUsernames() {
    return entries.stream().map(JsonStudentEntry::getInstitutionalUsername)
        .filter(s -> !s.isEmpty()).collect(ImmutableSet.toImmutableSet());
  }

  public ImmutableSet<EmailAddress> getEmails() {
    return entries.stream().map(JsonStudentEntry::getEmail).filter(Optional::isPresent)
        .map(Optional::get).collect(ImmutableSet.toImmutableSet());
  }

  public ImmutableBiMap<Integer, InstitutionalStudent> getInstitutionalStudentsById() {
    return entries.stream().map(JsonStudentEntry::getInstitutionalStudent)
        .filter(Optional::isPresent).map(Optional::get)
        .collect(ImmutableBiMap.toImmutableBiMap(InstitutionalStudent::getId, s -> s));
  }

  public ImmutableBiMap<EmailAddress, InstitutionalStudent> getInstitutionalStudentsByEmail() {
    return entries.stream().map(JsonStudentEntry::getInstitutionalStudent)
        .filter(Optional::isPresent).map(Optional::get)
        .collect(ImmutableBiMap.toImmutableBiMap(s -> s.getEmail().getAddress(), s -> s));
  }

  public ImmutableBiMap<String, InstitutionalStudent> getInstitutionalStudentsByUsername() {
    return entries.stream().map(JsonStudentEntry::getInstitutionalStudent)
        .filter(Optional::isPresent).map(Optional::get)
        .collect(ImmutableBiMap.toImmutableBiMap(InstitutionalStudent::getUsername, s -> s));
  }

  public ImmutableBiMap<GitHubUsername, InstitutionalStudent>
      getInstitutionalStudentsByGitHubUsername() {
    return entries.stream().filter(s -> s.getInstitutionalStudent().isPresent())
        .collect(ImmutableBiMap.toImmutableBiMap(s -> s.gitHubUsername.orElseThrow(),
            s -> s.getInstitutionalStudent().orElseThrow()));
  }
}
