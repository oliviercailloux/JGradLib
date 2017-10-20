package io.github.oliviercailloux.st_projects.model;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import javax.json.JsonObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.MoreObjects;
import com.jcabi.github.Coordinates;
import com.jcabi.github.Event;
import com.jcabi.github.Github;
import com.jcabi.github.IssueEvents;
import com.jcabi.github.Repo;
import com.jcabi.github.RtGithub;

import io.github.oliviercailloux.st_projects.services.git_hub.Fetch;
import io.github.oliviercailloux.st_projects.services.git_hub.Utils;

public class GitHubEvent {

	public static String CLOSED_TYPE = "closed";

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GitHubEvent.class);

	private JsonObject details;

	private Event event;

	/**
	 * Not <code>null</code>.
	 */
	private JsonObject json;

	public GitHubEvent(Event event) {
		this.json = null;
		details = null;
		this.event = event;
	}

	public GitHubEvent(JsonObject json) {
		this.json = requireNonNull(json);
		details = null;
		final Github github = new RtGithub();
		final Repo repo = github.repos()
				.get(new Coordinates.Simple(getRepoURL().toString().replace("https://api.github.com/repos/", "")));
		final IssueEvents issues = repo.issueEvents();
		event = issues.get(getNumber());
	}

	public URL getApiURL() {
		return Utils.newUrl(json.getString("url"));
	}

	public LocalDateTime getCreatedAt() {
		final ZonedDateTime parsed = ZonedDateTime.parse(json.getString("created_at"));
		assert parsed.getZone().equals(ZoneOffset.UTC);
		return parsed.toLocalDateTime();
	}

	public URL getHtmlURL() {
		return Utils.newUrl(json.getString("html_url"));
	}

	public GitHubIssue getIssue() {
		return new GitHubIssue(event.);
	}

	public String getName() {
		return json.getString("name");
	}

	public int getNumber() {
		return json.getInt("number");
	}

	public URL getRepoURL() {
		return Utils.newUrl(json.getString("repository_url"));
	}

	public String getType() {
		return json.getString("event");
	}

	public void init() throws IOException {
		if (json == null) {
			json = event.json();
		}

		try (Fetch fetcher = new Fetch()) {
			details = fetcher.fetchEventDetails(this);
		}
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this).addValue(json).add("details", details).toString();
	}

}
