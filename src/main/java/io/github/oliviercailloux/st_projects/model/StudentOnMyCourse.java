package io.github.oliviercailloux.st_projects.model;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import java.util.Objects;

import javax.json.JsonObject;
import javax.json.bind.annotation.JsonbCreator;
import javax.json.bind.annotation.JsonbProperty;
import javax.json.bind.annotation.JsonbPropertyOrder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.MoreObjects;

import io.github.oliviercailloux.git.utils.JsonUtils;
import io.github.oliviercailloux.json.PrintableJsonObject;

@JsonbPropertyOrder({ "studentId", "myCourseUsername", "firstName", "lastName" })
public class StudentOnMyCourse {
	@JsonbCreator
	public static StudentOnMyCourse with(@JsonbProperty("studentId") int id,
			@JsonbProperty("firstName") String firstName, @JsonbProperty("lastName") String lastName,
			@JsonbProperty("myCourseUsername") String username) {
		return new StudentOnMyCourse(id, firstName, lastName, username);
	}

	public static StudentOnMyCourse fromJson(JsonObject data) {
		return JsonUtils.deserializeWithJsonB(data.toString(), StudentOnMyCourse.class);
	}

	private int studentId;
	private String firstName;
	private String lastName;
	private String username;

	private StudentOnMyCourse(int id, String firstName, String lastName, String username) {
		checkArgument(id > 0);
		studentId = id;
		this.firstName = requireNonNull(firstName);
		this.lastName = requireNonNull(lastName);
		this.username = requireNonNull(username);
	}

	public PrintableJsonObject asJson() {
		return JsonUtils.serializeWithJsonB(this);
	}

	@Override
	public boolean equals(Object o2) {
		if (!(o2 instanceof StudentOnMyCourse)) {
			return false;
		}
		final StudentOnMyCourse s2 = (StudentOnMyCourse) o2;
		return studentId == s2.studentId && Objects.equals(firstName, s2.firstName)
				&& Objects.equals(lastName, s2.lastName) && Objects.equals(username, s2.username);
	}

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(StudentOnMyCourse.class);

	@Override
	public int hashCode() {
		return Objects.hash(studentId, firstName, lastName, username);
	}

	public int getStudentId() {
		return studentId;
	}

	public String getFirstName() {
		return firstName;
	}

	public String getLastName() {
		return lastName;
	}

	public String getMyCourseUsername() {
		return username;
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this).add("id", studentId).add("first name", firstName)
				.add("last name", lastName).add("MyCourse username", username).toString();
	}
}
