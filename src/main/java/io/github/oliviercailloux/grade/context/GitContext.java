package io.github.oliviercailloux.grade.context;

import org.eclipse.jgit.revwalk.RevCommit;

import io.github.oliviercailloux.git.Client;

public interface GitContext {

	public Client getClient();

	/**
	 * If no such commit, returns a files reader which never finds any file.
	 */
	public FilesSource getFilesReader(RevCommit sourceCommit);

}
