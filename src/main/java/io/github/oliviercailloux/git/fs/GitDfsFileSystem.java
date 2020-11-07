package io.github.oliviercailloux.git.fs;

import static com.google.common.base.Preconditions.checkNotNull;

import org.eclipse.jgit.internal.storage.dfs.DfsRepository;

/**
 * A git fs that rests on a (user-managed) DfsRepository
 */
public class GitDfsFileSystem extends GitRepoFileSystem {

	static GitDfsFileSystem givenUserRepository(GitFileSystemProvider provider, DfsRepository repository) {
		return new GitDfsFileSystem(provider, repository, false);
	}

	private GitDfsFileSystem(GitFileSystemProvider gitProvider, DfsRepository repository,
			boolean shouldCloseRepository) {
		super(gitProvider, repository, shouldCloseRepository);
		checkNotNull(repository.getDirectory());
	}

	@Override
	public DfsRepository getRepository() {
		return (DfsRepository) super.getRepository();
	}

	@Override
	public void close() {
		super.close();
		/** TODO. */
//		provider().hasBeenClosedEvent(this);
	}

}
