package io.github.oliviercailloux.git.fs;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Verify.verify;

import java.nio.file.Path;

import com.google.common.collect.Streams;
import com.google.common.jimfs.Jimfs;

class GitPathNonRoot extends GitAbsolutePath {

	private GitPathRoot root;
	private Path internalPath;

	GitPathNonRoot(GitPathRoot root, Path internalPath) {
		this.root = checkNotNull(root);
		this.internalPath = checkNotNull(internalPath);
		checkArgument(internalPath.isAbsolute());
		verify(internalPath.getRoot() != null);
		checkArgument(internalPath.getNameCount() >= 1);
		checkArgument(internalPath.getFileSystem().provider().getScheme().equals(Jimfs.URI_SCHEME));
		final boolean noSlashInNames = Streams.stream(internalPath).noneMatch(p -> p.toString().contains("/"));
		verify(noSlashInNames);
		final boolean hasEmptyName = Streams.stream(internalPath).anyMatch(p -> p.toString().isEmpty());
		verify(!hasEmptyName);
	}

	@Override
	Path getInternalPath() {
		return internalPath;
	}

	@Override
	public GitFileSystem getFileSystem() {
		return root.getFileSystem();
	}

	@Override
	public GitPathRoot getRoot() {
		return root;
	}

	@Override
	GitRelativeInternalPath toRelativePath() {
		return new GitRelativeInternalPath(this);
	}
}
