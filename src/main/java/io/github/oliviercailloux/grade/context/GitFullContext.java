package io.github.oliviercailloux.grade.context;

import java.time.Instant;

public interface GitFullContext extends GitContext {

	public Instant getIgnoredAfter();

	public Instant getSubmittedTime();

}
