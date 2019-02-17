package io.github.oliviercailloux.grade.mycourse;

import static java.util.Objects.requireNonNull;

import java.util.Objects;

import javax.json.JsonObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.MoreObjects;

public class StudentOnGitHubKnown {
	public static StudentOnGitHubKnown with(StudentOnMyCourse studentOnMyCourse, String gitHubUsername) {
		return new StudentOnGitHubKnown(studentOnMyCourse, gitHubUsername);
	}

	private String gitHubUsername;
	private StudentOnMyCourse studentOnMyCourse;

	private StudentOnGitHubKnown(StudentOnMyCourse studentOnMyCourse, String gitHubUsername) {
		this.studentOnMyCourse = requireNonNull(studentOnMyCourse);
		this.gitHubUsername = requireNonNull(gitHubUsername);
	}

	@Override
	public boolean equals(Object o2) {
		if (!(o2 instanceof StudentOnGitHubKnown)) {
			return false;
		}
		final StudentOnGitHubKnown s2 = (StudentOnGitHubKnown) o2;
		return gitHubUsername.equals(s2.gitHubUsername) && studentOnMyCourse.equals(s2.studentOnMyCourse);
	}

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(StudentOnGitHubKnown.class);

	@Override
	public int hashCode() {
		return Objects.hash(gitHubUsername, studentOnMyCourse);
	}

	public int getStudentId() {
		return studentOnMyCourse.getStudentId();
	}

	public String getFirstName() {
		return studentOnMyCourse.getFirstName();
	}

	public String getLastName() {
		return studentOnMyCourse.getLastName();
	}

	public String getGitHubUsername() {
		return gitHubUsername;
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this).add("GitHub username", gitHubUsername)
				.add("onMyCourse", studentOnMyCourse).toString();
	}

	public StudentOnGitHub asStudentOnGitHub() {
		return StudentOnGitHub.with(gitHubUsername, studentOnMyCourse);
	}

	public StudentOnMyCourse asStudentOnMyCourse() {
		return studentOnMyCourse;
	}
}
