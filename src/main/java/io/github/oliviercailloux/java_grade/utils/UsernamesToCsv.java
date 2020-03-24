package io.github.oliviercailloux.java_grade.utils;

import java.io.StringWriter;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

import com.google.common.collect.ImmutableMap;
import com.univocity.parsers.csv.CsvWriter;
import com.univocity.parsers.csv.CsvWriterSettings;

import io.github.oliviercailloux.grade.comm.StudentOnGitHubKnown;
import io.github.oliviercailloux.grade.mycourse.json.JsonStudentOnGitHubKnown;
import io.github.oliviercailloux.json.JsonbUtils;

public class UsernamesToCsv {
	private static final Path WORK_DIR = Paths.get("../../Java L3/");

	public static void main(String[] args) throws Exception {
		@SuppressWarnings("all")
		final Type typeSet = new HashSet<StudentOnGitHubKnown>() {
		}.getClass().getGenericSuperclass();
		final Set<StudentOnGitHubKnown> usernamesAsSet = JsonbUtils.fromJson(
				Files.readString(WORK_DIR.resolve("usernames.json")), typeSet, JsonStudentOnGitHubKnown.asAdapter());
		final ImmutableMap<String, StudentOnGitHubKnown> usernames = usernamesAsSet.stream()
				.collect(ImmutableMap.toImmutableMap(s -> s.getGitHubUsername(), s -> s));

		final StringWriter stringWriter = new StringWriter();
		final CsvWriter writer = new CsvWriter(stringWriter, new CsvWriterSettings());
		writer.writeHeaders("GitHub username", "GitHub URL");
		for (String username : usernames.keySet()) {
			writer.writeRow(username, "https://github.com/" + username);
		}
		writer.close();

		Files.writeString(Path.of("out.csv"), stringWriter.toString());
	}
}
