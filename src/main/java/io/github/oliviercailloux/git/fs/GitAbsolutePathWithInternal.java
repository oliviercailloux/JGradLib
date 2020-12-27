package io.github.oliviercailloux.git.fs;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Verify.verify;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;

import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.revwalk.RevTree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Streams;
import com.google.common.jimfs.Jimfs;

import io.github.oliviercailloux.git.fs.GitFileSystem.NoContextNoSuchFileException;
import io.github.oliviercailloux.git.fs.GitFileSystem.PathCouldNotBeFoundException;

/**
 * A git path with a root component and a non empty sequence of non-empty names.
 */
class GitAbsolutePathWithInternal extends GitAbsolutePath {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GitAbsolutePathWithInternal.class);

	private final GitPathRoot root;
	private final Path internalPath;

	GitAbsolutePathWithInternal(GitPathRoot root, Path internalPath) {
		this.root = checkNotNull(root);
		this.internalPath = checkNotNull(internalPath);
		checkArgument(internalPath.isAbsolute());
		verify(internalPath.getRoot() != null);
		checkArgument(internalPath.getNameCount() >= 1);
		checkArgument(internalPath.getFileSystem().provider().getScheme().equals(Jimfs.URI_SCHEME));
		final boolean slashInNames = Streams.stream(internalPath).anyMatch(p -> p.toString().contains("/"));
		verify(!slashInNames);
		final boolean hasEmptyName = Streams.stream(internalPath).anyMatch(p -> p.toString().isEmpty());
		verify(!hasEmptyName);
	}

	@Override
	public GitAbsolutePath toAbsolutePath() {
		return this;
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
	GitRelativePathWithInternal toRelativePath() {
		if (root.toStaticRev().equals(GitPathRoot.DEFAULT_GIT_REF)) {
			return new GitRelativePathWithInternal(this);
		}
		return new GitRelativePathWithInternal(
				new GitAbsolutePathWithInternal(getFileSystem().mainSlash, getInternalPath()));
	}

	@Override
	GitObject getGitObject(boolean followLinks) throws NoSuchFileException, PathCouldNotBeFoundException, IOException {
		final Path relative = GitFileSystem.JIM_FS_SLASH.relativize(getInternalPath());
		final RevTree tree = getRoot().getRevTree();
		LOGGER.debug("Searching for {} in {}.", relative, tree);
		final GitObject gitObject;
		try {
			gitObject = getFileSystem().getGitObject(tree, relative, followLinks);
		} catch (@SuppressWarnings("unused") NoContextNoSuchFileException e) {
			throw new NoSuchFileException(toString(), null, e.getMessage());
		}
		return gitObject;
	}

	@Override
	RevTree getRevTree(boolean followLinks) throws NoSuchFileException, NotDirectoryException, IOException {
		final GitObject obj = getGitObject(followLinks);

		if (!obj.getFileMode().equals(FileMode.TYPE_TREE)) {
			throw new NotDirectoryException(toString());
		}
		return getFileSystem().getRevTree(obj.getObjectId());
	}
}
