package io.github.oliviercailloux.git.fs;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.File;
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
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.spi.FileSystemProvider;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.RemoteConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.MoreCollectors;

import io.github.oliviercailloux.utils.Utils;

public class GitFileSystemProvider extends FileSystemProvider {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GitFileSystemProvider.class);

	public static final String GIT_FOLDER = "GIT_FOLDER";
	public static final String SCHEME = "gitfs";

	private final Map<Path, GitFileSystem> cachedFileSystems = new LinkedHashMap<>();

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
		return newFileSystem(gitDir);
	}

	public GitFileSystem newFileSystem(Path gitDir) throws IOException {
		if (cachedFileSystems.containsKey(gitDir)) {
			throw new FileSystemAlreadyExistsException();
		}
		try (Repository repo = new FileRepositoryBuilder().setGitDir(gitDir.toFile()).build()) {
			checkArgument(repo.getObjectDatabase().exists());
		}
		final GitFileSystem newFs = new GitFileSystem(this, gitDir);
		cachedFileSystems.put(gitDir, newFs);
		return newFs;
	}

	private Path getGitFolderPathInTemp(DoubleGitUri uris) {
		final Path tmpDir = Utils.getTempDirectory();
		final String repositoryName = uris.getRepositoryName();
		checkArgument(!repositoryName.contains(FileSystems.getDefault().getSeparator()));
		final Path subFolder = tmpDir.resolve(repositoryName);
		/**
		 * This check is required because the separator we check against is only the
		 * default one, there could be others, which would allow to create something
		 * like /tmp/..-mypath, where the - designates this alternative separator.
		 */
		checkArgument(subFolder.getParent().equals(tmpDir));
		return subFolder;
	}

	private void update(DoubleGitUri uri, Path workTree) throws IOException {
		/**
		 * If file repo, and this path equals the repo path: nothing to check, it’s up
		 * to date.
		 */
		final boolean direct = uri.getGitScheme() == GitScheme.FILE
				&& workTree.toString().equals(uri.getRepositoryPath());
		final boolean exists = Files.exists(workTree);
		if (direct || !update) {
			checkArgument(exists);
		}
		if (!exists && !direct && update) {
			final CloneCommand cloneCmd = Git.cloneRepository();
			cloneCmd.setURI(uri.getGitString());
			final File dest = workTree.toFile();
			cloneCmd.setDirectory(dest);
			LOGGER.info("Cloning {} to {}.", uri, dest);
			try {
				cloneCmd.call().close();
			} catch (GitAPIException e) {
				throw new IOException(e);
			}
		}
		if (exists && !direct && update) {
			try (Repository repo = new FileRepositoryBuilder().setWorkTree(workTree.toFile()).build()) {
				try (Git git = Git.wrap(repo)) {
					final List<RemoteConfig> remoteList = git.remoteList().call();
					final Optional<RemoteConfig> origin = remoteList.stream()
							.filter((r) -> r.getName().equals("origin")).collect(MoreCollectors.toOptional());
					if (!git.status().call().isClean()) {
						throw new IllegalStateException("Can’t update: not clean.");
					}
					LOGGER.info("Full branch: {}.", repo.getFullBranch());
					if (origin.isPresent() && origin.get().getURIs().size() == 1
							&& origin.get().getURIs().get(0).toString().equals(uri.getGitString())) {
						final PullResult result = git.pull().call();
						if (!result.isSuccessful()) {
							LOGGER.error("Merge failed with results: {}, {}, {}.", result.getFetchResult(),
									result.getMergeResult(), result.getRebaseResult());
							throw new IllegalStateException("Merge failed");
						}
					} else {
						throw new IllegalStateException("Unexpected remote.");
					}
				} catch (GitAPIException e) {
					throw new IllegalStateException(e);
				}
			}
		}
	}

	@Override
	public GitFileSystem getFileSystem(URI gitFsUri) {
		final Path gitDir = getGitDir(gitFsUri);
		return getFileSystem(gitDir);
	}

	public GitFileSystem getFileSystem(Path gitDir) {
		checkArgument(cachedFileSystems.containsKey(gitDir));
		return cachedFileSystems.get(gitDir);
	}

	/**
	 * Following reasoning here: https://stackoverflow.com/a/16213815, I refuse to
	 * create a new file system transparently from this method. This would encourage
	 * the caller to forget closing the just created file system.
	 *
	 * A URI may be more complete and identify a commit (possibly master), directory
	 * and file. I have not thought about how a general approach to do this. Patches
	 * welcome.
	 */
	@Override
	public GitPath getPath(URI gitFsUri) {
		final Path gitDir = getGitDir(gitFsUri);
		return getPath(gitDir);
	}

	public GitPath getPath(Path gitDir) {
		if (!cachedFileSystems.containsKey(gitDir)) {
			throw new FileSystemNotFoundException();
		}

		return GitPath.getMasterSlashPath(cachedFileSystems.get(gitDir));
	}

	@Override
	public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs)
			throws IOException {
		TODO();
		return null;
	}

	@Override
	public DirectoryStream<Path> newDirectoryStream(Path dir, Filter<? super Path> filter) throws IOException {
		TODO();
		return null;
	}

	@Override
	public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
		TODO();

	}

	@Override
	public void delete(Path path) throws IOException {
		TODO();

	}

	@Override
	public void copy(Path source, Path target, CopyOption... options) throws IOException {
		TODO();

	}

	@Override
	public void move(Path source, Path target, CopyOption... options) throws IOException {
		TODO();

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
		TODO();

	}

	void hasBeenClosed(GitFileSystem fs) {
		cachedFileSystems.remove(DoubleGitUri.fromGitFsUri(fs.getGitFsUri()));
	}

}
