package io.github.oliviercailloux.st_projects.model;

import java.time.Instant;

public interface GitFullContext extends GitContext {

	public Instant getIgnoredAfter();

	public Instant getSubmittedTime();

}
