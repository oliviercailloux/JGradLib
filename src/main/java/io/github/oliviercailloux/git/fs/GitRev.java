package io.github.oliviercailloux.git.fs;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import java.nio.file.InvalidPathException;
import java.util.Comparator;
import java.util.Objects;

import org.eclipse.jgit.errors.InvalidObjectIdException;
import org.eclipse.jgit.lib.ObjectId;

public class GitRev {
	public static final GitRev DEFAULT = ref("refs/heads/main");
	/**
	 * Compares without access to the file system and including those roots that do
	 * not exist an underlying file system. This object does not implement
	 * Comparable because is not the only natural implementation of a comparison.
	 * For example, with access to a given file system, one could want to order
	 * roots by commit date.
	 */
	static final Comparator<GitRev> SYNTAXIC_COMPARATOR = Comparator.comparingInt((GitRev r) -> r.isRef() ? 0 : 1)
			.thenComparing(GitRev::toString);

	public static GitRev stringForm(String stringForm) throws InvalidPathException {
		checkPath(stringForm.startsWith("/"), stringForm, "Must start with /");
		checkPath(stringForm.endsWith("/"), stringForm, "Must end with /");
		checkPath(!stringForm.equals("/"), stringForm, "Must not be /");
		return refOrCommitId(stringForm.substring(1, stringForm.length() - 1));
	}

	private static void checkPath(boolean check, String input, String reason) throws InvalidPathException {
		if (!check) {
			throw new InvalidPathException(input, reason);
		}
	}

	public static GitRev refOrCommitId(String refOrCommitId) throws InvalidPathException {
		if (refOrCommitId.startsWith("refs/") || refOrCommitId.startsWith("heads/")
				|| refOrCommitId.startsWith("tags/")) {
			return ref(refOrCommitId);
		}
		final ObjectId commitId;
		try {
			commitId = ObjectId.fromString(refOrCommitId);
		} catch (InvalidObjectIdException e) {
			throw new InvalidPathException(refOrCommitId, e.getMessage());
		}
		return commitId(commitId);
	}

	public static GitRev ref(String ref) {
		final String extendedRef;
		if (ref.startsWith("refs/")) {
			extendedRef = ref;
		} else if (ref.startsWith("heads/") || ref.startsWith("tags/")) {
			extendedRef = "refs/" + ref;
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

	private String gitRef;
	private ObjectId commitId;

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

	public boolean isRef() {
		return gitRef != null;
	}

	public boolean isCommitId() {
		return commitId != null;
	}

	public String getGitRef() {
		checkState(gitRef != null);
		return gitRef;
	}

	public ObjectId getCommitId() {
		checkState(commitId != null);
		return commitId;
	}

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

	@Override
	public String toString() {
		return "/" + (gitRef == null ? commitId.getName() : gitRef) + "/";
	}
}
