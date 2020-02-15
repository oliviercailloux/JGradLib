package io.github.oliviercailloux.git.fs;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;

import org.eclipse.jgit.internal.storage.file.FileRepository;

/**
 *
 * That this extends {@link GitRepoFileSystem} should be considered an
 * implementation detail; it is subject to change. By contract, however, this
 * class extends GitFileSystem.
 *
 * @author Olivier Cailloux
 *
 */
public class GitFileSystem extends GitRepoFileSystem {

	/**
	 * TODO think about forbidding to create a file system if the path does not
	 * exist (how does the default provider react?)
	 * 
	 * @throws IOException the repository appears to already exist but cannot be
	 *                     accessed.
	 */
	@SuppressWarnings("resource")
	public static GitFileSystem given(GitFileSystemProvider provider, Path gitDir) throws IOException {
		checkArgument(gitDir.getFileSystem().equals(FileSystems.getDefault()));
		FileRepository fileRepository = null;
		try {
			fileRepository = new FileRepository(gitDir.toFile());
			final GitFileSystem fs = new GitFileSystem(provider, gitDir, fileRepository);
			return fs;
		} catch (Exception e) {
			if (fileRepository != null) {
				fileRepository.close();
			}
			throw e;
		}
	}

	/**
	 * Must be default FS because of limitations of JGit.
	 */
	private final Path gitDir;

	private GitFileSystem(GitFileSystemProvider gitProvider, Path gitDir, FileRepository repository) {
		super(gitProvider, repository);
		this.gitDir = checkNotNull(gitDir);
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
