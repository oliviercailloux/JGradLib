package io.github.oliviercailloux.st_projects.ex2;

import javax.json.bind.adapter.JsonbAdapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.oliviercailloux.st_projects.model.StudentOnGitHub;
import io.github.oliviercailloux.st_projects.model.StudentOnGitHubKnown;

public class AsKnown implements JsonbAdapter<StudentOnGitHub, StudentOnGitHubKnown> {

	@Override
	public StudentOnGitHubKnown adaptToJson(StudentOnGitHub student) {
		LOGGER.info("Converting {}.", student);
		return student.asStudentOnGitHubKnown();
	}

	@Override
	public StudentOnGitHub adaptFromJson(StudentOnGitHubKnown studentKnown) {
		return studentKnown.asStudentOnGitHub();
	}

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(AsKnown.class);
}
