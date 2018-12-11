package io.github.oliviercailloux.git_hub.low;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public enum EventType {
	OTHER("Other"), CREATE_EVENT("CreateEvent"), MEMBER_EVENT("MemberEvent"), PUSH_EVENT("PushEvent");
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(EventType.class);

	public static EventType from(String description) {
		for (EventType t : EventType.values()) {
			if (t.toString().equals(description)) {
				LOGGER.debug("Returning {}.", t);
				return t;
			}
		}
		return OTHER;
	}

	private String description;

	EventType(String description) {
		this.description = description;
	}

	@Override
	public String toString() {
		return description;
	}
}
