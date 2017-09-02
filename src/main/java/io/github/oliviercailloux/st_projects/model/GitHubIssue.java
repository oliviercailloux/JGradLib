package io.github.oliviercailloux.st_projects.model;

import static java.util.Objects.requireNonNull;

import java.net.URL;
import java.util.List;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonValue;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;

import io.github.oliviercailloux.st_projects.services.git_hub.Utils;

public class GitHubIssue {
	private List<Contributor> assignees;

	/**
	 * Not <code>null</code>.
	 */
	private JsonObject json;

	public GitHubIssue(JsonObject json) {
		this.json = requireNonNull(json);
		this.assignees = null;
	}

	public List<Contributor> getAssignees() {
		if (assignees == null) {
			initAssignees();
		}
		return assignees;
	}

	public URL getUrl() {
		return Utils.newUrl(json.getString("url"));
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this).add("Assignees", assignees).addValue(json).toString();
	}

	private void initAssignees() {
		final Builder<Contributor> builder = ImmutableList.builder();
		final JsonArray assigneesJson = json.getJsonArray("assignees");
		for (JsonValue assigneeVal : assigneesJson) {
			final JsonObject assigneeJson = assigneeVal.asJsonObject();
			final Contributor assignee = new Contributor(assigneeJson);
			builder.add(assignee);
		}
		assignees = builder.build();
	}

	public String getName() {
		return json.getString("name");
	}
}
