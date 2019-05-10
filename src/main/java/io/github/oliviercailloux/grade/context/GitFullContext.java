package io.github.oliviercailloux.grade.context;

import java.time.Instant;
import java.util.Optional;

import org.eclipse.jgit.revwalk.RevCommit;

public interface GitFullContext extends GitContext {

	public Instant getIgnoredAfter();

	public Instant getSubmittedTime();

	public Optional<RevCommit> getMainCommit();

}
