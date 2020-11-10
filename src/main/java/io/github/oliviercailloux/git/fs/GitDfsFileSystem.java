package io.github.oliviercailloux.git.fs;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Verify.verifyNotNull;

import org.eclipse.jgit.internal.storage.dfs.DfsRepository;

/**
 * A git file system that rests on a (user-managed) {@link DfsRepository}.
 *
 * @see GitFileSystemProvider#newFileSystemFromDfsRepository(DfsRepository)
 */
public class GitDfsFileSystem extends GitFileSystem {

	static GitDfsFileSystem givenUserRepository(GitFileSystemProvider provider, DfsRepository repository) {
		return new GitDfsFileSystem(provider, repository);
	}

	private GitDfsFileSystem(GitFileSystemProvider gitProvider, DfsRepository repository) {
		super(gitProvider, repository, false);
		verifyNotNull(repository.getDescription());
		checkArgument(repository.getDescription().getRepositoryName() != null);
	}

	@Override
	public DfsRepository getRepository() {
		return (DfsRepository) super.getRepository();
	}

}
