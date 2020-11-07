package io.github.oliviercailloux.git.fs;

import static com.google.common.base.Preconditions.checkNotNull;

import java.nio.file.Path;

import org.eclipse.jgit.internal.storage.file.FileRepository;

/**
 * A git fs that reads from a git directory on the default file system.
 */
public class GitFileFileSystem extends GitRepoFileSystem {

	static GitFileFileSystem givenUserRepository(GitFileSystemProvider provider, FileRepository repository) {
		return new GitFileFileSystem(provider, repository, false);
	}

	static GitFileFileSystem givenOurRepository(GitFileSystemProvider provider, FileRepository repository) {
		return new GitFileFileSystem(provider, repository, true);
	}

	private GitFileFileSystem(GitFileSystemProvider gitProvider, FileRepository repository,
			boolean shouldCloseRepository) {
		super(gitProvider, repository, shouldCloseRepository);
		checkNotNull(repository.getDirectory());
	}

	@Override
	protected FileRepository getRepository() {
		return (FileRepository) super.getRepository();
	}

	/**
	 * Associated to the default file system.
	 */
	public Path getGitDir() {
		return getRepository().getDirectory().toPath();
	}

}
