package io.github.oliviercailloux.git.fs;

import static com.google.common.base.Preconditions.checkArgument;

import java.nio.file.Path;

abstract class GitRelativePath extends GitPath {

	static GitRelativePath relative(GitFileSystem fs, Path internalPath) {
		checkArgument(!internalPath.isAbsolute());
		checkArgument(internalPath.getNameCount() >= 1);

		if (internalPath.toString().equals("")) {
			return fs.defaultPath;
		}

		final GitPathRoot root = fs.mainSlash;
		final GitPathNonRoot absolute = new GitPathNonRoot(root, internalPath.toAbsolutePath());
		return new GitRelativeInternalPath(absolute);
	}

	@Override
	public GitFileSystem getFileSystem() {
		return toAbsolutePath().getFileSystem();
	}

	@Override
	public boolean isAbsolute() {
		return false;
	}

	@Override
	public GitPathRoot getRoot() {
		return null;
	}

	@Override
	GitPath toRelativePath() {
		return this;
	}
}
