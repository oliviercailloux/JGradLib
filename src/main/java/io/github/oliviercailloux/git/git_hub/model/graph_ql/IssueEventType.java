package io.github.oliviercailloux.git.git_hub.model.graph_ql;

import com.google.common.base.CaseFormat;

public enum IssueEventType {
	ASSIGNED, CLOSED, RENAMED_TITLE, REOPENED, UNASSIGNED;
	public Class<? extends IssueEvent> toClass() {
		try {
			final Class<?> c = Class.forName(toString());
			assert IssueEvent.class.isAssignableFrom(c);
			@SuppressWarnings("unchecked")
			final Class<? extends IssueEvent> cc = (Class<? extends IssueEvent>) c;
			return cc;
		} catch (ClassNotFoundException e) {
			throw new IllegalStateException(e);
		}
	}

	@Override
	public String toString() {
		return CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, super.toString()) + "Event";
	}
}
