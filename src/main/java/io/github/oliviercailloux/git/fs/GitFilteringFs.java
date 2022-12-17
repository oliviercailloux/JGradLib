package io.github.oliviercailloux.git.fs;

import io.github.oliviercailloux.gitjfs.GitPath;
import java.nio.file.FileSystem;
import java.nio.file.InvalidPathException;

public abstract class GitFilteringFs extends FileSystem implements IFS {

	private GitFilteringFs() {
//		super(gitProvider, repository, shouldCloseRepository);
	}

	@Override
	public GitPath getAbsolutePath(String first, String... more) throws InvalidPathException {
//	final GitPath path = super.getAbsolutePath(first, more);
//	return new GitPat;
		return null;
	}
}
