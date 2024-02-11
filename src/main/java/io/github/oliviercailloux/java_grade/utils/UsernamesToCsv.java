package io.github.oliviercailloux.java_grade.utils;

import com.google.common.collect.ImmutableMap;
import com.univocity.parsers.csv.CsvWriter;
import com.univocity.parsers.csv.CsvWriterSettings;
import io.github.oliviercailloux.git.git_hub.model.GitHubUsername;
import io.github.oliviercailloux.grade.comm.StudentOnGitHubKnown;
import io.github.oliviercailloux.grade.comm.json.JsonStudents;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class UsernamesToCsv {
	private static final Path WORK_DIR = Paths.get("../../Java L3/");

	public static void main(String[] args) throws Exception {
		final JsonStudents students =
				JsonStudents.from(Files.readString(WORK_DIR.resolve("usernames.json")));
		final ImmutableMap<GitHubUsername, StudentOnGitHubKnown> usernames =
				students.getStudentsKnownByGitHubUsername();

		final StringWriter stringWriter = new StringWriter();
		final CsvWriter writer = new CsvWriter(stringWriter, new CsvWriterSettings());
		writer.writeHeaders("GitHub username", "GitHub URL");
		for (GitHubUsername username : usernames.keySet()) {
			writer.writeRow(username.getUsername(), username.getUrl());
		}
		writer.close();

		Files.writeString(Path.of("out.csv"), stringWriter.toString());
	}
}
