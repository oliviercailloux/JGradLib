package io.github.oliviercailloux.st_projects.services.read;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.InputStream;
import java.util.Map;
import java.util.Set;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonReader;
import javax.json.JsonValue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSet;

import io.github.oliviercailloux.st_projects.model.StudentOnGitHub;
import io.github.oliviercailloux.st_projects.model.StudentOnMyCourse;
import io.github.oliviercailloux.st_projects.utils.Utils;

public class UsernamesReader {

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(UsernamesReader.class);

	private Map<Integer, String> idsToMyCourseUsernames;

	private Map<Integer, String> idsToFirstNames;

	private Map<Integer, String> idsToLastNames;

	private ImmutableSet<Integer> idsNotSubmitted;

	private Map<String, Integer> gitHubUsernamesToIds;

	private JsonArray json;

	public UsernamesReader() {
		gitHubUsernamesToIds = null;
		idsToFirstNames = null;
		idsToLastNames = null;
		idsNotSubmitted = null;
		json = null;
	}

	public void read(InputStream inputStream) {
		readData(inputStream);
		readIdsToMyCourseUsernames();
		readIdsToStudentFirstNames();
		readIdsToStudentLastNames();
		readIdsNotSubmitted();
		readGitHubUsernamesToIds();
	}

	private void readData(InputStream inputStream) {
		try (JsonReader jr = Json.createReader(inputStream)) {
			json = jr.readArray();
		}
	}

	private Map<Integer, String> readIdsToMyCourseUsernames() {
		idsToMyCourseUsernames = json.stream().sequential().map(JsonValue::asJsonObject)
				.collect(Utils.toLinkedMap((o) -> o.getInt("studentId"), (o) -> o.getString("myCourseUsername")));
		LOGGER.info("Got: {}.", idsToMyCourseUsernames);
		return idsToMyCourseUsernames;
	}

	private Map<Integer, String> readIdsToStudentFirstNames() {
		idsToFirstNames = json.stream().sequential().map(JsonValue::asJsonObject)
				.collect(Utils.toLinkedMap((o) -> o.getInt("studentId"), (o) -> o.getString("firstName")));
		LOGGER.info("Got: {}.", idsToFirstNames);
		return idsToFirstNames;
	}

	private Map<Integer, String> readIdsToStudentLastNames() {
		idsToLastNames = json.stream().sequential().map(JsonValue::asJsonObject)
				.collect(Utils.toLinkedMap((o) -> o.getInt("studentId"), (o) -> o.getString("lastName")));
		LOGGER.info("Got: {}.", idsToLastNames);
		return idsToLastNames;
	}

	private ImmutableSet<Integer> readIdsNotSubmitted() {
		idsNotSubmitted = json.stream().sequential().map(JsonValue::asJsonObject)
				.filter(o -> !o.getBoolean("submittedGitHubUsername", true)).map((o) -> o.getInt("studentId"))
				.collect(ImmutableSet.toImmutableSet());
		LOGGER.info("Got: {}.", idsNotSubmitted);
		return idsNotSubmitted;
	}

	private Map<String, Integer> readGitHubUsernamesToIds() {
		gitHubUsernamesToIds = json.stream().sequential().map(JsonValue::asJsonObject)
				.filter((o) -> !o.isNull("gitHubUsername"))
				.collect(Utils.toLinkedMap((o) -> o.getString("gitHubUsername"), (o) -> o.getInt("studentId")));
		LOGGER.info("Got: {}.", gitHubUsernamesToIds);
		return gitHubUsernamesToIds;
	}

	public StudentOnGitHub getStudentOnGitHub(String gitHubUsername) {
		if (!getGitHubUsernamesToIds().containsKey(gitHubUsername)) {
			LOGGER.info("Not found: {}.", gitHubUsername);
			return StudentOnGitHub.with(gitHubUsername);
		}

		final int id = getGitHubUsernamesToIds().get(gitHubUsername);
		final StudentOnMyCourse st = getStudentOnMyCourse(id);
		return StudentOnGitHub.with(gitHubUsername, st);
	}

	public StudentOnMyCourse getStudentOnMyCourse(int id) {
		checkArgument(getIdsToFirstNames().containsKey(id));
		final String firstName = getIdsToFirstNames().get(id);
		final String lastName = getIdsToLastNames().get(id);
		final String username = getIdsToMyCourseUsernames().get(id);
		return StudentOnMyCourse.with(id, firstName, lastName, username);
	}

	public Map<Integer, String> getIdsToGitHubUsernames() {
		final Map<Integer, String> idsToUsernames = json.stream().sequential().map(JsonValue::asJsonObject)
				.filter((o) -> !o.isNull("gitHubUsername"))
				.collect(Utils.toLinkedMap((o) -> o.getInt("studentId"), (o) -> o.getString("gitHubUsername")));
		LOGGER.info("Got: {}.", idsToUsernames);
		return idsToUsernames;
	}

	public Map<Integer, String> getIdsToMyCourseUsernames() {
		return idsToMyCourseUsernames;
	}

	public Map<Integer, String> getIdsToFirstNames() {
		return idsToFirstNames;
	}

	public Map<Integer, String> getIdsToLastNames() {
		return idsToLastNames;
	}

	public Set<Integer> getIdsNotSubmitted() {
		return idsNotSubmitted;
	}

	public Map<String, Integer> getGitHubUsernamesToIds() {
		return gitHubUsernamesToIds;
	}

}
