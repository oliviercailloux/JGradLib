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
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;

public class GitFileSystemProvider extends FileSystemProvider {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GitFileSystemProvider.class);

	public static final String SCHEME = "gitjfs";
	/**
	 * It is crucial to always use the same instance of Jimfs, because Jimfs refuses
	 * to resolve paths coming from different instances.
	 */
	static final FileSystem JIM_FS = Jimfs.newFileSystem(Configuration.unix());

	static final Path JIM_FS_EMPTY = JIM_FS.getPath("");

	static final Path JIM_FS_SLASH = JIM_FS.getPath("/");

	private final GitFileSystems fses;

	/**
	 * Zero argument constructor to satisfy the standard Java service-provider
	 * loading mechanism.
	 */
	public GitFileSystemProvider() {
		fses = new GitFileSystems();
	}

	@Override
	public String getScheme() {
		return SCHEME;
	}

	GitFileSystems getGitFileSystems() {
		return fses;
	}

	@Override
	public GitFileFileSystem newFileSystem(URI gitFsUri, Map<String, ?> env) throws IOException {
		final Path gitDir = fses.getGitDir(gitFsUri);
		return newFileSystemFromGitDir(gitDir);
	}

	@Override
	public GitFileFileSystem newFileSystem(Path gitDir, Map<String, ?> env) throws IOException {
		return newFileSystemFromGitDir(gitDir);
	}

	@SuppressWarnings("resource")
	public GitFileFileSystem newFileSystemFromGitDir(Path gitDir) throws UnsupportedOperationException {
		fses.verifyCanCreateFileSystemCorrespondingTo(gitDir);

		if (!Files.exists(gitDir)) {
			/**
			 * Not clear whether the specs mandate UnsupportedOperationException here. I
			 * follow the observed behavior of ZipFileSystemProvider.
			 */
			throw new FileSystemNotFoundException(String.format("Directory %s not found.", gitDir));
		}
		final FileRepository repo;
		try {
			repo = (FileRepository) new FileRepositoryBuilder().setGitDir(gitDir.toFile()).build();
			if (!repo.getObjectDatabase().exists()) {
				try {
					repo.close();
				} catch (Exception e) {
					LOGGER.debug("Exception while closing underlying repository.", e);
					// suppress
				}
				throw new UnsupportedOperationException(String.format("Object database not found in %s.", gitDir));
			}
		} catch (IOException e) {
			throw new UnsupportedOperationException(e);
		}
		final GitFileFileSystem newFs = GitFileFileSystem.givenOurRepository(this, repo);
		fses.put(gitDir, newFs);
		return newFs;
	}

	public GitRepoFileSystem newFileSystemFromRepository(Repository repository) throws IOException {
		if (repository instanceof DfsRepository) {
			final DfsRepository dfs = (DfsRepository) repository;
			return newFileSystemFromDfsRepository(dfs);
		}
		if (repository instanceof FileRepository) {
			final FileRepository f = (FileRepository) repository;
			return newFileSystemFromFileRepository(f);
		}
		throw new IllegalArgumentException("Unknown repository");
	}

	public GitFileFileSystem newFileSystemFromFileRepository(FileRepository repository) throws IOException {
		final Path gitDir = repository.getDirectory().toPath();
		fses.verifyCanCreateFileSystemCorrespondingTo(gitDir);

		if (!Files.exists(gitDir)) {
			throw new IOException(String.format("Directory %s not found.", gitDir));
		}
		if (!repository.getObjectDatabase().exists()) {
			throw new IOException(String.format("Object database not found in %s.", gitDir));
		}
		final GitFileFileSystem newFs = GitFileFileSystem.givenUserRepository(this, repository);
		fses.put(gitDir, newFs);
		return newFs;
	}

	public GitDfsFileSystem newFileSystemFromDfsRepository(DfsRepository repository) throws IOException {
		fses.verifyCanCreateFileSystemCorrespondingTo(repository);

		if (!repository.getObjectDatabase().exists()) {
			throw new IOException(String.format("Object database not found."));
		}

		final GitDfsFileSystem newFs = GitDfsFileSystem.givenUserRepository(this, repository);
		fses.put(repository, newFs);
		return newFs;
	}

	@Override
	public GitRepoFileSystem getFileSystem(URI gitFsUri) {
		return fses.getFileSystem(gitFsUri);
	}

	public GitFileFileSystem getFileSystemFromGitDir(Path gitDir) {
		return fses.getFileSystemFromGitDir(gitDir);
	}

	public GitRepoFileSystem getFileSystemFromRepositoryName(String name) {
		return fses.getFileSystemFromName(name);
	}

	/**
	 * Following reasoning here: https://stackoverflow.com/a/16213815, I refuse to
	 * create a new file system transparently from this method. This would encourage
	 * the caller to forget closing the just created file system.
	 *
	 */
	@SuppressWarnings("resource")
	@Override
	public GitPath getPath(URI gitFsUri) {
		final GitRepoFileSystem fs = fses.getFileSystem(gitFsUri);
		return GitPath.fromQueryString(fs, QueryUtils.splitQuery(gitFsUri));
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

}
