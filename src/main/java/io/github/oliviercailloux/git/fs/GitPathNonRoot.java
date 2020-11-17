package io.github.oliviercailloux.git.fs;

import static com.google.common.base.Preconditions.checkArgument;

import java.nio.file.Path;

class GitPathNonRoot extends GitPath {

	protected GitPathNonRoot(GitFileSystem fileSystem, GitRev root, Path dirAndFile) {
		super(fileSystem, root, dirAndFile);
		checkArgument(dirAndFile.getNameCount() >= 1);
	}

}
