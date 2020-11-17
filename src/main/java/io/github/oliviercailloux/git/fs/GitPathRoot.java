package io.github.oliviercailloux.git.fs;

import static com.google.common.base.Verify.verify;
import static com.google.common.base.Verify.verifyNotNull;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.Optional;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is not a git rev, although it shares some similar characteristics with a
 * git rev. Its string form ends with // (whereas the string form of a git rev
 * ends with a single /); and it never equals a git rev.
 */
public class GitPathRoot extends GitPath {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GitPathRoot.class);

	protected GitPathRoot(GitFileSystem fileSystem, GitRev root) {
		super(fileSystem, root, GitFileSystem.JIM_FS_SLASH);
		verifyNotNull(root);
	}

	public GitRev toStaticRev() {
		return getRootComponent();
	}

	public boolean isRef() {
		return getRootComponent().isRef();
	}

	public boolean isCommitId() {
		return getRootComponent().isCommitId();
	}

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
		return getRootComponent().getGitRef();
	}

	public ObjectId getStaticCommitId() {
		return getRootComponent().getCommitId();
	}

	/**
	 * Returns <code>true</code> iff the commit referred to by this path root exists
	 * in this git repository.
	 *
	 * @throws IOException
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
		final ObjectId possibleCommitId;
		if (isRef()) {
			final Optional<ObjectId> objectId = getFileSystem().getObjectId(getGitRef());
			if (objectId.isEmpty()) {
				return Optional.empty();
			}
			possibleCommitId = objectId.get();
		} else {
			possibleCommitId = getStaticCommitId();
		}

		try {
			return Optional.of(getFileSystem().getRevCommit(possibleCommitId));
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

	RevTree getRevTree() throws IOException, NoSuchFileException {
		return getRevCommit().getTree();
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
	public RevCommit getCommit() throws IOException, NoSuchFileException {
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
		return getRevCommit();
	}

	@Override
	public String toString() {
		return getRootComponent().toString();
	}
}
