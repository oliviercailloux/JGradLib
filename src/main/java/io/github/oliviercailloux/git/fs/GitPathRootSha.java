package io.github.oliviercailloux.git.fs;

import static com.google.common.base.Preconditions.checkArgument;

public class GitPathRootSha extends GitPathRoot {

	protected GitPathRootSha(GitFileSystem fileSystem, GitRev gitRev) {
		super(fileSystem, gitRev);
		checkArgument(gitRev.isCommitId());
	}

	@Override
	public GitPathRootSha toSha() {
		return this;
	}
}
