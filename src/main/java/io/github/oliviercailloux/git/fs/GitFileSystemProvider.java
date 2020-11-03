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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.internal.storage.dfs.DfsRepository;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterators;
import com.google.common.collect.Sets;

public class GitFileSystemProvider extends FileSystemProvider {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GitFileSystemProvider.class);

	public static final String SCHEME = "gitjfs";

	private static Path getGitDir(URI gitFsUri) {
		checkArgument(gitFsUri.isAbsolute());
		checkArgument(gitFsUri.getScheme().equalsIgnoreCase(SCHEME));
		checkArgument(!gitFsUri.isOpaque());
		checkArgument(gitFsUri.getAuthority() == null);
		checkArgument(gitFsUri.getQuery() == null);
		checkArgument(gitFsUri.getFragment() == null);

		final Path gitDir = Path.of(gitFsUri.getPath());
		return gitDir;
	}

	private final Map<Path, GitDirFileSystem> cachedFileSystems = new LinkedHashMap<>();

	private final Map<String, GitRepoFileSystem> cachedRepoFileSystems = new LinkedHashMap<>();

	/**
	 * Zero argument constructor to satisfy the standard Java service-provider
	 * loading mechanism.
	 */
	public GitFileSystemProvider() {
	}

	@Override
	public String getScheme() {
		return SCHEME;
	}

	@Override
	public GitDirFileSystem newFileSystem(URI gitFsUri, Map<String, ?> env) throws IOException {
		final Path gitDir = getGitDir(gitFsUri);
		return newFileSystemFromGitDir(gitDir);
	}

	@Override
	public GitDirFileSystem newFileSystem(Path gitDir, Map<String, ?> env) throws IOException {
		return newFileSystemFromGitDir(gitDir);
	}

	public GitDirFileSystem newFileSystemFromGitDir(Path gitDir) throws UnsupportedOperationException, IOException {
		if (cachedFileSystems.containsKey(gitDir)) {
			throw new FileSystemAlreadyExistsException();
		}
		if (!Files.exists(gitDir)) {
			/**
			 * Not clear whether the specs mandate UnsupportedOperationException here, but I
			 * rather follow the observed behavior of ZipFileSystemProvider.
			 */
			throw new FileSystemNotFoundException(String.format("Directory %s not found.", gitDir));
		}
		try (Repository repo = new FileRepositoryBuilder().setGitDir(gitDir.toFile()).build()) {
			if (!repo.getObjectDatabase().exists()) {
				throw new UnsupportedOperationException(String.format("Object database not found in %s.", gitDir));
			}
		} catch (IOException e) {
			throw new UnsupportedOperationException(e);
		}
		final GitDirFileSystem newFs = GitDirFileSystem.given(this, gitDir);
		cachedFileSystems.put(gitDir, newFs);
		return newFs;
	}

	public GitRepoFileSystem newFileSystemFromRepository(Repository repository) throws IOException {
		if (repository instanceof DfsRepository) {
			final DfsRepository dfs = (DfsRepository) repository;
			return newFileSystemFromDfsRepository(dfs);
		}
		if (repository instanceof FileRepository) {
			final FileRepository f = (FileRepository) repository;
			final Path gitDir = f.getDirectory().toPath();
			if (cachedFileSystems.containsKey(gitDir)) {
				throw new FileSystemAlreadyExistsException();
			}
			if (!Files.exists(gitDir)) {
				throw new IOException(String.format("Directory %s not found.", gitDir));
			}
			if (!repository.getObjectDatabase().exists()) {
				throw new IOException(String.format("Object database not found in %s.", gitDir));
			}
			final GitDirFileSystem newFs = GitDirFileSystem.given(this, gitDir);
			cachedFileSystems.put(gitDir, newFs);
			return newFs;
		}
		throw new IllegalArgumentException("Unknown repository");
	}

	public GitRepoFileSystem newFileSystemFromDfsRepository(DfsRepository repository) throws IOException {
		if (cachedRepoFileSystems.containsKey(repository.getDescription().getRepositoryName())) {
			throw new FileSystemAlreadyExistsException();
		}
		if (!repository.getObjectDatabase().exists()) {
			throw new IOException(String.format("Object database not found."));
		}
		final GitRepoFileSystem newFs = GitRepoFileSystem.given(this, repository);
		cachedRepoFileSystems.put(repository.getDescription().getRepositoryName(), newFs);
		return newFs;
	}

	@Override
	public GitDirFileSystem getFileSystem(URI gitFsUri) {
		/** TODO this could contain a repo name. */
		final Path gitDir = getGitDir(gitFsUri);
		return getFileSystemFromGitDir(gitDir);
	}

	public GitDirFileSystem getFileSystemFromGitDir(Path gitDir) {
		checkArgument(cachedFileSystems.containsKey(gitDir));
		return cachedFileSystems.get(gitDir);
	}

	public GitRepoFileSystem getFileSystemFromRepositoryName(String name) {
		checkArgument(cachedRepoFileSystems.containsKey(name));
		return cachedRepoFileSystems.get(name);
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

		return cachedFileSystems.get(gitDir).root;
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

		final GitPath gitPath = (GitPath) path;
		return gitPath.getFileSystem().newByteChannel(gitPath);
	}

	@SuppressWarnings("resource")
	@Override
	public DirectoryStream<Path> newDirectoryStream(Path dir, Filter<? super Path> filter) throws IOException {
		checkArgument(dir instanceof GitPath);
		final GitPath gitPath = (GitPath) dir;
		final DirectoryStream<GitPath> newDirectoryStream = gitPath.getFileSystem().newDirectoryStream(gitPath, filter);
		return new DirectoryStream<>() {

			@Override
			public void close() throws IOException {
				newDirectoryStream.close();
			}

			@Override
			public Iterator<Path> iterator() {
				return Iterators.transform(newDirectoryStream.iterator(), (p) -> p);
			}
		};
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
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean isHidden(Path path) throws IOException {
		throw new UnsupportedOperationException();
	}

	@Override
	public FileStore getFileStore(Path path) throws IOException {
		throw new UnsupportedOperationException();
	}

	@Override
	public void checkAccess(Path path, AccessMode... modes) throws IOException {
		checkArgument(path instanceof GitPath);
		final GitPath gitPath = (GitPath) path;
		gitPath.getFileSystem().checkAccess(gitPath, modes);
	}

	@Override
	public <V extends FileAttributeView> V getFileAttributeView(Path path, Class<V> type, LinkOption... options) {
		throw new UnsupportedOperationException();
	}

	@SuppressWarnings("unchecked")
	@Override
	public <A extends BasicFileAttributes> A readAttributes(Path path, Class<A> type, LinkOption... options)
			throws IOException {
		checkArgument(path instanceof GitPath);
		final GitPath gitPath = (GitPath) path;

		if (!type.equals(BasicFileAttributes.class)) {
			throw new UnsupportedOperationException();
		}

		final ImmutableSet<LinkOption> optionsSet = ImmutableSet.copyOf(options);

		return (A) gitPath.getFileSystem().readAttributes(gitPath, optionsSet);
	}

	@Override
	public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setAttribute(Path path, String attribute, Object value, LinkOption... options) throws IOException {
		throw new ReadOnlyFileSystemException();
	}

	void hasBeenClosedEvent(GitDirFileSystem fs) {
		cachedFileSystems.remove(fs.getGitDir());
	}

}
