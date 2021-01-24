package io.github.oliviercailloux.git.fs;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Verify.verify;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Optional;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

import io.github.oliviercailloux.git.fs.GitFileSystem.FollowLinksBehavior;
import io.github.oliviercailloux.git.fs.GitFileSystem.GitObject;

/**
 * A git path root is an absolute git path that has an empty sequence of names.
 * In other words, it consists in a root component only. Its string form ends
 * with <tt>//</tt>.
 *
 * @see GitPath
 */
public class GitPathRoot extends GitAbsolutePath {
	public static final GitRev DEFAULT_GIT_REF = GitRev.shortRef("refs/heads/main");

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GitPathRoot.class);

	private final GitFileSystem fileSystem;

	private final GitRev gitRev;

	private GitObject cachedGitObject;

	/**
	 * This is not a git rev, although it shares some similar characteristics with a
	 * git rev. Its string form ends with //, whereas the string form of a git rev
	 * ends with a single /; and it never equals a git rev.
	 */
	GitPathRoot(GitFileSystem fileSystem, GitRev gitRev) {
		this.fileSystem = checkNotNull(fileSystem);
		this.gitRev = checkNotNull(gitRev);
		cachedGitObject = null;
	}

	@Override
	Path getInternalPath() {
		return GitFileSystem.JIM_FS_SLASH;
	}

	@Override
	public GitFileSystem getFileSystem() {
		return fileSystem;
	}

	/**
	 * Returns itself.
	 *
	 * @return itself
	 */
	@Override
	public GitPathRoot getRoot() {
		return this;
	}

	/**
	 * Returns this path.
	 */
	@Override
	public GitPathRoot toAbsolutePath() {
		return this;
	}

	@Override
	GitEmptyPath toRelativePath() {
		return fileSystem.emptyPath;
	}

	GitRev toStaticRev() {
		return gitRev;
	}

	/**
	 * Indicates whether this root component contains a git ref or a commit id.
	 *
	 * @return <code>true</code> iff this root component contains a git ref;
	 *         equivalently, iff this root component does not contain a commit id.
	 */
	public boolean isRef() {
		return gitRev.isRef();
	}

	/**
	 * Indicates whether this root component contains a commit id or a git ref.
	 *
	 * @return <code>true</code> iff this root component contains a commit id;
	 *         equivalently, iff this root component does not contain a git ref.
	 */
	public boolean isCommitId() {
		return gitRev.isCommitId();
	}

	/**
	 * Returns the git ref contained in this root component, if any. The returned
	 * string starts with <tt>refs/</tt>, does not contain <tt>//</tt>, does not
	 * contain <tt>\</tt>, and does not end with <tt>/</tt>.
	 * <p>
	 * This method does not access the file system.
	 *
	 * @return the git ref contained in this root component.
	 * @throws IllegalArgumentException iff this root component does not contain a
	 *                                  git ref
	 * @see #isRef()
	 */
	public String getGitRef() {
		/**
		 * Returning a JGit Ref here is another possibility. But 1) a Ref is much more
		 * complex than required at this level: JGit’s Ref objects include symbolic
		 * refs, they may be peeled or non peeled, and they may refer to git objects
		 * that are not commits. Git refs as considered here are only direct pointers to
		 * commits. And 2) a Ref may return a null commit id; I prefer to guarantee that
		 * this library never returns null. (Admittedly, I have to allow for exceptions
		 * when using third party objects, for example Map can return null, but I prefer
		 * to reduce exceptions as much as possible.)
		 */
		return gitRev.getGitRef();
	}

	/**
	 * Returns the commit id contained in this root component, if any. The method is
	 * called <tt>static</tt> because the returned id is simply the one that was
	 * given when constructing this path. This method does not attempt to check that
	 * the returned id indeed corresponds to some commit in this file system.
	 *
	 * @return the commit id contained in this root component.
	 * @throws IllegalArgumentException iff this root component does not contain a
	 *                                  commit id
	 * @see #isCommitId()
	 */
	public ObjectId getStaticCommitId() {
		return gitRev.getCommitId();
	}

	/**
	 * Returns <code>true</code> iff the commit referred to (possibly indirectly) by
	 * this git path root exists in the associated git file system.
	 * <p>
	 * Returns <code>false</code> when either:
	 * <ul>
	 * <li>this path root contains a git ref which does not exist in this
	 * repository;</li>
	 * <li>this path root contains a git ref which refers to a git object that is
	 * not a commit;</li>
	 * <li>this path root contains a commit id that does not exist.</li>
	 *
	 * @throws IOException if an error occurs while accessing the underlying
	 *                     repository
	 *
	 * @see #getCommit()
	 */
	public boolean exists() throws IOException {
		/**
		 * NB this exists-based approach (rather than Optional on getCommit) seems
		 * adequate because most of the time, the user will use commit ids, coming from
		 * the history or the set of roots of this fs, and thus it is known that the
		 * related commit exists. Similarly, if the user uses some ref, she must have
		 * learned from somewhere that this ref exists in this repo. Only if the user
		 * accesses the main branch should she test its existence, and even there,
		 * perhaps she knows that this branch exists (e.g. her own repositories).
		 */
		return tryGetRevCommit().isPresent();
	}

	/**
	 * Returns empty when either 1) the ref points nowhere; 2) the ref points to an
	 * object that is not a commit; 3) this is not a ref but a commit id directly
	 * and it does not exist.
	 */
	Optional<RevCommit> tryGetRevCommit() throws IOException {
		LOGGER.debug("Trying to get rev commit of {}.", toString());
		final ObjectId possibleCommitId;
		if (isRef()) {
			final Optional<ObjectId> objectId = fileSystem.getObjectId(getGitRef());
			if (objectId.isEmpty()) {
				return Optional.empty();
			}
			possibleCommitId = objectId.get();
		} else {
			possibleCommitId = getStaticCommitId();
		}

		try {
			final RevCommit revCommit = fileSystem.getRevCommit(possibleCommitId);
			LOGGER.debug("Returning with rev commit.");
			return Optional.of(revCommit);
		} catch (IncorrectObjectTypeException e) {
			LOGGER.info("Tried to access a non-commit as a commit: " + e + ".");
			return Optional.empty();
		} catch (@SuppressWarnings("unused") MissingObjectException e) {
			verify(isCommitId());
			return Optional.empty();
		}
	}

	RevCommit getRevCommit() throws IOException, NoSuchFileException {
		final Optional<RevCommit> commit = tryGetRevCommit();
		if (commit.isEmpty()) {
			throw new NoSuchFileException(toString());
		}
		return commit.get();
	}

	@Override
	RevTree getRevTree(boolean followLinks) throws IOException, NoSuchFileException {
		return getRevTree();
	}

	RevTree getRevTree() throws IOException, NoSuchFileException {
		final RevTree tree = getRevCommit().getTree();
		/**
		 * Note that if I am a git rev, there is no point in storing the git object as
		 * we need to check that it has not changed, each time.
		 */
		if (isCommitId()) {
			cachedGitObject = GitObject.given(GitFileSystem.JIM_FS_SLASH, tree, FileMode.TREE);
		}
		return tree;
	}

	/**
	 * TODO return an own type Commit with nice getTime, no getTree (unuseful),
	 * getParents() that return other commits, and so on.
	 *
	 * If {@link #exists()} returns <code>false</code>, an exception is thrown.
	 *
	 * @return
	 * @throws IOException
	 * @throws NoSuchFileException
	 */
	public Commit getCommit() throws IOException, NoSuchFileException {
		/**
		 * I considered using dynamic fetching in the returned object: if the user only
		 * wants the commit id, we don’t need to parse the commit, thus, we could parse
		 * the commit on-demand. But this introduces complexities (we have to document
		 * that sometimes, the Commit is bound to a file system and should be fetched
		 * while the fs is still open), and we don’t gain much: I can’t imagine cases
		 * where the user will want a vast series of commit ids without having to parse
		 * them. Mostly, a vast series of commits would come from a desire to browse
		 * (part of) the history, and this requires accessing the parent-of relation,
		 * which requires parsing the commit.
		 */
		return Commit.create(getRevCommit());
	}

	public ImmutableList<GitPathRoot> getParentCommits() throws IOException, NoSuchFileException {
		final RevCommit revCommit = getRevCommit();
		final ImmutableList<RevCommit> parents = ImmutableList.copyOf(revCommit.getParents());

		final ImmutableList.Builder<GitPathRoot> builder = ImmutableList.builder();
		for (ObjectId parentId : parents) {
			builder.add(getFileSystem().getPathRoot(parentId));
		}
		return builder.build();
	}

	@Override
	GitObject getGitObject(FollowLinksBehavior behavior) throws NoSuchFileException, IOException {
		return getGitObject();
	}

	GitObject getGitObject() throws NoSuchFileException, IOException {
		if (cachedGitObject != null) {
			verify(isCommitId());
			/** Never changes! */
			return cachedGitObject;
		}
		final GitObject gitObject = GitObject.given(GitFileSystem.JIM_FS_SLASH, getRevTree(), FileMode.TREE);
		return gitObject;
	}
}
