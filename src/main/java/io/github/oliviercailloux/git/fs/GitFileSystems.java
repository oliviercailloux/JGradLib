package io.github.oliviercailloux.git.fs;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Verify.verify;
import static com.google.common.base.Verify.verifyNotNull;
import static io.github.oliviercailloux.jaris.exceptions.Unchecker.URI_UNCHECKER;

import com.google.common.base.VerifyException;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import java.net.URI;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Path;
import java.util.Objects;
import org.eclipse.jgit.internal.storage.dfs.DfsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class GitFileSystems {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GitFileSystems.class);

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

	public void verifyCanCreateFileSystemCorrespondingTo(Path gitDir) throws FileSystemAlreadyExistsException {
		if (cachedFileFileSystems.containsKey(gitDir.toAbsolutePath())) {
			throw new FileSystemAlreadyExistsException();
		}
	}

	public void verifyCanCreateFileSystemCorrespondingTo(DfsRepository dfsRepository)
			throws FileSystemAlreadyExistsException {
		final String name = getName(dfsRepository);
		if (cachedDfsFileSystems.containsKey(name)) {
			throw new FileSystemAlreadyExistsException("A repository with the name ‘" + name + "’ already exists.");
		}
	}

	String getExistingUniqueId(GitFileSystem gitFs) {
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
		checkArgument(Objects.equals(gitFileUri.getAuthority(), FILE_AUTHORITY),
				"Unexpected authority: " + gitFileUri.getAuthority() + ", expected " + FILE_AUTHORITY + ".");
		/**
		 * It follows from these two checks that the uri is absolute (it has a scheme)
		 * and hierarchical (it was further parsed).
		 */
		verify(gitFileUri.isAbsolute());
		verify(!gitFileUri.isOpaque());

		final String gitDirStr = gitFileUri.getPath();
		/** An hierarchical absolute URI has an absolute path. */
		verifyNotNull(gitDirStr);
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
	public GitFileSystem getFileSystem(URI gitUri) throws FileSystemNotFoundException {
		checkArgument(Objects.equals(gitUri.getScheme(), GitFileSystemProvider.SCHEME));
		final String authority = gitUri.getAuthority();
		checkArgument(authority != null);
		final GitFileSystem fs;
		switch (authority) {
		case FILE_AUTHORITY:
			final Path gitDir = getGitDir(gitUri);
			fs = getFileSystemFromGitDir(gitDir);
			break;
		case DFS_AUTHORITY:
			final String name = getRepositoryName(gitUri);
			fs = getFileSystemFromName(name);
			break;
		default:
			throw new VerifyException();
		}
		return fs;
	}

	public GitFileFileSystem getFileSystemFromGitDir(Path gitDir) throws FileSystemNotFoundException {
		final Path absolutePath = gitDir.toAbsolutePath();
		if (!cachedFileFileSystems.containsKey(absolutePath)) {
			throw new FileSystemNotFoundException("No system at " + gitDir + ".");
		}
		return cachedFileFileSystems.get(absolutePath);
	}

	public GitDfsFileSystem getFileSystemFromName(String name) throws FileSystemNotFoundException {
		if (!cachedDfsFileSystems.containsKey(name)) {
			throw new FileSystemNotFoundException("No system at " + name + ".");
		}
		return cachedDfsFileSystems.get(name);
	}

	public URI toUri(GitFileSystem gitFs) {
		if (gitFs instanceof GitFileFileSystem) {
			final GitFileFileSystem gitFileFs = (GitFileFileSystem) gitFs;
			final Path gitDir = gitFileFs.getGitDir();
			final String pathStr = gitDir.toAbsolutePath().toString();
			final String pathStrSlash = pathStr.endsWith("/") ? pathStr : pathStr + "/";
			return URI_UNCHECKER
					.getUsing(() -> new URI(GitFileSystemProvider.SCHEME, FILE_AUTHORITY, pathStrSlash, null));
		}

		if (gitFs instanceof GitDfsFileSystem) {
			final GitDfsFileSystem gitDfsFs = (GitDfsFileSystem) gitFs;
			final String name = getName(gitDfsFs.getRepository());
			verifyNotNull(name);
			/**
			 * I’d like not to have the possible / in name to reach the URI and act as
			 * segment separator in the URI path. But it might be that encoding it with %2F
			 * as usual will make it equivalent to a /, at least, the URI class acts so. So
			 * I’d have to encode / to something else using a home-grown encoding. Let’s not
			 * go that far. Also, it might be good anyway to not encode slashes and thus
			 * treat them as segment separators: if the user used slashes in the repository
			 * name, it might well be with the intent of separating segments.
			 */
			return URI_UNCHECKER.getUsing(() -> new URI(GitFileSystemProvider.SCHEME, DFS_AUTHORITY, "/" + name, null));
		}

		throw new IllegalArgumentException("Unknown repository type.");
	}

	private String getName(final DfsRepository dfsRepository) {
		return dfsRepository.getDescription().getRepositoryName();
	}

	public void put(Path gitDir, GitFileFileSystem newFs) {
		LOGGER.debug("Adding an entry at {}: {}.", gitDir, newFs);
		verifyCanCreateFileSystemCorrespondingTo(gitDir);
		cachedFileFileSystems.put(gitDir.toAbsolutePath(), newFs);
	}

	public void put(DfsRepository dfsRespository, GitDfsFileSystem newFs) {
		verifyCanCreateFileSystemCorrespondingTo(dfsRespository);
		cachedDfsFileSystems.put(getName(dfsRespository), newFs);
	}

	void hasBeenClosedEvent(GitFileSystem gitFs) {
		if (gitFs instanceof GitFileFileSystem) {
			final GitFileFileSystem gitFileFs = (GitFileFileSystem) gitFs;
			LOGGER.debug("Removing {} from {}.", gitFileFs, cachedFileFileSystems);
			final BiMap<GitFileFileSystem, Path> inverse = cachedFileFileSystems.inverse();
			final Path path = inverse.remove(gitFileFs);
			checkArgument(path != null, inverse.keySet().toString());
		} else if (gitFs instanceof GitDfsFileSystem) {
			final GitDfsFileSystem gitDfsFs = (GitDfsFileSystem) gitFs;
			final String id = cachedDfsFileSystems.inverse().remove(gitDfsFs);
			checkArgument(id != null);
		} else {
			throw new IllegalArgumentException("Unknown repository type.");
		}
	}
}
