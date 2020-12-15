package io.github.oliviercailloux.git.fs;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import java.nio.file.InvalidPathException;
import java.util.Comparator;
import java.util.Objects;

import org.eclipse.jgit.errors.InvalidObjectIdException;
import org.eclipse.jgit.lib.ObjectId;

/**
 * Contains either a git ref or a commit id.
 * <ul>
 * <li>A git ref is a string that starts with refs/, does not contain "//" or
 * "\" and does not end with "/".</li>
 * <li>A commit id is a SHA-1.</li>
 * </ul>
 * <p>
 * This object is not associated to a file system, in other words, it only
 * contains “static” information and it may not represent anything that exists
 * in a file system. Its checks are only syntactic.
 * <p>
 * May be created using a git ref short form: this starts with refs/ or heads/
 * or tags/.
 * <p>
 * The string form of a git rev is "/" then the git ref then "//", or "/" then a
 * 40 (?) characters lower case number in hexadecimal form then "//".
 */
class GitRev {
	/**
	 * Compares without access to the file system and including those roots that do
	 * not exist in an underlying file system. This object does not implement
	 * Comparable because multiple implementations of comparisons exist that could
	 * be considered natural. For example, with access to a given file system, one
	 * could want to order roots by commit date.
	 */
	static final Comparator<GitRev> SYNTAXIC_COMPARATOR = Comparator.comparingInt((GitRev r) -> r.isRef() ? 0 : 1)
			.thenComparing(GitRev::toString);

	public static GitRev stringForm(String stringForm) throws InvalidPathException {
		checkPath(stringForm.startsWith("/"), stringForm, "Must start with /");
		checkPath(stringForm.endsWith("/"), stringForm, "Must end with /");
		checkPath(!stringForm.equals("/"), stringForm, "Must not be /");
		return shortRefOrCommitId(stringForm.substring(1, stringForm.length() - 1));
	}

	private static void checkPath(boolean check, String input, String reason) throws InvalidPathException {
		if (!check) {
			throw new InvalidPathException(input, reason);
		}
	}

	/**
	 * @param shortRefOrCommitId must start with <tt>refs/</tt> or <tt>heads/</tt>
	 *                           or <tt>tags/</tt> or be a commit id. May not end
	 *                           with <tt>/</tt>, nor contain <tt>//</tt> or
	 *                           <tt>\</tt>.
	 * @return
	 * @throws InvalidPathException
	 */
	public static GitRev shortRefOrCommitId(String shortRefOrCommitId) throws InvalidPathException {
		if (shortRefOrCommitId.startsWith("refs/") || shortRefOrCommitId.startsWith("heads/")
				|| shortRefOrCommitId.startsWith("tags/")) {
			return shortRef(shortRefOrCommitId);
		}
		final ObjectId commitId;
		try {
			commitId = ObjectId.fromString(shortRefOrCommitId);
		} catch (InvalidObjectIdException e) {
			throw new InvalidPathException(shortRefOrCommitId, e.getMessage());
		}
		return commitId(commitId);
	}

	public static GitRev shortRef(String shortRef) {
		final String extendedRef;
		if (shortRef.startsWith("refs/")) {
			extendedRef = shortRef;
		} else if (shortRef.startsWith("heads/") || shortRef.startsWith("tags/")) {
			extendedRef = "refs/" + shortRef;
		} else {
			throw new IllegalArgumentException("The given ref must start with refs/, heads/, or tags/.");
		}
		return new GitRev(extendedRef, null);
	}

	/**
	 * @param branch not starting with /
	 * @return
	 */
	public static GitRev branch(String branch) {
		final String extendedRef;
		if (branch.startsWith("refs/heads/")) {
			extendedRef = branch;
		} else if (branch.startsWith("heads/")) {
			extendedRef = "refs/" + branch;
		} else {
			extendedRef = "refs/heads/" + branch;
		}
		return new GitRev(extendedRef, null);
	}

	public static GitRev commitId(String commitId) {
		return commitId(ObjectId.fromString(commitId));
	}

	public static GitRev commitId(ObjectId objectId) {
		return new GitRev(null, objectId);
	}

	private final String gitRef;
	private final ObjectId commitId;

	private GitRev(String gitRef, ObjectId commitId) {
		final boolean hasObjectId = commitId != null;
		final boolean hasRef = gitRef != null;
		checkArgument(hasRef != hasObjectId);
		if (hasRef) {
			assert gitRef != null;
			checkArgument(gitRef.startsWith("refs/"));
			checkArgument(!gitRef.endsWith("/"));
			checkArgument(!gitRef.contains("//"));
			checkArgument(!gitRef.contains("\\"));
		}
		this.gitRef = gitRef;
		this.commitId = commitId;
	}

	/**
	 * @return <code>true</code> iff not {@link #isCommitId()}
	 */
	public boolean isRef() {
		return gitRef != null;
	}

	/**
	 * @return <code>true</code> iff not {@link #isRef()}
	 */
	public boolean isCommitId() {
		return commitId != null;
	}

	/**
	 * Must contain a ref.
	 *
	 * @return starts with <tt>refs/</tt>, does not contain <tt>//</tt>, does not
	 *         contain <tt>\</tt>, does not end with <tt>/</tt>
	 */
	public String getGitRef() {
		checkState(gitRef != null);
		return gitRef;
	}

	/**
	 * Must contain a commit id.
	 */
	public ObjectId getCommitId() {
		checkState(commitId != null);
		return commitId;
	}

	/**
	 * A git rev equals another one iff they contain equal git refs, or they contain
	 * equal commit ids.
	 */
	@Override
	public boolean equals(Object o2) {
		if (!(o2 instanceof GitRev)) {
			return false;
		}
		final GitRev r2 = (GitRev) o2;
		return Objects.equals(gitRef, r2.gitRef) && Objects.equals(commitId, r2.commitId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(gitRef, commitId);
	}

	/**
	 * @return the string form of this git rev
	 */
	@Override
	public String toString() {
		return "/" + (gitRef == null ? commitId.getName() : gitRef) + "/";
	}
}
