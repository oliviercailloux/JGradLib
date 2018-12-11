package io.github.oliviercailloux.st_projects.model;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

import java.util.Objects;
import java.util.Optional;

import com.google.common.base.MoreObjects;

public class StudentOnGitHub {
	public static StudentOnGitHub with(String username) {
		return new StudentOnGitHub(username, Optional.empty());
	}

	public static StudentOnGitHub with(String username, StudentOnMyCourse studentOnMyCourse) {
		return new StudentOnGitHub(username, Optional.of(studentOnMyCourse));
	}

	public static StudentOnGitHub with(String username, Optional<StudentOnMyCourse> studentOnMyCourseOpt) {
		return new StudentOnGitHub(username, studentOnMyCourseOpt);
	}

	private String gitHubUsername;
	private Optional<StudentOnMyCourse> studentOnMyCourseOpt;

	private StudentOnGitHub(String username, Optional<StudentOnMyCourse> studentOnMyCourseOpt) {
		this.gitHubUsername = requireNonNull(username);
		this.studentOnMyCourseOpt = requireNonNull(studentOnMyCourseOpt);
	}

	@Override
	public boolean equals(Object o2) {
		if (!(o2 instanceof StudentOnGitHub)) {
			return false;
		}
		final StudentOnGitHub s2 = (StudentOnGitHub) o2;
		return gitHubUsername.equals(s2.gitHubUsername) && studentOnMyCourseOpt.equals(s2.studentOnMyCourseOpt);
	}

	@Override
	public int hashCode() {
		return Objects.hash(gitHubUsername, studentOnMyCourseOpt);
	}

	public String getGitHubUsername() {
		return gitHubUsername;
	}

	public boolean hasStudentOnMyCourse() {
		return studentOnMyCourseOpt.isPresent();
	}

	public Optional<StudentOnMyCourse> getStudentOnMyCourse() {
		return studentOnMyCourseOpt;
	}

	public StudentOnGitHubKnown asStudentOnGitHubKnown() {
		checkState(studentOnMyCourseOpt.isPresent());
		return StudentOnGitHubKnown.with(studentOnMyCourseOpt.get(), gitHubUsername);
	}

	public Optional<Integer> getStudentId() {
		return studentOnMyCourseOpt.map(StudentOnMyCourse::getStudentId);
	}

	public Optional<String> getFirstName() {
		return studentOnMyCourseOpt.map(StudentOnMyCourse::getFirstName);
	}

	public Optional<String> getLastName() {
		return studentOnMyCourseOpt.map(StudentOnMyCourse::getLastName);
	}

	public Optional<String> getMyCourseUsername() {
		return studentOnMyCourseOpt.map(StudentOnMyCourse::getMyCourseUsername);
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this).add("GitHub username", gitHubUsername)
				.add("onMyCourse", studentOnMyCourseOpt).toString();
	}
}
