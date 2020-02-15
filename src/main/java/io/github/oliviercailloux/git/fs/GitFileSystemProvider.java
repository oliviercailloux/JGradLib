package io.github.oliviercailloux.git.fs;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.IOException;
import java.net.URI;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.ReadOnlyFileSystemException;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.spi.FileSystemProvider;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

public class GitFileSystemProvider extends FileSystemProvider {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GitFileSystemProvider.class);

	public static final String GIT_FOLDER = "GIT_FOLDER";
	public static final String SCHEME = "gitfs";

	public static Path getGitDir(URI gitFsUri) {
		checkArgument(gitFsUri.isAbsolute());
		checkArgument(gitFsUri.getScheme().equalsIgnoreCase(SCHEME));
		checkArgument(!gitFsUri.isOpaque());
		checkArgument(gitFsUri.getAuthority() == null);
		checkArgument(gitFsUri.getQuery() == null);
		checkArgument(gitFsUri.getFragment() == null);

		final Path gitDir = Path.of(gitFsUri.getPath());
		return gitDir;
	}

	private final Map<Path, GitFileSystem> cachedFileSystems = new LinkedHashMap<>();

	public GitFileSystemProvider() {
		/** Default constructor. */
	}

	@Override
	public String getScheme() {
		return SCHEME;
	}

	@Override
	public GitFileSystem newFileSystem(URI gitFsUri, Map<String, ?> env) throws IOException {
		final Path gitDir = getGitDir(gitFsUri);
		return newFileSystemFromGitDir(gitDir);
	}

	@Override
	public FileSystem newFileSystem(Path path, Map<String, ?> env) throws IOException {
		throw new UnsupportedOperationException();
	}

	public GitFileSystem newFileSystemFromGitDir(Path gitDir) throws IOException {
		if (cachedFileSystems.containsKey(gitDir)) {
			throw new FileSystemAlreadyExistsException();
		}
		if (!Files.exists(gitDir)) {
			throw new IOException(String.format("Directory %s not found.", gitDir));
		}
		try (Repository repo = new FileRepositoryBuilder().setGitDir(gitDir.toFile()).build()) {
			if (!repo.getObjectDatabase().exists()) {
				throw new IOException(String.format("Object database not found in %s.", gitDir));
			}
		}
		final GitFileSystem newFs = GitFileSystem.given(this, gitDir);
		cachedFileSystems.put(gitDir, newFs);
		return newFs;
	}

	@Override
	public GitFileSystem getFileSystem(URI gitFsUri) {
		final Path gitDir = getGitDir(gitFsUri);
		return getFileSystemFromGitDir(gitDir);
	}

	public GitFileSystem getFileSystemFromGitDir(Path gitDir) {
		checkArgument(cachedFileSystems.containsKey(gitDir));
		return cachedFileSystems.get(gitDir);
	}

	/**
	 * Following reasoning here: https://stackoverflow.com/a/16213815, I refuse to
	 * create a new file system transparently from this method. This would encourage
	 * the caller to forget closing the just created file system.
	 *
	 * A URI may be more complete and identify a commit (possibly master), directory
	 * and file. I have not thought about a general approach to do this. Patches
	 * welcome.
	 */
	@Override
	public GitPath getPath(URI gitFsUri) {
		final Path gitDir = getGitDir(gitFsUri);
		return getPathFromGitDir(gitDir);
	}

	public GitPath getPathFromGitDir(Path gitDir) {
		if (!cachedFileSystems.containsKey(gitDir)) {
			throw new FileSystemNotFoundException();
		}

		return GitPath.getMasterSlashPath(cachedFileSystems.get(gitDir));
	}

	@Override
	public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs)
			throws IOException {
		checkArgument(path instanceof GitPath);
		if (attrs.length >= 1) {
			throw new ReadOnlyFileSystemException();
		}
		if (!Sets.difference(options, ImmutableSet.of(StandardOpenOption.READ)).isEmpty()) {
			LOGGER.error("Unknown options: " + Sets.difference(options, ImmutableSet.of(StandardOpenOption.READ)));
			throw new ReadOnlyFileSystemException();
		}

		final GitPath gitPath = (GitPath) path.toAbsolutePath();
		return gitPath.getFileSystem().newByteChannel(gitPath);
	}

	@Override
	public DirectoryStream<Path> newDirectoryStream(Path dir, Filter<? super Path> filter) throws IOException {
		TODO();
		return null;
	}

	@Override
	public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
		throw new ReadOnlyFileSystemException();
	}

	@Override
	public void delete(Path path) throws IOException {
		throw new ReadOnlyFileSystemException();
	}

	@Override
	public void copy(Path source, Path target, CopyOption... options) throws IOException {
		throw new ReadOnlyFileSystemException();
	}

	@Override
	public void move(Path source, Path target, CopyOption... options) throws IOException {
		throw new ReadOnlyFileSystemException();
	}

	@Override
	public boolean isSameFile(Path path, Path path2) throws IOException {
		TODO();
		return false;
	}

	@Override
	public boolean isHidden(Path path) throws IOException {
		TODO();
		return false;
	}

	@Override
	public FileStore getFileStore(Path path) throws IOException {
		TODO();
		return null;
	}

	@Override
	public void checkAccess(Path path, AccessMode... modes) throws IOException {
		TODO();

	}

	@Override
	public <V extends FileAttributeView> V getFileAttributeView(Path path, Class<V> type, LinkOption... options) {
		TODO();
		return null;
	}

	@Override
	public <A extends BasicFileAttributes> A readAttributes(Path path, Class<A> type, LinkOption... options)
			throws IOException {
		TODO();
		return null;
	}

	@Override
	public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
		TODO();
		return null;
	}

	@Override
	public void setAttribute(Path path, String attribute, Object value, LinkOption... options) throws IOException {
		throw new ReadOnlyFileSystemException();
	}

	void hasBeenClosedEvent(GitFileSystem fs) {
		cachedFileSystems.remove(fs.getGitDir());
	}

}
