package io.github.oliviercailloux.git.fs;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Verify.verify;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchEvent.Modifier;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Comparator;
import java.util.Objects;

import org.eclipse.jgit.internal.storage.dfs.DfsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Verify;
import com.google.common.jimfs.Jimfs;

/**
 *
 * <p>
 * Has an optional root component, such as "master/", and a sequence of names
 * made of a linux-like path. The linux-like path represents the sequence of
 * names of this path. This path is absolute iff it has a root component.
 * </p>
 *
 * <p>
 * Whether the linux-like path is absolute plays no role. For clarity,
 * internally, the linux-like path is ensured to be always relative without root
 * component iff this path has no root component (no commit). Equivalently, the
 * linux-like path is absolute and with a root component iff this path has a
 * root component.
 * </p>
 *
 * <p>
 * The choice that this path’s root component equals its commit implies that
 * when this path has no root component (no commit), it can’t advertise a
 * linux-like absolute path: otherwise, such a path would have a root component,
 * but this object has none.
 * </p>
 *
 * <p>
 * The choice that when this path has a root component (a commit), it can’t
 * advertise a linux-like relative path, is for simplicity, and because
 * distinguishing a path with a linux-like relative path and one with a
 * linux-like absolute path in string form would be hard.
 * </p>
 * TODO distinguish Ref (such as master) and OId (a SHA-1).
 *
 * @author Olivier Cailloux
 *
 */
