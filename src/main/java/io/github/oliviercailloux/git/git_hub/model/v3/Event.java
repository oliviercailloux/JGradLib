package io.github.oliviercailloux.git.git_hub.model.v3;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

import java.time.Instant;

import javax.json.JsonObject;

import com.google.common.base.MoreObjects;

import io.github.oliviercailloux.git.git_hub.services.GitHubJsonParser;
import io.github.oliviercailloux.json.PrintableJsonObjectFactory;

/**
 * <p>
 * https://developer.github.com/v3/activity/events/
 * </p>
 * <p>
 * Not to be mixed with issue event.
 * </p>
 *
 * @author Olivier Cailloux
 *
 */
public class Event {

	public static Event from(JsonObject json) {
		switch (getType(json)) {
		case PUSH_EVENT:
			return new PushEvent(json);
		case CREATE_REPOSITORY_EVENT:
			return new CreateRepositoryEvent(json);
		case CREATE_BRANCH_EVENT:
			return new CreateBranchEvent(json);
		case CREATE_TAG_EVENT:
			return new CreateTagEvent(json);
		case ISSUES_EVENT:
		case ISSUE_COMMENT_EVENT:
		case MEMBER_EVENT:
		case OTHER:
		default:
			return new Event(json);
		}
	}

	private final JsonObject json;
	private final JsonObject payload;

	Event(JsonObject json) {
		this.json = requireNonNull(json);
		payload = json.getJsonObject("payload");
		assert payload != null;
	}

	public Instant getCreatedAt() {
		return GitHubJsonParser.getCreatedAtV3(json);
	}

	public PushEvent asPushEvent() {
		checkState(getType() == EventType.PUSH_EVENT);
		return (PushEvent) this;
	}

	public int getId() {
		return json.getInt("id");
	}

	public JsonObject getJson() {
		return json;
	}

	public EventType getType() {
		return getType(json);
	}

	static EventType getType(@SuppressWarnings("hiding") JsonObject json) {
		switch (json.getString("type")) {
		case "IssuesEvent":
			return EventType.ISSUES_EVENT;
		case "IssueCommentEvent":
			return EventType.ISSUE_COMMENT_EVENT;
		case "CreateEvent":
			final String type = json.getJsonObject("payload").getString("ref_type");
			switch (type) {
			case "repository":
				return EventType.CREATE_REPOSITORY_EVENT;
			case "branch":
				return EventType.CREATE_BRANCH_EVENT;
			case "tag":
				return EventType.CREATE_TAG_EVENT;
			default:
				throw new IllegalArgumentException();
			}
		case "MemberEvent":
			return EventType.MEMBER_EVENT;
		case "PushEvent":
			return EventType.PUSH_EVENT;
		default:
		case "Other":
			return EventType.OTHER;
		}
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this).addValue(PrintableJsonObjectFactory.wrapObject(json)).toString();
	}

	JsonObject getPayload() {
		return payload;
	}

}
