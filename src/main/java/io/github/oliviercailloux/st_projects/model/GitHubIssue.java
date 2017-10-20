package io.github.oliviercailloux.st_projects.model;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.net.URL;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.json.JsonObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.diffplug.common.base.Errors;
import com.google.common.base.MoreObjects;
import com.jcabi.github.Coordinates;
import com.jcabi.github.Event;
import com.jcabi.github.Github;
import com.jcabi.github.Issue;
import com.jcabi.github.Issues;
import com.jcabi.github.Repo;
import com.jcabi.github.RtGithub;

import io.github.oliviercailloux.st_projects.services.git_hub.Utils;
import jersey.repackaged.com.google.common.collect.Lists;
import jersey.repackaged.com.google.common.collect.Sets;

public class GitHubIssue {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GitHubIssue.class);

	private List<Contributor> assignees;

	private Issue issue;

	/**
	 * Not <code>null</code>.
	 */
	private JsonObject json;

	public GitHubIssue(Issue issue) {
		this.issue = requireNonNull(issue);
		this.assignees = null;
		this.json = null;
	}

	public GitHubIssue(JsonObject json) {
		/** TODO remove this constructor. */
		this.json = requireNonNull(json);
		this.assignees = null;
		final Github github = new RtGithub();
		final Repo repo = github.repos()
				.get(new Coordinates.Simple(getRepoURL().toString().replace("https://api.github.com/repos/", "")));
		final Issues issues = repo.issues();
		issue = issues.get(getNumber());
	}

	public URL getApiURL() {
		return Utils.newUrl(json.getString("url"));
	}

	public List<Contributor> getAssigneesAtFirstClose() {
		checkState(assignees != null);
		return assignees;
	}

	public URL getHtmlURL() {
		return Utils.newUrl(json.getString("html_url"));
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

	public void init() throws IOException {
		this.json = issue.json();
		initAssignees();
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this).add("Assignees", assignees).addValue(json).toString();
	}

	private void initAssignees() throws IOException {
		final Iterable<Event> eventsIt = issue.events();
		final Stream<Event> eventsStream = StreamSupport.stream(eventsIt.spliterator(), false);
		final Stream<Event.Smart> smartStream = eventsStream.map((e) -> new Event.Smart(e));
		final List<Event.Smart> sortedEvents = smartStream
				.sorted(Comparator.comparing(Errors.rethrow().wrapFunction(Event.Smart::createdAt)))
				.collect(Collectors.toList());

		final List<Event.Smart> firstEvents = Lists.newLinkedList();
		for (Event.Smart event : sortedEvents) {
			if (event.type().equals(Event.CLOSED)) {
				break;
			}
			firstEvents.add(event);
		}

		final Set<Contributor> assigneesSet = Sets.newLinkedHashSet();
		for (Event.Smart event : sortedEvents) {
			final String type = event.type();
			switch (type) {
			case Event.ASSIGNED: {
				final Contributor c = new Contributor(event.json().getJsonObject("assignee"));
				LOGGER.info("Assigned {}.", c);
				final boolean modified = assigneesSet.add(c);
				assert modified;
				break;
			}
			case Event.UNASSIGNED: {
				final Contributor c = new Contributor(event.json().getJsonObject("assignee"));
				LOGGER.info("Unassigned {}.", c);
				final boolean modified = assigneesSet.remove(c);
				assert modified;
				break;
			}
			case Event.CLOSED: {
				throw new IllegalStateException();
			}
			default:
			}
		}

		assignees = Lists.newArrayList(assigneesSet);
	}
}
