package io.github.oliviercailloux.grade.context;

import java.util.Optional;

import org.eclipse.jgit.revwalk.RevCommit;

import io.github.oliviercailloux.git.Client;

public interface GitContext {

	public Client getClient();

	public Optional<RevCommit> getMainCommit();

}
