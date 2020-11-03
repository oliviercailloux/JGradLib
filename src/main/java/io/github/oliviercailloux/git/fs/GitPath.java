package io.github.oliviercailloux.git.fs;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Verify.verify;
import static com.google.common.base.Verify.verifyNotNull;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchEvent.Modifier;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.jgit.internal.storage.dfs.DfsRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSet;
import com.google.common.jimfs.Jimfs;

import io.github.oliviercailloux.git.fs.GitRepoFileSystem.GitObject;

/**
 * Has an optional root component and a sequence of names.
 *
 * The root component is present iff this path is absolute. If present, it
 * consists in a git reference (a string which must start with /refs/, such as
 * /refs/heads/main) or an {@link ObjectId} (not both).
 *
 * The sequence of names is empty iff this path only represents a root
 * component.
 *
 * <h1>String form</h1> The string form of a path consists in the string form of
 * its root component, if it has one, followed by the string form of its
 * sequence of names.
 *
 * The string form of the root component (if it exists) starts and ends with a
 * slash, and contains more slashes iff it is a git reference. It has typically
 * the form /refs/category/someref/, where category is tags, heads or remotes,
 * but may have other forms (see https://git-scm.com/docs/git-check-ref-format).
 * This class requires that these formas are such that the ref does not end with
 * / and does not contain // (these are also restrictions on git refs anyway).
 *
 * The string form of the sequence of names obeys the rules of the string form
 * of a linux path, more precisely, of an absolute linux path if the path is
 * absolute, and of a relative linux path if the path is relative.
 *
 * <h2>Consequently</h2>As a consequence, the string form of the sequence of
 * names starts with a slash iff the path is absolute. The name elements are
 * separated by slashes (in violation of the {@link Path#toString()} contract).
 *
 * Consequently, the string form of a path starts with a slash iff the path is
 * absolute. If it is absolute, its string form contains a double slash.
 *
 * \/c403// the root component only "" the relative path with one name element
 * that is empty stuff/thing a relative path with two name elements
 * \/refs/heads/master//stuff an absolute path with one name element
 *
 * <h1>URI</h1> newFs must be given a Uri of the form gitjfs:/some/path/ (with a
 * trailing slash). getPath must be given a Uri of the form
 * gitjfs:/some/path/?ref=/refs/heads/main/&sub-path=/internal/path. On Windows,
 * gitjfs:///c:/path/to/the%20file.txt?… (ref is optional, defaults to …main,
 * and sub-path is optional, defaults to /) (or only one slash). The path must
 * have a <a href="https://stackoverflow.com/a/16445016/">trailing slash</a> and
 * denotes a local directory corresponding to file:/the/same/path/.
 *
 * <h1>Rationale</h1> HEAD is not accepted for simplification. HEAD makes sense
 * only wrt a workspace, whereas this library is designed to work directly from
 * the git dir, without requiring a work space.
 *
 * A Ref is not accepted as an input because a Ref has an OId and does not
 * update, whereas this object considers a git reference as referring to OIds
 * dynamically.
 *
 *
 *
 * <h1>About Ref</h1> A Ref is symbolic and targets another Ref (which may be an
 * unborn branch), or non-symbolic and stores an OId. It may be an annotated tag
 * and hence is symbolic (?). Use rep.resolve to get an OId. findRef for
 * short-hand forms. getRefsByPrefix can be useful. getRef accepts non-ref items
 * such as MERGE_HEAD, …
 *
 * <h1>Old</h1>
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
		 * TODO should check that in git, revStr may not end with /
		 */
		checkArgument(!revStr.endsWith("/"));
		checkArgument(dirAndFile.isAbsolute() == (dirAndFile.getRoot() != null));
	}

	public String getRevStr() {
		verifyNotNull(revStr);
		return revStr;
	}

	String getNonEmptyRevStr() {
		verifyNotNull(revStr);
		verify(!revStr.isEmpty());
		return revStr;
	}

	private String getRevStrOrMaster() {
		return revStr.equals("") ? "master" : revStr;
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
	public Path toRealPath(LinkOption... options) throws IOException, NoSuchFileException {
		final GitPath absolutePath = this.toAbsolutePath();
		final GitObject gitObject = fileSystem.getAndCheckGitObject(absolutePath);
		final String revStrPossiblyResolved;
		if (ImmutableSet.copyOf(options).contains(LinkOption.NOFOLLOW_LINKS)) {
			revStrPossiblyResolved = absolutePath.getNonEmptyRevStr();
		} else {
			revStrPossiblyResolved = gitObject.getCommit().getName();
		}
		/**
		 * Shouldn’t use toRealPath here as this path does not exist in JimFs. Replacing
		 * by normalize down here is not adequate either, as JGit does not consider that
		 * ./someexistingdir exists; and normalizing before checking would be confusing
		 * when this would lose symbolic links information, I suppose (though this would
		 * be nice to do systematically and correctly, also for the rest of the API).
		 */
		/**
		 * TODO https://www.eclipse.org/forums/index.php?t=msg&th=1103986 1. check if it
		 * is a link; if so, crash if wants to follow links. 2. Instead of crashing, add
		 * a method that gets link target (one step): /link/stuff/ → /blah/bloh/stuff/;
		 * add this to the set of currently followed links or throws if already in the
		 * set; add method that follows through all steps then clears the set if it
		 * ends. Also follow the links to other file systems if the user explicitly
		 * allowed at creation time to get out of a given repository (otherwise,
		 * dangerous).
		 */
		return new GitPath(fileSystem, revStrPossiblyResolved, absolutePath.dirAndFile);
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

	boolean isRevStrAnObjectId() {
		/**
		 * This is incorrect (I suppose): a ref could have the same length as an object
		 * id.
		 */
		return revStr.length() == 40;
	}

	Optional<ObjectId> getCommitId() throws IOException {
		if (isRevStrAnObjectId()) {
			return Optional.of(ObjectId.fromString(revStr));
		}
		return getCommitIdFromRef();
	}

	/**
	 * revStr must be a ref, such as “master”, and not a SHA-1 object id
	 */
	Optional<ObjectId> getCommitIdFromRef() throws IOException {
		final Optional<ObjectId> commitId;

		final String effectiveRevStr = getRevStrOrMaster();
		final Ref ref = getFileSystem().getRepository().findRef(effectiveRevStr);
		if (ref == null) {
			LOGGER.debug("Rev str " + effectiveRevStr + " not found.");
			commitId = Optional.empty();
		} else {
			commitId = Optional.ofNullable(ref.getLeaf().getObjectId());
			if (commitId.isEmpty()) {
				/** TODO enquire how this can happen. */
				LOGGER.debug("Ref " + ref.getName() + " does not exist yet.");
			} else {
				LOGGER.debug("Ref {} found: {}.", ref.getName(), commitId);
			}
		}
		return commitId;
	}

	ObjectId getCommitIdOrThrow() throws IOException, NoSuchFileException {
		return getCommitId().orElseThrow(() -> new NoSuchFileException(toString()));
	}

}
