package io.github.oliviercailloux.git.fs;

import static com.google.common.base.Preconditions.checkNotNull;

import io.github.oliviercailloux.gitjfs.ForwardingGitFileSystemProvider;
import io.github.oliviercailloux.gitjfs.GitFileSystemProvider;

public class GitFilteringFsProvider extends ForwardingGitFileSystemProvider {
	private final GitFileSystemProvider delegate;

	public GitFilteringFsProvider(GitFileSystemProvider delegate) {
		this.delegate = checkNotNull(delegate);
	}

	@Override
	protected GitFileSystemProvider delegate() {
		return delegate;
	}
	
}
