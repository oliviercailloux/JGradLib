package io.github.oliviercailloux.git.fs;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Verify.verify;

import java.nio.file.Path;

abstract class GitAbsolutePath extends GitPath {

	static GitAbsolutePath givenRoot(GitPathRoot root, Path internalPath) {
		checkNotNull(root);
		checkArgument(internalPath.isAbsolute());

		final GitAbsolutePath absolute;
		if (internalPath.getNameCount() == 0) {
			verify(internalPath.toString().equals("/"));
			absolute = root;
		} else {
			absolute = new GitPathNonRoot(root, internalPath);
		}

		return absolute;
	}

	static GitAbsolutePath givenRev(GitFileSystem fs, GitRev gitRev, Path internalPath) {
		checkNotNull(gitRev);
		final GitPathRoot root = new GitPathRoot(fs, gitRev);
		return givenRoot(root, internalPath);
	}

	@Override
	public GitAbsolutePath toAbsolutePath() {
		return this;
	}

	@Override
	public boolean isAbsolute() {
		return true;
	}
}
