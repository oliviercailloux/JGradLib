package io.github.oliviercailloux.st_projects.model;

import static java.util.Objects.requireNonNull;

import java.net.URL;
import java.time.Instant;
import java.util.Set;

import javax.json.JsonObject;

import com.google.common.collect.ImmutableSet;

import io.github.oliviercailloux.st_projects.services.git_hub.GitHubJsonParser;
import io.github.oliviercailloux.st_projects.utils.Utils;

/**
 * An issue as it was at some specific time of its life.
 *
 * @author Olivier Cailloux
 *
 */
public class IssueSnapshot {
	/**
	 * @param json
	 *            the json describing the event corresponding to this snapshot.
	 */
	public static IssueSnapshot of(JsonObject json, String name, boolean isOpen, Set<User> assignees) {
		return new IssueSnapshot(json, name, isOpen, assignees);
	}

	private final ImmutableSet<User> assignees;

	private final boolean isOpen;

	private final JsonObject json;

	private final String name;

	private IssueSnapshot(JsonObject json, String name, boolean isOpen, Set<User> assignees) {
		this.json = requireNonNull(json);
		this.name = requireNonNull(name);
		this.isOpen = isOpen;
		this.assignees = ImmutableSet.copyOf(requireNonNull(assignees));
	}

	public ImmutableSet<User> getAssignees() {
		return assignees;
	}

	public Instant getBirthTime() {
		return GitHubJsonParser.getCreatedAt(json);
	}

	public int getEventId() {
		return json.getInt("id");
	}

	public String getName() {
		return name;
	}

	public boolean isOpen() {
		return isOpen;
	}

	public URL getApiURL() {
		return Utils.newURL(json.getString("url"));
	}
}
