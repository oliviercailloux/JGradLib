package io.github.oliviercailloux.git.fs;

import org.eclipse.jgit.lib.ObjectId;

public interface GitRev {

	public boolean isRef();

	public boolean isCommitId();

	public String getGitRef();

	public ObjectId getCommitId();

	@Override
	public String toString();

}
