package io.github.oliviercailloux.git.fs;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Verify.verifyNotNull;

import org.eclipse.jgit.internal.storage.dfs.DfsRepository;

/**
 * A git file system that rests on a (user-managed) {@link DfsRepository}.
 *
 * @see GitFileSystemProvider#newFileSystemFromDfsRepository(DfsRepository)
 */
public class GitDfsFileSystem extends GitAbstractFileSystem {

	/**
	 * This is the same reference as the one in {@link GitAbstractFileSystem}. I want to
	 * keep the latter private to make it clear that only {@link GitAbstractFileSystem} is
	 * responsible for low-level read operations from the repository.
	 */
	private final DfsRepository repository;

	static GitDfsFileSystem givenUserRepository(GitFileSystemProvider provider, DfsRepository repository) {
		return new GitDfsFileSystem(provider, repository);
	}

	private GitDfsFileSystem(GitFileSystemProvider gitProvider, DfsRepository repository) {
		super(gitProvider, repository, false);
		verifyNotNull(repository.getDescription());
		checkArgument(repository.getDescription().getRepositoryName() != null);
		this.repository = repository;
	}

	public DfsRepository getRepository() {
		return repository;
	}

}
