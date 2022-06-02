package io.github.oliviercailloux.git.fs;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Verify.verify;

import java.io.IOException;
import java.util.Optional;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GitPathRootShaCached extends GitPathRootSha {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GitPathRootShaCached.class);

	protected GitPathRootShaCached(GitFileSystem fileSystem, GitRev gitRev, RevCommit commit) {
		super(fileSystem, gitRev, Optional.of(commit));
		checkArgument(gitRev.isCommitId());
		checkArgument(commit.getId().equals(gitRev.getCommitId()));
	}

	@Override
	public GitPathRootShaCached toSha() {
		return this;
	}

	@Override
	public GitPathRootShaCached toShaCached() throws IOException {
		return this;
	}

	@Override
	public boolean exists() {
		return true;
	}

	@Override
	RevCommit getRevCommit() {
		verify(!revCommit.isEmpty());
		return revCommit.get();
	}

	@Override
	public Commit getCommit() {
		return Commit.create(getRevCommit());
	}
}
