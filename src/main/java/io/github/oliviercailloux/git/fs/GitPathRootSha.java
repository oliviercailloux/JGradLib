package io.github.oliviercailloux.git.fs;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.IOException;
import java.nio.file.NoSuchFileException;

public class GitPathRootSha extends GitPathRoot {

	protected GitPathRootSha(GitFileSystem fileSystem, GitRev gitRev) {
		super(fileSystem, gitRev);
		checkArgument(gitRev.isCommitId());
	}

	@Override
	public GitPathRootSha toSha() throws IOException, NoSuchFileException {
		return this;
	}
}
