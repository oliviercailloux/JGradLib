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

	private boolean update;

	private final Map<DoubleGitUri, GitFileSystem> cachedFileSystems = new LinkedHashMap<>();

	public GitFileSystemProvider() {
		update = true;
	}

	public boolean doesUpdate() {
		return update;
	}

	public void setUpdate(boolean update) {
		this.update = update;
	}

	@Override
	public String getScheme() {
		return SCHEME;
	}

	@Override
	public GitFileSystem newFileSystem(URI gitfsUri, Map<String, ?> env) throws IOException {
		final DoubleGitUri uris = DoubleGitUri.fromGitFsUri(gitfsUri);

		final Object gitFolderObj = env.get(GIT_FOLDER);
		final Path gitFolder;
		if (gitFolderObj instanceof Path) {
			gitFolder = (Path) gitFolderObj;
		} else if (gitFolderObj == null) {
			gitFolder = getGitFolderPathInTemp(uris);
		} else {
			throw new IllegalArgumentException("Unknown " + GIT_FOLDER);
		}

		return newFileSystem(uris, gitFolder);
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

	public GitFileSystem newFileSystem(DoubleGitUri uri) throws IOException {
		return newFileSystem(uri, getGitFolderPathInTemp(uri));
	}

	public GitFileSystem newFileSystem(DoubleGitUri uri, Path workTree) throws IOException {
		if (cachedFileSystems.containsKey(uri)) {
			throw new FileSystemAlreadyExistsException();
		}
		update(uri, workTree);
		final GitFileSystem newFs = new GitFileSystem(this, uri.getGitFsUri(), workTree);
		cachedFileSystems.put(uri, newFs);
		return newFs;
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
		final DoubleGitUri uris = DoubleGitUri.fromGitFsUri(gitFsUri);
		checkArgument(cachedFileSystems.containsKey(uris));
		return cachedFileSystems.get(uris);
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
	public GitPath getPath(URI uri) {
		final DoubleGitUri uris = DoubleGitUri.fromGitFsUri(uri);
		if (!cachedFileSystems.containsKey(uris)) {
			throw new FileSystemNotFoundException(uris.toString());
		}

		return GitPath.getMasterSlashPath(cachedFileSystems.get(uris));
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
