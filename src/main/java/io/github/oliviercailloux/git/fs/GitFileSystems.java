package io.github.oliviercailloux.git.fs;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Verify.verify;
import static io.github.oliviercailloux.jaris.exceptions.Unchecker.URI_UNCHECKER;

import java.net.URI;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.Path;
import java.util.Objects;

import org.eclipse.jgit.internal.storage.dfs.DfsRepository;

import com.google.common.base.VerifyException;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.net.UrlEscapers;

class GitFileSystems {

	private static final String FILE_AUTHORITY = "FILE";
	private static final String DFS_AUTHORITY = "DFS";

	/**
	 * Keys are absolute paths.
	 */
	private final BiMap<Path, GitFileFileSystem> cachedFileFileSystems = HashBiMap.create();
	/**
	 * Key is the repository name, unescaped (original). An empty name is authorized
	 * (this is sensible when the user wishes to have only one in-memory file
	 * system).
	 */
	private final BiMap<String, GitDfsFileSystem> cachedDfsFileSystems = HashBiMap.create();

	public void verifyCanCreateFileSystemCorrespondingTo(Path gitDir) {
		if (cachedFileFileSystems.containsKey(gitDir.toAbsolutePath())) {
			throw new FileSystemAlreadyExistsException();
		}
	}

	public void verifyCanCreateFileSystemCorrespondingTo(DfsRepository dfsRepository) {
		final String name = getName(dfsRepository);
		if (cachedDfsFileSystems.containsKey(name)) {
			throw new FileSystemAlreadyExistsException("A repository with the name ‘" + name + "’ already exists.");
		}
	}

	String getExistingUniqueId(GitRepoFileSystem gitFs) {
		if (gitFs instanceof GitFileFileSystem) {
			final GitFileFileSystem gitFileFs = (GitFileFileSystem) gitFs;
			final Path path = cachedFileFileSystems.inverse().get(gitFileFs);
			checkArgument(path != null);
			verify(path.isAbsolute());
			return path.toString();
		}

		if (gitFs instanceof GitDfsFileSystem) {
			final GitDfsFileSystem gitDfsFs = (GitDfsFileSystem) gitFs;
			final String id = cachedDfsFileSystems.inverse().get(gitDfsFs);
			checkArgument(id != null);
			return id;
		}

		throw new IllegalArgumentException("Unknown repository type.");
	}

	/**
	 * @param gitFileUri must parse as a git file-based filesystem URI.
	 * @return the absolute path in the URI, representing the git directory that
	 *         this URI refers to.
	 */
	public Path getGitDir(URI gitFileUri) {
		checkArgument(Objects.equals(gitFileUri.getScheme(), GitFileSystemProvider.SCHEME));
		checkArgument(Objects.equals(gitFileUri.getAuthority(), FILE_AUTHORITY));
		/**
		 * It follows from these two checks that the uri is absolute (it has a scheme)
		 * and hierarchical (it was further parsed).
		 */
		final String gitDirStr = gitFileUri.getPath();
		/** An hierarchical absolute URI has an absolute path. */
		assert gitDirStr != null;
		checkArgument(gitDirStr.endsWith("/"));
		final Path gitDir = Path.of(gitDirStr);
		verify(gitDir.isAbsolute());
		return gitDir;
	}

	private String getRepositoryName(URI gitDfsUri) {
		checkArgument(Objects.equals(gitDfsUri.getScheme(), GitFileSystemProvider.SCHEME));
		checkArgument(Objects.equals(gitDfsUri.getAuthority(), DFS_AUTHORITY));
		final String path = gitDfsUri.getPath();
		verify(path.startsWith("/"));
		final String name = path.substring(1);
		return name;
	}

	@SuppressWarnings("resource")
	public GitRepoFileSystem getFileSystem(URI gitUri) {
		checkArgument(Objects.equals(gitUri.getScheme(), GitFileSystemProvider.SCHEME));
		final String authority = gitUri.getAuthority();
		checkArgument(authority != null);
		final GitRepoFileSystem fs;
		switch (authority) {
		case FILE_AUTHORITY:
			final Path gitDir = getGitDir(gitUri);
			fs = cachedFileFileSystems.get(gitDir.toAbsolutePath());
			checkArgument(fs != null, "No file system at ‘" + gitDir + "’.");
			break;
		case DFS_AUTHORITY:
			final String name = getRepositoryName(gitUri);
			fs = cachedDfsFileSystems.get(name);
			checkArgument(fs != null, "No file system named ‘" + name + "’.");
			break;
		default:
			throw new VerifyException();
		}
		return fs;
	}

	public GitFileFileSystem getFileSystemFromGitDir(Path gitDir) {
		final Path absolutePath = gitDir.toAbsolutePath();
		checkArgument(cachedFileFileSystems.containsKey(absolutePath));
		return cachedFileFileSystems.get(absolutePath);
	}

	public GitDfsFileSystem getFileSystemFromName(String name) {
		checkArgument(cachedDfsFileSystems.containsKey(name));
		return cachedDfsFileSystems.get(name);
	}

	public URI toUri(GitRepoFileSystem gitFs) {
		if (gitFs instanceof GitFileFileSystem) {
			final GitFileFileSystem gitFileFs = (GitFileFileSystem) gitFs;
			final Path gitDir = gitFileFs.getGitDir();
			final String pathStr = gitDir.toAbsolutePath().toString();
			return URI_UNCHECKER.getUsing(() -> new URI(GitFileSystemProvider.SCHEME, FILE_AUTHORITY, pathStr, null));
		}

		if (gitFs instanceof GitDfsFileSystem) {
			final GitDfsFileSystem gitDfsFs = (GitDfsFileSystem) gitFs;
			final String name = getName(gitDfsFs.getRepository());
			verify(name != null);
			final String escaped = UrlEscapers.urlPathSegmentEscaper().escape(name);
			return URI_UNCHECKER
					.getUsing(() -> new URI(GitFileSystemProvider.SCHEME, DFS_AUTHORITY, "/" + escaped, null));
		}

		throw new IllegalArgumentException("Unknown repository type.");
	}

	private String getName(final DfsRepository dfsRepository) {
		return dfsRepository.getDescription().getRepositoryName();
	}

	public void put(Path gitDir, GitFileFileSystem newFs) {
		verifyCanCreateFileSystemCorrespondingTo(gitDir);
		cachedFileFileSystems.put(gitDir.toAbsolutePath(), newFs);
	}

	public void put(DfsRepository dfsRespository, GitDfsFileSystem newFs) {
		verifyCanCreateFileSystemCorrespondingTo(dfsRespository);
		cachedDfsFileSystems.put(getName(dfsRespository), newFs);
	}

	void hasBeenClosedEvent(GitRepoFileSystem gitFs) {
		if (gitFs instanceof GitFileFileSystem) {
			final GitFileFileSystem gitFileFs = (GitFileFileSystem) gitFs;
			final Path path = cachedFileFileSystems.inverse().remove(gitFileFs);
			checkArgument(path != null);
		}

		if (gitFs instanceof GitDfsFileSystem) {
			final GitDfsFileSystem gitDfsFs = (GitDfsFileSystem) gitFs;
			final String id = cachedDfsFileSystems.inverse().remove(gitDfsFs);
			checkArgument(id != null);
		}

		throw new IllegalArgumentException("Unknown repository type.");
	}
}
