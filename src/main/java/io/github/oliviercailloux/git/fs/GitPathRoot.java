package io.github.oliviercailloux.git.fs;

import static com.google.common.base.Verify.verifyNotNull;

import org.eclipse.jgit.lib.ObjectId;

public class GitPathRoot extends GitPath implements GitRev {

	protected GitPathRoot(GitFileSystem fileSystem, GitStaticRev root) {
		super(fileSystem, root, GitFileSystem.JIM_FS_SLASH);
		verifyNotNull(root);
	}

	public GitStaticRev toStaticRev() {
		return getRootComponent();
	}

	@Override
	public boolean isRef() {
		return getRootComponent().isRef();
	}

	@Override
	public boolean isCommitId() {
		return getRootComponent().isCommitId();
	}

	@Override
	public String getGitRef() {
		return getRootComponent().getGitRef();
	}

	@Override
	public ObjectId getCommitId() {
		return getRootComponent().getCommitId();
	}

	@Override
	public String toString() {
		return getRootComponent().toString();
	}
}
