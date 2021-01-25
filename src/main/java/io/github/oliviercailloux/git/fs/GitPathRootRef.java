package io.github.oliviercailloux.git.fs;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.IOException;
import java.nio.file.NoSuchFileException;

import org.eclipse.jgit.lib.ObjectId;

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
		final ObjectId newId = fetchObjectId();
		/**
		 * We try to hold to our existing reference if possible, because it may contain
		 * valuable cache data.
		 */
		if (sha == null || !sha.getStaticCommitId().equals(newId)) {
			sha = getFileSystem().getPathRoot(newId);
		}
		return sha;
	}

}
