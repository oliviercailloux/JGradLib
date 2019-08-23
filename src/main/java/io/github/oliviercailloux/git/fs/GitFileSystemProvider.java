package io.github.oliviercailloux.git.fs;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.spi.FileSystemProvider;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeResult.MergeStatus;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;

import com.google.common.collect.MoreCollectors;

public class GitFileSystemProvider extends FileSystemProvider {

	public static final String GIT_FOLDER = "GIT_FOLDER";
	public static final String SCHEME = "gitfs";

	@Override
	public String getScheme() {
		return SCHEME;
	}

	@Override
	public GitFileSystem newFileSystem(URI gitfsUri, Map<String, ?> env) throws IOException {
		validateAndGetGitScheme(gitfsUri);

		final Object gitFolderObj = env.get(GIT_FOLDER);
		final Path gitFolder;
		if (gitFolderObj instanceof String) {
			final String gitFolderStr = (String) gitFolderObj;
			gitFolder = Path.of(gitFolderStr);
		} else if (gitFolderObj instanceof URI) {
			final URI gitFolderURI = (URI) gitFolderObj;
			gitFolder = Path.of(gitFolderURI);
		} else if (gitFolderObj instanceof Path) {
			gitFolder = (Path) gitFolderObj;
		} else if (gitFolderObj == null) {
//			final Path gitRepoPath = Path.of(gitfsUri.getPath());
//			final String gitRepoFullName = gitRepoPath.getName(gitRepoPath.getNameCount() - 1).toString();
//			final String gitRepoNameNoSlash;
//			if (gitRepoFullName.endsWith("/")) {
//				gitRepoNameNoSlash = gitRepoFullName.substring(0, gitRepoFullName.length() - 1);
//			} else {
//				gitRepoNameNoSlash = gitRepoFullName;
//			}
//			final String gitRepoName;
//			if (gitRepoNameNoSlash.endsWith(".git")) {
//				gitRepoName = gitRepoFullName.substring(0, gitRepoFullName.length() - 4);
//			} else {
//				gitRepoName = gitRepoFullName;
//			}
			String gitRepoName;
			try {
				gitRepoName = new URIish(gitfsUri.toString()).getHumanishName();
			} catch (URISyntaxException e) {
				throw new AssertionError(e);
			}
			gitFolder = Path.of("/tmp/" + gitRepoName + "/.git");
		} else {
			throw new IllegalArgumentException("Unknown " + GIT_FOLDER);
		}

		return newFileSystem(gitfsUri, gitFolder);
	}

	public GitFileSystem newFileSystem(DoubleGitUri uri, Path gitFolder) throws IOException {
		/**
		 * If file repo, and this path equals the repo path: nothing to check, it’s up
		 * to date.
		 */
		final boolean direct = uri.getGitScheme() == GitScheme.FILE
				&& gitFolder.toString().equals(uri.getRepositoryPath());
		if (direct) {
			checkArgument(Files.exists(gitFolder));
		}
		if (Files.exists(gitFolder) && !direct) {
			try (FileRepository repo = new FileRepository(gitFolder.toFile())) {
				try (Git git = Git.wrap(repo)) {
					final List<RemoteConfig> remoteList = git.remoteList().call();
					final Optional<RemoteConfig> origin = remoteList.stream()
							.filter((r) -> r.getName().equals("origin")).collect(MoreCollectors.toOptional());
					if (origin.isPresent() && origin.get().getURIs().size() == 1
							&& origin.get().getURIs().get(0).toString().equals(uri.getGitUri().toString())) {
						final PullResult result = git.pull().call();
						final MergeStatus mergeStatus = result.getMergeResult().getMergeStatus();
						final boolean rightState = result.isSuccessful()
								&& (mergeStatus == MergeStatus.ALREADY_UP_TO_DATE
										|| mergeStatus == MergeStatus.FAST_FORWARD);
						if (!rightState) {
							throw new IllegalStateException("Illegal pull result: " + result);
						}
					}
				} catch (GitAPIException e) {
					throw new IllegalStateException(e);
				}
			}
		}
	}

	@Override
	public FileSystem getFileSystem(URI uri) {
		TODO();
		return null;
	}

	@Override
	public Path getPath(URI uri) {
		TODO();
		return null;
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
		TODO();
	}

}
