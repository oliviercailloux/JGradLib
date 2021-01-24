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

import com.google.common.base.VerifyException;
import com.google.common.collect.Streams;
import com.google.common.jimfs.Jimfs;

import io.github.oliviercailloux.git.fs.GitFileSystem.FollowLinksBehavior;
import io.github.oliviercailloux.git.fs.GitFileSystem.GitObject;
import io.github.oliviercailloux.git.fs.GitFileSystem.NoContextNoSuchFileException;

/**
 * A git path with a root component and a non empty sequence of non-empty names.
 */
class GitAbsolutePathWithInternal extends GitAbsolutePath {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GitAbsolutePathWithInternal.class);

	private final GitPathRoot root;
	private final Path internalPath;

	/**
	 * Needed to check whether our root has changed (possible if it is a git rev),
	 * in which case our cache becomes invalid.
	 */
	private GitObject cachedRootObject;

	/**
	 * A cache. This is good value for money (price is just a bit of memory): about
	 * every operation on such file requires its git object (generally its object id
	 * and sometimes its file mode as well), and retrieving it is costly as it
	 * requires to navigate the whole path from tree to tree.
	 *
	 * For example, when listing files in a directory (using Files.find), the
	 * algorithm navigates to a folder, gets a sub-folder, at which time the git
	 * object corresponding to the sub-folder is known, but if not cached, when the
	 * algorithm then asks for the file attributes, and then for the listing the
	 * sub-directory, it has to navigate to it again, twice.
	 *
	 * Its path is the real path this object designates, following the links but the
	 * last component. E.g. if this path is <i>a/b</i> and <i>a</i> is a link to
	 * <i>c</i> and <i>b</i>, a link to <i>d</i>, the git object will have
	 * <i>c/b</i> as a real path.
	 *
	 * When one of real and link is set, the other cache variable is set as well if
	 * possible, so that it is never necessary to check both variables when querying
	 * the cache.
	 */
	private GitObject cachedRealGitObject;

	/**
	 * The git object this one links to (following all links), if this real git
	 * object is a link. Not a link.
	 */
	private GitObject cachedLinkGitObject;

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
		cachedRootObject = null;
		cachedRealGitObject = null;
		cachedLinkGitObject = null;
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
		final GitFileSystem fileSystem = getFileSystem();
		return new GitRelativePathWithInternal(
				new GitAbsolutePathWithInternal(fileSystem.mainSlash, getInternalPath()));
	}

	@Override
	GitObject getGitObject(FollowLinksBehavior behavior)
			throws NoSuchFileException, PathCouldNotBeFoundException, IOException {
		final GitObject current = root.getGitObject();
		if (!current.equals(cachedRootObject)) {
			cachedRealGitObject = null;
			cachedLinkGitObject = null;
		}

		final GitObject toReturn;
		switch (behavior) {
		case FOLLOW_LINKS_BUT_END:
			if (cachedRealGitObject == null) {
				doGetGitObject(current, behavior);
			}
			toReturn = cachedRealGitObject;
			break;
		case FOLLOW_ALL_LINKS:
			if (cachedLinkGitObject == null) {
				doGetGitObject(current, behavior);
			}
			toReturn = cachedLinkGitObject;
			break;
		case DO_NOT_FOLLOW_LINKS:
			if (cachedRealGitObject == null) {
				doGetGitObject(current, behavior);
			}
			if (cachedRealGitObject.getRealPath().equals(internalPath)) {
				toReturn = cachedRealGitObject;
			} else {
				throw new PathCouldNotBeFoundException(toString());
			}
			break;
		default:
			throw new VerifyException();
		}

		verify(toReturn != null);
		return toReturn;
	}

	private GitObject doGetGitObject(GitObject rootObject, FollowLinksBehavior behavior)
			throws IOException, NoSuchFileException, PathCouldNotBeFoundException {
		LOGGER.debug("Getting git object of {} with behavior {}.", toString(), behavior);
		final Path relative = GitFileSystem.JIM_FS_SLASH.relativize(getInternalPath());
		final RevTree tree = getRoot().getRevTree();

		LOGGER.debug("Searching for {} in {}.", relative, tree);
		final GitObject gitObject;
		try {
			gitObject = getFileSystem().getGitObject(tree, relative, behavior);
		} catch (@SuppressWarnings("unused") NoContextNoSuchFileException e) {
			throw new NoSuchFileException(toString(), null, e.getMessage());
		}
		LOGGER.debug("Found object {}.", gitObject);

		if (behavior.equals(FollowLinksBehavior.FOLLOW_ALL_LINKS)) {
			setLinkGitObject(rootObject, gitObject);
		} else {
			setRealGitObject(rootObject, gitObject);
		}
		return gitObject;
	}

	@Override
	RevTree getRevTree(boolean followLinks) throws NoSuchFileException, NotDirectoryException, IOException {
		LOGGER.debug("Getting tree of {} following links? {}.", toString(), followLinks);
		final GitObject obj = getGitObject(
				followLinks ? FollowLinksBehavior.FOLLOW_ALL_LINKS : FollowLinksBehavior.DO_NOT_FOLLOW_LINKS);

		if (!obj.getFileMode().equals(FileMode.TYPE_TREE)) {
			throw new NotDirectoryException(toString());
		}
		final RevTree tree = getFileSystem().getRevTree(obj.getObjectId());
		LOGGER.debug("Found tree.", tree);
		return tree;
	}

	void setRealGitObject(GitObject rootObject, GitObject gitObject) {
		cachedRootObject = checkNotNull(rootObject);
		cachedRealGitObject = checkNotNull(gitObject);
		if (!cachedRealGitObject.getFileMode().equals(FileMode.TYPE_SYMLINK)) {
			cachedLinkGitObject = cachedRealGitObject;
		}
	}

	void setLinkGitObject(GitObject rootObject, GitObject gitObject) {
		cachedRootObject = checkNotNull(rootObject);
		cachedLinkGitObject = checkNotNull(gitObject);
	}
}
