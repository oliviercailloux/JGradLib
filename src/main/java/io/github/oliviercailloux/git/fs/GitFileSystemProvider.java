package io.github.oliviercailloux.git.fs;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Verify.verify;

import java.io.IOException;
import java.net.URI;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.FileStore;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
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
import java.util.Objects;
import java.util.Set;

import org.eclipse.jgit.internal.storage.dfs.DfsRepository;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterators;
import com.google.common.collect.Sets;

import io.github.oliviercailloux.git.fs.GitPath.GitObject;

/**
 * A (partial) implementation of {@link FileSystemProvider}, able to produce
 * instances of {@link GitFileSystem}.
 *
 * @see #getInstance()
 * @see #newFileSystemFromGitDir(Path)
 * @see #newFileSystemFromDfsRepository(DfsRepository)
 */
public class GitFileSystemProvider extends FileSystemProvider {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GitFileSystemProvider.class);

	static final String SCHEME = "gitjfs";
	private static GitFileSystemProvider instance;

	/**
	 * Obtains the same instance as the one reachable through the
	 * {@link FileSystemProvider#installedProviders() installed providers}. Calling
	 * this method causes the default provider to be initialized (if not already
	 * initialized) and loads any other installed providers as described by the
	 * FileSystems class.
	 *
	 * @return the instance of this class registered for this scheme.
	 */
	public static GitFileSystemProvider getInstance() {
		if (instance == null) {
			FileSystemProvider.installedProviders();
			verify(instance != null);
		}
		return instance;
	}

	private final GitFileSystems fses;

	/**
	 * Zero argument constructor to satisfy the standard Java service-provider
	 * loading mechanism.
	 *
	 * @deprecated It is highly recommended to use {@link #getInstance()} instead of
	 *             this constructor. This constructor creates a new instance of the
	 *             provider, which might not be the one used automatically through
	 *             the installed providers mechanism, thereby possibly leading to
	 *             two provider instances running for the same scheme, using two
	 *             distinct caches for git file systems. If this is desired, please
	 *             open an issue and describe your use case. Access through this
	 *             constructor may be denied in future versions of this library.
	 * @see #getInstance()
	 */
	@Deprecated
	public GitFileSystemProvider() {
		fses = new GitFileSystems();
		if (instance != null) {
			LOGGER.warn("Already one instance, please instanciate only once.");
		} else {
			instance = this;
		}
	}

	@Override
	public String getScheme() {
		return SCHEME;
	}

	GitFileSystems getGitFileSystems() {
		return fses;
	}

	/**
	 * <p>
	 * Returns a new git file system reading from a git directory.
	 * </p>
	 * <p>
	 * The given URI must be a gitjfs URI referring to a git directory that no open
	 * git file system reads from. That is to say that any git file system reading
	 * from that directory and that have been created by this provider must be
	 * closed.
	 * </p>
	 * <p>
	 * Such an URI can have been obtained by a call to
	 * {@link GitFileFileSystem#toUri()}, or by a call to {@link GitPath#toUri()} on
	 * an instance of a git path associated with a {@link GitFileFileSystem}
	 * instance. In both cases, under condition of having used the same version of
	 * this library.
	 * </p>
	 * <p>
	 * Thus, an URI obtained during a previous VM execution may be used as well, but
	 * an URI obtained using a given version of this library is in general not
	 * guaranteed to be useable in another version. Please open an issue and
	 * describe your use case if this raises a problem.
	 * </p>
	 *
	 * @param gitFsUri a gitjfs URI referring to a git directory.
	 * @return a new git file system, reading from the git directory that the URI
	 *         refers to.
	 * @throws FileSystemAlreadyExistsException if a git file system is registered
	 *                                          already for that path (in which
	 *                                          case, use
	 *                                          {@link #getFileSystemFromGitDir(Path)}).
	 * @throws UnsupportedOperationException    if the path exists but does not seem
	 *                                          to correspond to a git directory.
	 * @throws FileSystemNotFoundException      if the path does not exist.
	 * @throws IOException                      if an exception occurred during
	 *                                          access to the underlying file
	 *                                          system.
	 */
	public GitFileFileSystem newFileSystem(URI gitFsUri) throws FileSystemAlreadyExistsException,
			UnsupportedOperationException, FileSystemNotFoundException, IOException {
		final Path gitDir = fses.getGitDir(gitFsUri);
		return newFileSystemFromGitDir(gitDir);
	}

	/**
	 * Behaves, currently, as if {@link #newFileSystem(URI)} had been called.
	 *
	 * @deprecated This method is there to satisfy the {@link FileSystemProvider}
	 *             contract. Because the {@code env} parameter is currently not
	 *             used, it is clearer, and more future-proof, to use
	 *             {@link #newFileSystem(URI)}.
	 */
	@Deprecated
	@Override
	public GitFileFileSystem newFileSystem(URI gitFsUri, Map<String, ?> env) throws FileSystemAlreadyExistsException,
			UnsupportedOperationException, FileSystemNotFoundException, IOException {
		return newFileSystem(gitFsUri);
	}

	/**
	 * Behaves, currently, as if {@link #newFileSystemFromGitDir(Path)} had been
	 * called.
	 *
	 * @deprecated This method is there to satisfy the {@link FileSystemProvider}
	 *             contract. Because the {@code env} parameter is currently not
	 *             used, it is clearer, and more future-proof, to use
	 *             {@link #newFileSystemFromGitDir(Path)}.
	 */
	@Deprecated
	@Override
	public GitFileFileSystem newFileSystem(Path gitDir, Map<String, ?> env) throws FileSystemAlreadyExistsException,
			UnsupportedOperationException, FileSystemNotFoundException, IOException {
		return newFileSystemFromGitDir(gitDir);
	}

	/**
	 * <p>
	 * Returns a new git file system reading from the given directory.
	 * </p>
	 * <p>
	 * The given directory is the place where git stores the data. It is typically
	 * named “.git”, but this is not mandatory.
	 * </p>
	 * <p>
	 * In the current version of this library, it must be associated with the
	 * {@link FileSystems#getDefault() default} file system because of
	 * <a href="https://www.eclipse.org/forums/index.php/m/1828091/">limitations</a>
	 * of JGit (please vote
	 * <a href="https://bugs.eclipse.org/bugs/show_bug.cgi?id=526500">here</a> to
	 * voice your concern: the bug will already be <a href=
	 * "https://bugs.eclipse.org/bugs/buglist.cgi?component=JGit&resolution=---&order=votes%20DESC">quite
	 * visible</a> if we reach 5 votes).
	 * </p>
	 *
	 * @param gitDir the directory to read data from.
	 * @return a new git file system.
	 * @throws FileSystemAlreadyExistsException if a git file system is registered
	 *                                          already for that path (in which
	 *                                          case, use
	 *                                          {@link #getFileSystemFromGitDir(Path)}).
	 * @throws UnsupportedOperationException    if the path exists but does not seem
	 *                                          to correspond to a git directory.
	 * @throws FileSystemNotFoundException      if the path does not exist.
	 * @throws IOException                      if an exception occurred during
	 *                                          access to the underlying file
	 *                                          system.
	 */
	@SuppressWarnings("resource")
	public GitFileFileSystem newFileSystemFromGitDir(Path gitDir) throws FileSystemAlreadyExistsException,
			UnsupportedOperationException, FileSystemNotFoundException, IOException {
		/**
		 * Implementation note: this method also throws UnsupportedOperationException if
		 * the path exists but is not associated with the default file system. But this
		 * is not part of the public contract.
		 */

		fses.verifyCanCreateFileSystemCorrespondingTo(gitDir);

		if (!Files.exists(gitDir)) {
			/**
			 * Not clear whether the specs mandate UnsupportedOperationException here. I
			 * follow the observed behavior of ZipFileSystemProvider.
			 */
			throw new FileSystemNotFoundException(String.format("Directory %s not found.", gitDir));
		}
		final FileRepository repo = (FileRepository) new FileRepositoryBuilder().setGitDir(gitDir.toFile()).build();
		try {
			if (!repo.getObjectDatabase().exists()) {
				throw new UnsupportedOperationException(String.format("Object database not found in %s.", gitDir));
			}
			final GitFileFileSystem newFs = GitFileFileSystem.givenOurRepository(this, repo);
			fses.put(gitDir, newFs);
			return newFs;
		} catch (Exception e) {
			try {
				repo.close();
			} catch (Exception closing) {
				LOGGER.debug("Exception while closing underlying repository.", closing);
				// suppress
			}
			throw e;
		}

	}

	/**
	 * <p>
	 * Returns a new git file system reading data from the provided JGit repository
	 * object.
	 * </p>
	 * <p>
	 * Because the repository is provided by the caller, it is the caller’s
	 * responsibility to close it when not needed any more: closing the returned
	 * file system will not close the underlying repository. Thus, when done with
	 * the given repository, it is necessary to close both the returned file system
	 * and the provided repository.
	 * </p>
	 *
	 * @param repository the repository to read data from.
	 * @return a new git file system.
	 * @throws FileSystemAlreadyExistsException if a git file system is registered
	 *                                          already for that repository or for
	 *                                          the path this repository reads from.
	 * @throws UnsupportedOperationException    if the repository contains no git
	 *                                          data.
	 * @throws IOException                      if an exception occurred during
	 *                                          access to the underlying data.
	 */
	public GitFileSystem newFileSystemFromRepository(Repository repository)
			throws FileSystemAlreadyExistsException, UnsupportedOperationException, IOException {
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

	/**
	 * <p>
	 * Returns a new git file system reading data from the provided JGit repository
	 * object.
	 * </p>
	 * <p>
	 * Because the repository is provided by the caller, it is the caller’s
	 * responsibility to close it when not needed any more: closing the returned
	 * file system will not close the underlying repository. Thus, when done with
	 * the given repository, it is necessary to close both the returned file system
	 * and the provided repository.
	 * </p>
	 *
	 * @param repository the repository to read data from.
	 * @return a new git file system.
	 * @throws FileSystemAlreadyExistsException if a git file system is registered
	 *                                          already for that repository or for
	 *                                          the path this repository reads from.
	 * @throws UnsupportedOperationException    if the repository contains no git
	 *                                          data.
	 * @throws IOException                      if an exception occurred during
	 *                                          access to the underlying data.
	 */
	@SuppressWarnings("unused")
	public GitFileFileSystem newFileSystemFromFileRepository(FileRepository repository)
			throws FileSystemAlreadyExistsException, UnsupportedOperationException, IOException {
		final Path gitDir = repository.getDirectory().toPath();
		fses.verifyCanCreateFileSystemCorrespondingTo(gitDir);

		if (!repository.getObjectDatabase().exists()) {
			throw new UnsupportedOperationException(String.format("Object database not found in %s.", gitDir));
		}
		final GitFileFileSystem newFs = GitFileFileSystem.givenUserRepository(this, repository);
		fses.put(gitDir, newFs);
		return newFs;
	}

	/**
	 * <p>
	 * Returns a new git file system reading data from the provided JGit repository
	 * object.
	 * </p>
	 * <p>
	 * Because the repository is provided by the caller, it is the caller’s
	 * responsibility to close it when not needed any more: closing the returned
	 * file system will not close the underlying repository. Thus, when done with
	 * the given repository, it is necessary to close both the returned file system
	 * and the provided repository.
	 * </p>
	 *
	 * @param repository the repository to read data from.
	 * @return a new git file system.
	 * @throws FileSystemAlreadyExistsException if a git file system is registered
	 *                                          already for that repository.
	 * @throws UnsupportedOperationException    if the repository contains no git
	 *                                          data.
	 * @throws IOException                      if an exception occurred during
	 *                                          access to the underlying data.
	 */
	@SuppressWarnings("unused")
	public GitDfsFileSystem newFileSystemFromDfsRepository(DfsRepository repository)
			throws FileSystemAlreadyExistsException, UnsupportedOperationException, IOException {
		fses.verifyCanCreateFileSystemCorrespondingTo(repository);

		if (!repository.getObjectDatabase().exists()) {
			throw new UnsupportedOperationException(String.format("Object database not found."));
		}

		final GitDfsFileSystem newFs = GitDfsFileSystem.givenUserRepository(this, repository);
		fses.put(repository, newFs);
		return newFs;
	}

	/**
	 * <p>
	 * Returns an already existing git file system created previously by this
	 * provider.
	 * </p>
	 * <p>
	 * The given URI must have been returned by a call to
	 * {@link GitFileSystem#toUri()} on a git file system instance created by this
	 * provider and that is still open; or by {@link GitPath#toUri()} on a git path
	 * instance associated to a git file system created by this provider and that is
	 * still open.
	 * </p>
	 * <p>
	 * (The wording of the contract for
	 * {@link FileSystemProvider#getFileSystem(URI)} suggests that this method
	 * should return only those file systems that have been created by an explicit
	 * invocation of {@link #newFileSystem(URI, Map)}, and no other method, which
	 * contradict the present implementation. But this restriction does not seem
	 * justified, and the OpenJDK implementation of the default provider does not
	 * satisfy it, so I take it to be an imprecise wording.)
	 * </p>
	 *
	 * @param gitFsUri the uri as returned by {@link GitFileSystem#toUri()} or
	 *                 {@link GitPath#toUri()}.
	 * @return an already existing, open git file system.
	 * @throws FileSystemNotFoundException if no corresponding file system is found.
	 */
	@Override
	public GitFileSystem getFileSystem(URI gitFsUri) throws FileSystemNotFoundException {
		return fses.getFileSystem(gitFsUri);
	}

	/**
	 * <p>
	 * Returns an already existing git file system created previously by this
	 * provider.
	 * </p>
	 * <p>
	 * There must be an open git file system whose
	 * {@link GitFileFileSystem#getGitDir()} method returns a path with the same
	 * absolute path as {@code gitDir}. In other cases, no guarantee is given.
	 * </p>
	 *
	 * @param gitDir the git directory as returned by
	 *               {@link GitFileFileSystem#getGitDir()}.
	 * @return an already existing, open git file system.
	 * @throws FileSystemNotFoundException if no corresponding file system is found.
	 */
	public GitFileFileSystem getFileSystemFromGitDir(Path gitDir) throws FileSystemNotFoundException {
		return fses.getFileSystemFromGitDir(gitDir);
	}

	/**
	 * <p>
	 * Returns an already existing git file system created previously by this
	 * provider.
	 * </p>
	 * <p>
	 * There must be an open git {@link GitDfsFileSystem} file system resting on a
	 * {@link DfsRepository} whose {@link DfsRepository#getDescription()
	 * description} contains the given name.
	 * </p>
	 *
	 * @param name the name of the repository of the git file system to retrieve.
	 * @return an already existing, open git file system.
	 * @throws FileSystemNotFoundException if no corresponding file system is found.
	 */
	public GitDfsFileSystem getFileSystemFromRepositoryName(String name) throws FileSystemNotFoundException {
		return fses.getFileSystemFromName(name);
	}

	/**
	 * <p>
	 * Returns a {@code Path} object by converting the given {@link URI}. The given
	 * uri must have been returned by {@link GitPath#toUri()} on a path associated
	 * to an open git file system created by this provider, or directly by
	 * {@link GitFileSystem#toUri()} on an open git file system created by this
	 * provider.
	 * </p>
	 * <p>
	 * This method does not access the underlying file system and requires no
	 * specific permission.
	 * </p>
	 * <p>
	 * This method does not create a new file system transparently, as this would
	 * encourage the caller to forget closing the just created file system (see also
	 * <a href="https://stackoverflow.com/a/16213815">this</a> discussion).
	 * </p>
	 *
	 * @param gitFsUri The URI to convert
	 *
	 * @return The resulting {@code Path}
	 *
	 * @throws IllegalArgumentException    If the given URI has not been issued by
	 *                                     {@link GitFileSystem#toUri()} or
	 *                                     {@link GitPath#toUri()}.
	 * @throws FileSystemNotFoundException If the file system, indirectly identified
	 *                                     by the URI, is not open or has not been
	 *                                     created by this provider.
	 */
	@Override
	@SuppressWarnings("resource")
	public GitPath getPath(URI gitFsUri) {
		final GitFileSystem fs = fses.getFileSystem(gitFsUri);
		return GitPath.fromQueryString(fs, QueryUtils.splitQuery(gitFsUri));
	}

	@Override
	public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs)
			throws IOException {
		checkArgument(path instanceof GitPath);
		if (attrs.length >= 1) {
			throw new ReadOnlyFileSystemException();
		}
		final ImmutableSet<? extends OpenOption> unsupportedOptions = Sets
				.difference(options, ImmutableSet.of(StandardOpenOption.READ, StandardOpenOption.SYNC)).immutableCopy();
		if (!unsupportedOptions.isEmpty()) {
			LOGGER.error("Unknown options: {}.", unsupportedOptions);
			throw new ReadOnlyFileSystemException();
		}

		final GitPath gitPath = (GitPath) path;
		return gitPath.toAbsolutePathAsAbsolutePath().newByteChannel(true);
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * The elements returned by the directory stream's
	 * {@link DirectoryStream#iterator iterator} are of type {@code
	 * GitPath}.
	 *
	 * @throws IllegalArgumentException if {@code dir} cannot be cast to
	 *                                  {@link GitPath}
	 */
	@SuppressWarnings("resource")
	@Override
	public DirectoryStream<Path> newDirectoryStream(Path dir, Filter<? super Path> filter) throws IOException {
		checkArgument(dir instanceof GitPath);
		final GitPath gitPath = (GitPath) dir;
		final DirectoryStream<GitPath> newDirectoryStream = gitPath.newDirectoryStream(filter);
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

	/**
	 * Throws {@code ReadOnlyFileSystemException}.
	 */
	@Override
	public void createDirectory(Path dir, FileAttribute<?>... attrs) throws ReadOnlyFileSystemException {
		throw new ReadOnlyFileSystemException();
	}

	/**
	 * Throws {@code ReadOnlyFileSystemException}.
	 */
	@Override
	public void delete(Path path) throws ReadOnlyFileSystemException {
		throw new ReadOnlyFileSystemException();
	}

	/**
	 * Throws {@code ReadOnlyFileSystemException}.
	 */
	@Override
	public void copy(Path source, Path target, CopyOption... options) throws ReadOnlyFileSystemException {
		throw new ReadOnlyFileSystemException();
	}

	/**
	 * Throws {@code ReadOnlyFileSystemException}.
	 */
	@Override
	public void move(Path source, Path target, CopyOption... options) throws ReadOnlyFileSystemException {
		throw new ReadOnlyFileSystemException();
	}

	/**
	 * At the moment, throws {@code UnsupportedOperationException}.
	 */
	@Override
	public boolean isSameFile(Path path, Path path2) throws IOException {
		throw new UnsupportedOperationException();
	}

	/**
	 * At the moment, throws {@code UnsupportedOperationException}.
	 */
	@Override
	public boolean isHidden(Path path) throws IOException {
		throw new UnsupportedOperationException();
	}

	/**
	 * At the moment, throws {@code UnsupportedOperationException}.
	 */
	@Override
	public FileStore getFileStore(Path path) throws IOException {
		throw new UnsupportedOperationException();
	}

	/**
	 * @throws ReadOnlyFileSystemException if {@code modes} contain {@code WRITE}.
	 */
	@Override
	public void checkAccess(Path path, AccessMode... modes)
			throws ReadOnlyFileSystemException, AccessDeniedException, NoSuchFileException, IOException {
		checkArgument(path instanceof GitPath);
		final GitPath gitPath = (GitPath) path;

		final ImmutableSet<AccessMode> modesList = ImmutableSet.copyOf(modes);
		if (modesList.contains(AccessMode.WRITE)) {
			throw new ReadOnlyFileSystemException();
		}
		if (!Sets.difference(modesList, ImmutableSet.of(AccessMode.READ, AccessMode.EXECUTE)).isEmpty()) {
			throw new UnsupportedOperationException();
		}

		final GitObject gitObject = gitPath.toAbsolutePathAsAbsolutePath().getGitObject(true);

		if (modesList.contains(AccessMode.EXECUTE)) {
			if (!Objects.equals(gitObject.getFileMode(), FileMode.EXECUTABLE_FILE)) {
				throw new AccessDeniedException(gitPath.toString());
			}
		}
	}

	/**
	 * At the moment, throws {@code UnsupportedOperationException}.
	 */
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

		return (A) gitPath.toAbsolutePathAsAbsolutePath().readAttributes(optionsSet);
	}

	/**
	 * At the moment, throws {@code UnsupportedOperationException}.
	 */
	@Override
	public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
		throw new UnsupportedOperationException();
	}

	/**
	 * Throws {@code ReadOnlyFileSystemException}.
	 */
	@Override
	public void setAttribute(Path path, String attribute, Object value, LinkOption... options)
			throws ReadOnlyFileSystemException {
		throw new ReadOnlyFileSystemException();
	}

}
