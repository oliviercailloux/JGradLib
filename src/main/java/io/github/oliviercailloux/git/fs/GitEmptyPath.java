package io.github.oliviercailloux.git.fs;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.nio.file.Path;

/**
 * An empty path. Each git file system is associated to a unique empty path.
 * This class should therefore have a unique instance per git file system. Use
 * {@link GitAbstractFileSystem#emptyPath} rather than creating a new one.
 */
class GitEmptyPath extends GitRelativePath {
	private GitPathRoot absoluteEquivalent;

	GitEmptyPath(GitPathRoot absoluteEquivalent) {
		this.absoluteEquivalent = checkNotNull(absoluteEquivalent);
		checkArgument(absoluteEquivalent.getRoot().toStaticRev().equals(GitPathRoot.DEFAULT_GIT_REF));
	}

	/**
	 * Returns a git path root referring to the main branch of the git file system
	 * associated to this path.
	 */
	@Override
	public GitPathRoot toAbsolutePath() {
		return absoluteEquivalent;
	}

	@Override
	Path getInternalPath() {
		return GitAbstractFileSystem.JIM_FS_EMPTY;
	}
}
