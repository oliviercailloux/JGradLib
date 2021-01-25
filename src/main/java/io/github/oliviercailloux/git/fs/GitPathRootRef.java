package io.github.oliviercailloux.git.fs;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.Optional;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;

/**
 *
 * A {@link GitPathRoot} containing a git ref.
 *
 */
public class GitPathRootRef extends GitPathRoot {

	private GitPathRootSha sha;

	protected GitPathRootRef(GitFileSystem fileSystem, GitRev gitRev) {
		super(fileSystem, gitRev);
		checkArgument(gitRev.isRef());
		sha = null;
	}

	@Override
	public GitPathRootSha toSha() throws IOException, NoSuchFileException {
		refreshCache();
		if (sha == null) {
			throw new NoSuchFileException(toString());
		}
		return sha;
	}

	private void refreshCache() throws IOException, NoSuchFileException {
		final Optional<ObjectId> newIdOpt = getFileSystem().getObjectId(getGitRef());
		final GitPathRootSha newSha;
		if (newIdOpt.isPresent()) {
			final ObjectId newId = newIdOpt.get();
			/**
			 * We try to hold to our existing reference if possible, because it may contain
			 * valuable cache data.
			 */
			if (sha == null || !sha.getStaticCommitId().equals(newId)) {
				newSha = getFileSystem().getPathRoot(newId);
			} else {
				newSha = sha;
			}
		} else {
			newSha = null;
		}
		sha = newSha;
	}

	@Override
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
		refreshCache();
		if (sha == null) {
			return false;
		}
		return sha.exists();
	}

	@Override
	RevCommit getRevCommit() throws IOException, NoSuchFileException {
		return toSha().getRevCommit();
	}

}
