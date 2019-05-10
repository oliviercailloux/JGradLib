package io.github.oliviercailloux.grade.context;

import java.util.Optional;

import org.eclipse.jgit.revwalk.RevCommit;

import io.github.oliviercailloux.git.Client;

public interface GitContext {

	public Client getClient();

	/**
	 * If no such commit, or if given optional is empty, returns a files reader
	 * which never finds any file.
	 */
	public FilesSource getFilesReader(Optional<RevCommit> sourceCommit);

}
