package io.github.oliviercailloux.git.fs;

import static com.google.common.base.Preconditions.checkNotNull;

import java.nio.file.Path;

class GitEmptyPath extends GitRelativePath {
	private GitPathRoot absoluteEquivalent;

	GitEmptyPath(GitPathRoot absoluteEquivalent) {
		this.absoluteEquivalent = checkNotNull(absoluteEquivalent);
	}

	@Override
	public GitPathRoot toAbsolutePath() {
		return absoluteEquivalent;
	}

	@Override
	Path getInternalPath() {
		return GitFileSystem.JIM_FS_EMPTY;
	}
}
