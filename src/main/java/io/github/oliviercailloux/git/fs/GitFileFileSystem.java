package io.github.oliviercailloux.git.fs;

import static com.google.common.base.Preconditions.checkNotNull;

import java.net.URI;
import java.nio.file.Path;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A git file system that obtains its commit data by reading from a git
 * directory.
 *
 * @see GitFileSystemProvider#newFileSystemFromGitDir(Path)
 * @see GitFileSystemProvider#newFileSystemFromFileRepository(FileRepository)
 */
public class GitFileFileSystem extends GitFileSystem {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GitFileFileSystem.class);

	static GitFileFileSystem givenUserRepository(GitFileSystemProvider provider, FileRepository repository) {
		return new GitFileFileSystem(provider, repository, false);
	}

	static GitFileFileSystem givenOurRepository(GitFileSystemProvider provider, FileRepository repository) {
		return new GitFileFileSystem(provider, repository, true);
	}

	/**
	 * See {@link GitDfsFileSystem}.
	 */
	private final FileRepository repository;

	private GitFileFileSystem(GitFileSystemProvider gitProvider, FileRepository repository,
			boolean shouldCloseRepository) {
		super(gitProvider, repository, shouldCloseRepository);
		LOGGER.debug("Creating file system given {}, {}, {}.", gitProvider, repository, shouldCloseRepository);
		checkNotNull(repository.getDirectory());
		this.repository = repository;
	}

	/**
	 * @deprecated Temporary workaround.
	 */
	@Deprecated
	public Repository getRepository() {
		return repository;
	}

	/**
	 * Returns the git directory this file system reads its data from. This is
	 * typically, but not necessarily, a directory named “.git”.
	 */
	public Path getGitDir() {
		return repository.getDirectory().toPath();
	}

	/**
	 * <p>
	 * Returns a gitjfs URI that identifies this git file system by referring to the
	 * git directory that it obtains its data from. The returned URI also identifies
	 * this specific git file system instance, while it is open.
	 * </p>
	 * <p>
	 * While this file system is open, the returned URI can be given to
	 * {@link GitFileSystemProvider#getFileSystem(URI)} to obtain this file system
	 * instance back; or to {@link GitFileSystemProvider#getPath(URI)} to obtain the
	 * default path associated to this file system. It can also be given to
	 * {@link GitFileSystemProvider#newFileSystem(URI)} to obtain a new file system
	 * that obtains its data by reading from the same git directory, in a new VM
	 * instance or after this one has been closed. (This identifier should not
	 * however be considered stable accross releases of this library. Please open an
	 * issue if this creates a problem.)
	 * </p>
	 *
	 * @return the URI that identifies this git file system.
	 */
	@Override
	public URI toUri() {
		return super.toUri();
	}
}
