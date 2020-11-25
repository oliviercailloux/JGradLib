package io.github.oliviercailloux.git.fs;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Verify.verify;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.util.Optional;

import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.revwalk.RevTree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Streams;
import com.google.common.jimfs.Jimfs;

import io.github.oliviercailloux.git.fs.GitFileSystem.PathNotFoundException;

class GitPathNonRoot extends GitAbsolutePath {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GitPathNonRoot.class);

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

	@Override
	GitObject getGitObject() throws NoSuchFileException, IOException {
		return getGitObject(Optional.empty());
	}

	GitObject getGitObject(Optional<RevTree> rootTree) throws IOException, NoSuchFileException {
		final String relative = toRelativePath().getInternalPath().toString();
		final RevTree tree;
		if (rootTree.isPresent()) {
			tree = rootTree.get();
		} else {
			tree = getRoot().getRevTree();
		}
		LOGGER.debug("Searching for {} in {}.", relative, tree);
		final GitObject gitObject;
		try {
			gitObject = getFileSystem().getGitObject(tree, relative);
		} catch (@SuppressWarnings("unused") PathNotFoundException e) {
			throw new NoSuchFileException(toString());
		}
		return gitObject;
	}

	@Override
	RevTree getRevTree() throws NoSuchFileException, NotDirectoryException, IOException {
		return getRevTree(Optional.empty());
	}

	RevTree getRevTree(Optional<GitObject> gitObject) throws NoSuchFileException, NotDirectoryException, IOException {
		final GitObject obj;
		if (gitObject.isPresent()) {
			obj = gitObject.get();
		} else {
			obj = getGitObject();
		}

		if (!obj.getFileMode().equals(FileMode.TYPE_TREE)) {
			throw new NotDirectoryException(toString());
		}
		return getFileSystem().getRevTree(obj.getObjectId());
	}
}
