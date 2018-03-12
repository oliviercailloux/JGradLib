package io.github.oliviercailloux.git_hub_gql;

import com.google.common.base.CaseFormat;

public enum IssueEventType {
	ASSIGNED, CLOSED, RENAMED_TITLE, REOPENED, UNASSIGNED;
	@SuppressWarnings("unchecked")
	public <T extends IssueEvent> Class<T> toClass() {
		try {
			return (Class<T>) Class.forName(toString());
		} catch (ClassNotFoundException e) {
			throw new IllegalStateException(e);
		}
	}

	@Override
	public String toString() {
		return CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, super.toString()) + "Event";
	}
}