public class GitPath implements Path {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GitPath.class);

	private static final Comparator<GitPath> COMPARATOR = Comparator.<GitPath, String>comparing((p) -> p.revStr)
			.thenComparing((p) -> p.dirAndFile);

	private final GitRepoFileSystem fileSystem;

	/**
	 * Empty for no commit specified.
	 */
	private final String revStr;

	/**
	 * Linux style in-memory path, may be the empty path.
	 */
	private final Path dirAndFile;

	GitPath(GitRepoFileSystem fileSystem, String revStr, Path dirAndFile) {
		this.fileSystem = checkNotNull(fileSystem);
		this.revStr = checkNotNull(revStr);
		this.dirAndFile = checkNotNull(dirAndFile);
		checkArgument(dirAndFile.getFileSystem().provider().getScheme().equals(Jimfs.URI_SCHEME));
		checkArgument(!revStr.equals("") == dirAndFile.isAbsolute());
		/**
		 * TODO should check that in git, revStr may not contain / (assumption can be
		 * relaxed otherwise)
		 */
		checkArgument(!revStr.contains("/"));
		checkArgument(dirAndFile.isAbsolute() == (dirAndFile.getRoot() != null));
	}

	public String getRevStr() {
		Verify.verifyNotNull(revStr);
		return revStr;
	}

	@Override
	public GitRepoFileSystem getFileSystem() {
		return fileSystem;
	}

	@Override
	public boolean isAbsolute() {
		verify(dirAndFile.isAbsolute() == !revStr.isEmpty());
		return dirAndFile.isAbsolute();
	}

	@Override
	public GitPath getRoot() {
		if (revStr.equals("")) {
			return null;
		}
		if (dirAndFile.equals(GitRepoFileSystem.JIM_FS_SLASH)) {
			return this;
		}
		return new GitPath(fileSystem, revStr, GitRepoFileSystem.JIM_FS_SLASH);
	}

	@Override
	public int getNameCount() {
		return dirAndFile.getNameCount();
	}

	@Override
	public GitPath getName(int index) {
		final Path name = dirAndFile.getName(index);
		return new GitPath(fileSystem, "", name);
	}

	@Override
	public GitPath subpath(int beginIndex, int endIndex) {
		final Path subpath = dirAndFile.subpath(beginIndex, endIndex);
		return new GitPath(fileSystem, "", subpath);
	}

	@Override
	public GitPath getFileName() {
		final Path fileName = dirAndFile.getFileName();
		if (fileName == null) {
			return null;
		}
		return new GitPath(fileSystem, "", fileName);
	}

	@Override
	public GitPath getParent() {
		final Path parent = dirAndFile.getParent();
		if (parent == null) {
			return null;
		}
		return new GitPath(fileSystem, revStr, parent);
	}

	@Override
	public boolean startsWith(Path other) {
		if (!getFileSystem().equals(other.getFileSystem())) {
			return false;
		}
		final GitPath p2 = (GitPath) other;
		if (!revStr.startsWith(p2.revStr)) {
			return false;
		}
		return dirAndFile.startsWith(p2.dirAndFile);
	}

	@Override
	public boolean endsWith(Path other) {
		if (!getFileSystem().equals(other.getFileSystem())) {
			return false;
		}
		final GitPath p2 = (GitPath) other;
		if (!revStr.endsWith(p2.revStr)) {
			return false;
		}
		return dirAndFile.endsWith(p2.dirAndFile);
	}

	@Override
	public GitPath normalize() {
		return new GitPath(fileSystem, revStr, dirAndFile.normalize());
	}

	@Override
	public GitPath resolve(Path other) {
		if (!getFileSystem().equals(other.getFileSystem())) {
			throw new IllegalArgumentException();
		}
		if (other.isAbsolute()) {
			return (GitPath) other;
		}
		return resolveRelative(other);
	}

	public GitPath resolveRelative(Path other) {
		checkArgument(!other.isAbsolute());

		if (other.toString().equals("")) {
			return this;
		}
		if (!getFileSystem().equals(other.getFileSystem())) {
			throw new IllegalArgumentException();
		}
		final GitPath p2 = (GitPath) other;
		final String newRevStr;
		if (p2.revStr.equals("")) {
			newRevStr = revStr;
		} else {
			newRevStr = p2.revStr;
		}
		return new GitPath(fileSystem, newRevStr, dirAndFile.resolve(p2.dirAndFile));
	}

	@Override
	public GitPath relativize(Path other) {
		if (!getFileSystem().equals(other.getFileSystem())) {
			throw new IllegalArgumentException();
		}
		final GitPath p2 = (GitPath) other;
		if (revStr.equals(p2.revStr)) {
			return new GitPath(fileSystem, "", dirAndFile.relativize(p2.dirAndFile));
		}
		throw new IllegalArgumentException();
	}

	@SuppressWarnings("resource")
	@Override
	public URI toUri() {
		/**
		 * I do not use UriBuilder because it performs “contextual encoding of
		 * characters not permitted in the corresponding URI component following the
		 * rules of the application/x-www-form-urlencoded media type for query
		 * parameters”, and therefore encodes / to %2F.
		 */
		final StringBuilder queryBuilder = new StringBuilder();
		if (!revStr.isEmpty()) {
			assert !dirAndFile.toString().isEmpty();
			queryBuilder.append("revStr=" + revStr + "&");
		}
		if (!dirAndFile.toString().isEmpty()) {
			queryBuilder.append("dirAndFile=" + dirAndFile);
		}
		final String query = queryBuilder.toString();

		final URI uri;
		final GitRepoFileSystem fs = getFileSystem();
		if (fs instanceof GitDirFileSystem) {
			final GitDirFileSystem pathBasedFs = (GitDirFileSystem) fs;
			final Path gitDir = pathBasedFs.getGitDir();
			try {
				uri = new URI(GitFileSystemProvider.SCHEME, null, gitDir.toAbsolutePath().toString(), query, null);
			} catch (URISyntaxException e) {
				throw new IllegalStateException(e);
			}
		} else if (fs.getRepository() instanceof DfsRepository) {
			final DfsRepository repo = (DfsRepository) fs.getRepository();
			try {
				uri = new URI(GitFileSystemProvider.SCHEME, "mem", "/" + repo.getDescription().getRepositoryName(),
						query, null);
			} catch (URISyntaxException e) {
				throw new IllegalStateException(e);
			}
		} else {
			try {
				uri = new URI(GitFileSystemProvider.SCHEME, "mem", fs.getRepository().toString(), query, null);
			} catch (URISyntaxException e) {
				throw new IllegalStateException(e);
			}
		}

		return uri;
	}

	@Override
	public GitPath toAbsolutePath() {
		if (isAbsolute()) {
			return this;
		}
		return fileSystem.masterRoot.resolveRelative(this);
	}

	public GitPath toRelativePath() {
		if (revStr.isEmpty()) {
			return this;
		}
		assert dirAndFile.isAbsolute();
		return new GitPath(fileSystem, "", GitRepoFileSystem.JIM_FS_SLASH.relativize(dirAndFile));
	}

	@Override
	public Path toRealPath(LinkOption... options) throws IOException {
		throw new UnsupportedOperationException();
	}

	@Override
	public WatchKey register(WatchService watcher, Kind<?>[] events, Modifier... modifiers) throws IOException {
		throw new UnsupportedOperationException();
	}

	@Override
	public int compareTo(Path other) {
		if (!getFileSystem().equals(other.getFileSystem())) {
			throw new IllegalArgumentException();
		}
		final GitPath p2 = (GitPath) other;
		return COMPARATOR.compare(this, p2);
	}

	@Override
	public boolean equals(Object o2) {
		if (!(o2 instanceof GitPath)) {
			return false;
		}
		final GitPath p2 = (GitPath) o2;
		return revStr.equals(p2.revStr) && dirAndFile.equals(p2.dirAndFile);
	}

	@Override
	public int hashCode() {
		return Objects.hash(revStr, dirAndFile);
	}

	@Override
	public String toString() {
		final String maybeSlash = revStr.isEmpty() ? "" : "/";
		return revStr + maybeSlash + dirAndFile.toString();
	}

}
