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

import javax.ws.rs.core.UriBuilder;

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
 *
 * @author Olivier Cailloux
 *
 */
public class GitPath implements Path {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GitPath.class);

	private static final Comparator<GitPath> COMPARATOR = Comparator.<GitPath, String>comparing((p) -> p.revStr)
			.thenComparing((p) -> p.dirAndFile);

	static GitPath getMasterSlashPath(GitFileSystem gitFileSystem) {
		/**
		 * TODO make JIM_FS_SLASH non static, one per file system. Move this method to
		 * file system.
		 */
		return new GitPath(gitFileSystem, "master", GitFileSystem.JIM_FS_SLASH);
	}

	private GitFileSystem fileSystem;

	/**
	 * Empty for no commit specified.
	 */
	private String revStr;

	/**
	 * Linux style in-memory path, may be the empty path.
	 */
	private Path dirAndFile;

	GitPath(GitFileSystem fileSystem, String revStr, Path dirAndFile) {
		this.fileSystem = checkNotNull(fileSystem);
		this.revStr = checkNotNull(revStr);
		this.dirAndFile = checkNotNull(dirAndFile);
		checkArgument(dirAndFile.getFileSystem().provider().getScheme().equals(Jimfs.URI_SCHEME));
		checkArgument(!revStr.equals("") == dirAndFile.isAbsolute());
		checkArgument(dirAndFile.isAbsolute() == (dirAndFile.getRoot() != null));
	}

	public String getRevStr() {
		Verify.verifyNotNull(revStr);
		return revStr;
	}

	@Override
	public GitFileSystem getFileSystem() {
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
		if (dirAndFile.equals(GitFileSystem.JIM_FS_SLASH)) {
			return this;
		}
		return new GitPath(fileSystem, revStr, GitFileSystem.JIM_FS_SLASH);
	}

	public GitPath getWithoutRoot() {
		if (revStr.isEmpty()) {
			return this;
		}
		assert dirAndFile.isAbsolute();
		return new GitPath(fileSystem, "", GitFileSystem.JIM_FS_SLASH.relativize(dirAndFile));
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
	public Path resolve(Path other) {
		if (other.isAbsolute()) {
			return other;
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

	@Override
	public URI toUri() {
		final Path gitDir = getFileSystem().getGitDir();
		final URI uri;
		try {
			uri = new URI(GitFileSystemProvider.SCHEME, null, gitDir.toAbsolutePath().toString(), null);
		} catch (URISyntaxException e) {
			throw new IllegalStateException(e);
		}
		final UriBuilder builder = UriBuilder.fromUri(uri);
		if (!revStr.isEmpty()) {
			builder.queryParam("revStr", revStr);
		}
		if (!dirAndFile.toString().isEmpty()) {
			builder.queryParam("dirAndFile", dirAndFile);
		}

		return builder.build();
	}

	@Override
	public GitPath toAbsolutePath() {
		if (isAbsolute()) {
			return this;
		}
		return getMasterSlashPath(fileSystem).resolveRelative(this);
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
