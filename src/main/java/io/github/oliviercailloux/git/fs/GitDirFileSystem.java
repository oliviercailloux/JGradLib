package io.github.oliviercailloux.git.fs;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;

import org.eclipse.jgit.internal.storage.file.FileRepository;

public class GitDirFileSystem extends GitRepoFileSystem {

	/**
	 * TODO think about forbidding to create a file system if the path does not
	 * exist (how does the default provider react?)
	 *
	 * @throws IOException the repository appears to already exist but cannot be
	 *                     accessed.
	 */
	@SuppressWarnings("resource")
	public static GitDirFileSystem given(GitFileSystemProvider provider, Path gitDir) throws IOException {
		checkArgument(gitDir.getFileSystem().equals(FileSystems.getDefault()));
		FileRepository fileRepository = null;
		try {
			fileRepository = new FileRepository(gitDir.toFile());
			final GitDirFileSystem fs = new GitDirFileSystem(provider, fileRepository);
			return fs;
		} catch (Exception e) {
			if (fileRepository != null) {
				fileRepository.close();
			}
			throw e;
		}
	}

	public static GitDirFileSystem given(GitFileSystemProvider provider, FileRepository repository) {
		return new GitDirFileSystem(provider, repository);
	}

	/**
	 * Must be default FS because of limitations of JGit.
	 */
	private final Path gitDir;

	private GitDirFileSystem(GitFileSystemProvider gitProvider, FileRepository repository) {
		super(gitProvider, repository);
		this.gitDir = checkNotNull(repository.getDirectory()).toPath();
	}

	public Path getGitDir() {
		return gitDir;
	}

	@Override
	public void close() throws IOException {
		super.close();
		provider().hasBeenClosedEvent(this);
	}

}
