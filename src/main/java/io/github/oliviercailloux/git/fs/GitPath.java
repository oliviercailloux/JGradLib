package io.github.oliviercailloux.git.fs;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Verify.verify;
import static io.github.oliviercailloux.jaris.exceptions.Unchecker.URI_UNCHECKER;

import java.io.IOException;
import java.net.URI;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchEvent.Modifier;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Streams;
import com.google.common.jimfs.Jimfs;

/**
 * Has an optional root component and an optional sequence of names (strings).
 * If it has no root component, then it has a sequence of names. The root
 * component, if it is present, consists in a git reference (a string which must
 * start with refs/, such as refs/heads/main) or an {@link ObjectId} (not both).
 * The sequence of names, if it is present, consists in a non-empty sequence of
 * names, and either consists in a unique empty name, or contains no empty name.
 * Each name is a string that does not contain any slash.
 *
 * This path is absolute iff the root component is present.
 *
 * <h1>String form</h1>
 *
 * The string form of a path consists in the string form of its root component,
 * if it has one, followed by its internal path.
 *
 * The string form of a root component is /gitref/, or /sha1/.
 *
 * Its internal path is a string that starts with a slash iff the path is
 * absolute, and is composed of the names that constitute its sequence of names,
 * separated by slashes.
 *
 * <h1>Summary</h1>
 *
 * Instances of this class are in exactly one of these four cases.
 * <ul>
 * <li>It has no root component. Equivalently, its string form contains no
 * leading slash. Equivalently, its string form contains no two consecutive
 * slashes. Equivalently, it is a relative path. Implies that it has a sequence
 * of names.</li>
 * <ul>
 * <li>Its sequence of names consists in a unique empty name. Equivalently, it
 * represents the default path of the file system, that is, the branch
 * <tt>main</tt> and the root directory in that branch. Equivalently, its string
 * form is <code>""</code>.</li>
 * <li>Its sequence of names consists in at least one name, and contains no
 * empty name. An example of string form is <code>"some/path"</code>.</li>
 * </ul>
 * <li>It has a root component. Equivalently, its string form contains a leading
 * slash. Equivalently, its string form contains two consecutive slashes exactly
 * once. Equivalently, it is an absolute path.</li>
 * <ul>
 * <li>It consists in a root component only. Equivalently, it has no sequence of
 * name. Equivalently, its string form ends with two slashes. An example of
 * string form is <code>"/refs/heads/main//"</code>.</li>
 * <li>It has a root component and a sequence of names. An example of string
 * form is <code>"/refs/heads/main//some/path"</code>.</li>
 * </ul>
 *
 * <h1>URI</h1>
 *
 * newFs must be given a Uri of the form gitjfs:/some/path/ (with a trailing
 * slash). getPath must be given a Uri of the form
 * gitjfs:/some/path/?root=/refs/heads/main/&internal-path=/internal/path. On
 * Windows, gitjfs:///c:/path/to/the%20file.txt?… (root is optional and
 * internal-path is optional, internal-path must be present and start with a
 * slash if root is present). The path must have a
 * <a href="https://stackoverflow.com/a/16445016/">trailing slash</a> and
 * denotes a local directory corresponding to file:/the/same/path/.
 *
 * <h1>Extended discussion</h1>
 *
 * This documentation considers a sequence of names to be never empty: if a path
 * has a sequence of names, it is not empty.
 *
 * The string form of the root component starts and ends with a slash, and
 * contains more slashes iff it is a git reference. It has typically the form
 * /refs/category/someref/, where category is tags, heads or remotes, but may
 * have other forms (see https://git-scm.com/docs/git-check-ref-format and
 * https://stackoverflow.com/a/47208574/). This class requires that the gitref
 * starts with "refs/", does not end with / and does not contain // or \ (these
 * are also restrictions on git refs anyway).
 *
 * The sequence of names is empty iff this path only represents a root
 * component.
 *
 * This path is said to be an empty path, or equivalently to represent the
 * default path of its file system, iff it has no root component and its
 * sequence of names contains exactly one name that is empty.
 *
 * The sequence containing exactly one name that is empty has "" as string form.
 *
 * As a consequence, the string form of the sequence of names starts with a
 * slash iff the path is absolute. The name elements are separated by slashes
 * (in violation of the {@link Path#toString()} contract).
 *
 * Consequently, the string form of a path starts with a slash iff the path is
 * absolute. If it is absolute, its string form contains a double slash.
 *
 * <h1>Rationale</h1> HEAD is not accepted for simplification. HEAD makes sense
 * only wrt a workspace, whereas this library is designed to work directly from
 * the git dir, without requiring a work space.
 *
 * A Ref is not accepted as an input because a Ref has an OId and does not
 * update, whereas this object considers a git reference as referring to OIds
 * dynamically.
 *
 * <h1>About Ref</h1>
 *
 * A Ref is symbolic and targets another Ref (which may be an unborn branch), or
 * non-symbolic and stores an OId. It may be an annotated tag and hence is
 * symbolic (?). Use rep.resolve to get an OId. findRef for short-hand forms.
 * getRefsByPrefix can be useful. getRef accepts non-ref items such as
 * MERGE_HEAD, …
 *
 * Question. Given the choice that a root component exists iff the path is
 * absolute (which is a nice and clear invariant),
 *
 * Question. Should /root// be considered as a root component only, thus with an
 * empty sequence of names, or should it be considered as a root component with
 * a one element sequence of names, equal to "/"? Equivalently, should
 * getFileName return null on /root//? (I suppose the intent of the spec is that
 * getFileName() must return null iff the path is a root component only.) Note
 * that the Windows implementation <a href=
 * "https://github.com/openjdk/jdk/tree/450452bb8cb617682a3eb28ae651cb829a45dcc6/test/jdk/java/nio/file/Path/PathOps.java#L290">treats</a>
 * C:\ as a root component only, and returns null on getFileName(). It would be
 * nice to consider it a one element sequence, so that it would be never empty
 * (thereby easing usage) and because it feels more natural, as "/root//" really
 * denotes a directory, not "nothing" as the empty path does. But 1) this forces
 * us to return something to getFileName(), and 2) this would break the nice
 * guarantee that the sequence of names in a gitpath behaves like the sequence
 * of names in a linux path: "/" under linux is an empty sequence of names (this
 * is forced by the spec as it is only a root component). (And I believe that
 * also under Windows, "\" is considered a root component only, equivalently, an
 * empty sequence of names.) If we want the sequence of names of a gitpath to
 * behave like a normal sequence of names, we must have that /root// has the
 * sequence of names "/", which is contradictory, as the sequence of names "/"
 * does not exist under linux! (The path "/" is a root component only and
 * corresponds to an empty sequence of names.). Thus, we must say that the
 * sequence of names here is the part without the first slash.
 *
 * Thus, if we adopt the second interpretation, what do we return? We could
 * return just "/", or "/root//". to getFileName(), and consider it as relative
 * (which is I think similar to what the Windows implementation does: I think it
 * treats \ as relative). OR return
 *
 * NB /root// getFileName returns null: that seems to be the only reasonable
 * answer compatible with the specs. The only possible alternative seems to be
 * to return a relative path to "/", but it seems to me that the Windows
 * implementation does not do this; and it would break the nice invariant that
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
 *
 * @author Olivier Cailloux
 *
 */
public class GitPath implements Path {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GitPath.class);

	private static final Comparator<GitPath> COMPARATOR = Comparator.<GitPath, String>comparing(Path::toString);

	private static final String QUERY_PARAMETER_ROOT = "root";

	private static final String QUERY_PARAMETER_INTERNAL_PATH = "internal-path";

	static GitPath fromQueryString(GitRepoFileSystem fs, Map<String, String> splitQuery) {
		final Optional<String> rootValue = Optional.ofNullable(splitQuery.get(QUERY_PARAMETER_ROOT));
		final Optional<String> internalPathValue = Optional.ofNullable(splitQuery.get(QUERY_PARAMETER_INTERNAL_PATH));

		if (rootValue.isPresent()) {
			final String rootString = rootValue.get();
			checkArgument(internalPathValue.isPresent());
			final String internalPathString = internalPathValue.get();
			final Path internalPath = GitFileSystemProvider.JIM_FS_EMPTY.resolve(internalPathString);
			checkArgument(internalPath.isAbsolute());
			return absolute(fs, RootComponent.givenStringForm(rootString), internalPath);
		}

		final Path internalPath = GitFileSystemProvider.JIM_FS_EMPTY.resolve(internalPathValue.orElse(""));
		return relative(fs, internalPath);
	}

	static GitPath absolute(GitRepoFileSystem fs, RootComponent root, Path internalPath) {
		checkNotNull(root);
		checkArgument(internalPath.isAbsolute());
		return new GitPath(fs, root, internalPath);
	}

	static GitPath relative(GitRepoFileSystem fs, Path internalPath) {
		checkArgument(!internalPath.isAbsolute());
		return new GitPath(fs, null, internalPath);
	}

	private final GitRepoFileSystem fileSystem;

	/**
	 * May be <code>null</code>.
	 */
	private final RootComponent root;

	/**
	 * Linux style in-memory path, absolute iff has a root component iff this path
	 * is absolute iff this path has a root component, may contain no names ("/",
	 * good for root-only paths), may contain a single empty name ("", good for
	 * default path).
	 */
	private final Path dirAndFile;

	private GitPath(GitRepoFileSystem fileSystem, RootComponent root, Path dirAndFile) {
		checkArgument(dirAndFile != null);

		checkArgument(dirAndFile.getFileSystem().provider().getScheme().equals(Jimfs.URI_SCHEME));
		checkArgument(dirAndFile.isAbsolute() == (root != null));
		checkArgument(dirAndFile.isAbsolute() == (dirAndFile.getRoot() != null));
		final boolean noSlashInNames = Streams.stream(dirAndFile).noneMatch(p -> p.toString().contains("/"));
		checkArgument(noSlashInNames);
		final boolean hasEmptyName = Streams.stream(dirAndFile).anyMatch(p -> p.toString().isEmpty());
		if (hasEmptyName) {
			checkArgument(dirAndFile.getNameCount() == 1);
			checkArgument(root == null);
		}
		if (root == null) {
			checkArgument(dirAndFile.getNameCount() >= 1);
		}

		this.fileSystem = checkNotNull(fileSystem);
		this.root = root;
		this.dirAndFile = dirAndFile;
	}

	private RootComponent getRootOrDefault() {
		return root == null ? RootComponent.DEFAULT : root;
	}

	@Override
	public GitRepoFileSystem getFileSystem() {
		return fileSystem;
	}

	@Override
	public boolean isAbsolute() {
		return root != null;
	}

	@Override
	public GitPath getRoot() {
		if (root == null) {
			return null;
		}
		if (getNameCount() == 0) {
			return this;
		}
		return new GitPath(fileSystem, root, GitFileSystemProvider.JIM_FS_SLASH);
	}

	/**
	 * Returns the root component of this path, if this path has one (equivalently,
	 * if it is absolute).
	 *
	 * @return the root component.
	 */
	public RootComponent getRootComponent() {
		checkState(isAbsolute());
		verify(root != null);
		return root;
	}

	public RootComponent getRootComponentOrDefault() {
		return root != null ? root : RootComponent.DEFAULT;
	}

	@Override
	public int getNameCount() {
		return dirAndFile.getNameCount();
	}

	@Override
	public GitPath getName(int index) {
		final Path name = dirAndFile.getName(index);
		return new GitPath(fileSystem, null, name);
	}

	@Override
	public GitPath subpath(int beginIndex, int endIndex) {
		final Path subpath = dirAndFile.subpath(beginIndex, endIndex);
		return new GitPath(fileSystem, null, subpath);
	}

	@Override
	public GitPath getFileName() {
		final Path fileName = dirAndFile.getFileName();
		if (fileName == null) {
			return null;
		}
		return new GitPath(fileSystem, null, fileName);
	}

	@Override
	public GitPath getParent() {
		final Path parent = dirAndFile.getParent();
		if (parent == null) {
			return null;
		}
		return new GitPath(fileSystem, root, parent);
	}

	/**
	 * Tests if this path starts with the given path.
	 *
	 * <p>
	 * This path <em>starts</em> with the given path if this path's root component
	 * <em>equals</em> the root component of the given path, and this path starts
	 * with the same name elements as the given path. If the given path has more
	 * name elements than this path then {@code false} is returned.
	 *
	 * <p>
	 * If the given path is associated with a different {@code FileSystem} to this
	 * path then {@code false} is returned.
	 *
	 * @param other the given path
	 *
	 * @return {@code true} iff this path starts with the given path.
	 */
	@Override
	public boolean startsWith(Path other) {
		if (!getFileSystem().equals(other.getFileSystem())) {
			return false;
		}
		final GitPath p2 = (GitPath) other;
		return Objects.equals(root, p2.root) && dirAndFile.startsWith(p2.dirAndFile);
	}

	/**
	 * Tests if this path ends with the given path.
	 *
	 * <p>
	 * If the given path has <em>N</em> elements, and no root component, and this
	 * path has <em>N</em> or more elements, then this path ends with the given path
	 * if the last <em>N</em> elements of each path, starting at the element
	 * farthest from the root, are equal.
	 *
	 * <p>
	 * If the given path has a root component then this path ends with the given
	 * path if the root component of this path <em>equals</em> the root component of
	 * the given path, and the corresponding elements of both paths are equal.
	 *
	 * <p>
	 * If the given path is associated with a different {@code FileSystem} to this
	 * path then {@code false} is returned.
	 *
	 * @param other the given path
	 *
	 * @return {@code true} iff this path ends with the given path.
	 */
	@Override
	public boolean endsWith(Path other) {
		if (!getFileSystem().equals(other.getFileSystem())) {
			return false;
		}
		final GitPath p2 = (GitPath) other;
		final boolean matchRoot = (p2.root == null || p2.root.equals(root));
		return matchRoot && dirAndFile.endsWith(p2.dirAndFile);
	}

	@Override
	public GitPath normalize() {
		return new GitPath(fileSystem, root, dirAndFile.normalize());
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

	private GitPath resolveRelative(Path other) {
		checkArgument(!other.isAbsolute());

		if (other.toString().equals("")) {
			return this;
		}
		if (!getFileSystem().equals(other.getFileSystem())) {
			throw new IllegalArgumentException();
		}
		final GitPath p2 = (GitPath) other;
		return new GitPath(fileSystem, root, dirAndFile.resolve(p2.dirAndFile));
	}

	@Override
	public GitPath relativize(Path other) {
		if (!getFileSystem().equals(other.getFileSystem())) {
			throw new IllegalArgumentException();
		}
		final GitPath p2 = (GitPath) other;
		if (!Objects.equals(root, p2.root)) {
			throw new IllegalArgumentException();
		}
		return new GitPath(fileSystem, null, dirAndFile.relativize(p2.dirAndFile));
	}

	@SuppressWarnings("resource")
	@Override
	public URI toUri() {
		/**
		 * I do not use UriBuilder because it performs “contextual encoding of
		 * characters not permitted in the corresponding URI component following the
		 * rules of the application/x-www-form-urlencoded media type for query
		 * parameters”, and therefore encodes / to %2F. This is not required by the spec
		 * when not using that media type (as in this case). See
		 * https://stackoverflow.com/a/49400367/.
		 */
		final StringBuilder queryBuilder = new StringBuilder();
		if (root != null) {
			/** TODO think about when ref or dirAndFile contains = or &. */
			queryBuilder
					.append(QUERY_PARAMETER_ROOT + "=" + QueryUtils.QUERY_ENTRY_ESCAPER.escape(root.toStringForm()));
			queryBuilder.append("&" + QUERY_PARAMETER_INTERNAL_PATH + "="
					+ QueryUtils.QUERY_ENTRY_ESCAPER.escape(dirAndFile.toString()));
		} else {
			if (!dirAndFile.toString().isEmpty()) {
				queryBuilder.append("internal-path=" + dirAndFile.toString());
			}
		}
		final String query = queryBuilder.toString();

		final URI fsUri = fileSystem.toUri();
		final URI uri = URI_UNCHECKER
				.getUsing(() -> new URI(fsUri.getScheme(), fsUri.getAuthority(), fsUri.getPath(), query, null));
		return uri;
	}

	@Override
	public GitPath toAbsolutePath() {
		if (isAbsolute()) {
			return this;
		}
		return fileSystem.mainSlash.resolveRelative(this);
	}

	public GitPath toRelativePath() {
		if (!isAbsolute()) {
			return this;
		}
		verify(dirAndFile.isAbsolute());
		return new GitPath(fileSystem, null, GitFileSystemProvider.JIM_FS_SLASH.relativize(dirAndFile));
	}

	@Override
	public Path toRealPath(LinkOption... options) throws IOException, NoSuchFileException {
		/**
		 * Not easy to get right. For example, given "a/../b", if no link, I have to
		 * normalize to "b"; if contains a link (e.g. a → c/d), I have to either leave
		 * it alone ("a/../b"), or follow the link and normalize ("c/b").
		 */
		throw new UnsupportedOperationException();
//		final GitPath absolutePath = toAbsolutePath();
//		final GitObject gitObject = fileSystem.getAndCheckGitObject(absolutePath);
//		final String revStrPossiblyResolved;
//		if (ImmutableSet.copyOf(options).contains(LinkOption.NOFOLLOW_LINKS)) {
//			revStrPossiblyResolved = absolutePath.getNonEmptyRevStr();
//		} else {
//			revStrPossiblyResolved = gitObject.getCommit().getName();
//		}
//		/**
//		 * Shouldn’t use toRealPath here as this path does not exist in JimFs. Replacing
//		 * by normalize down here is not adequate either, as JGit does not consider that
//		 * ./someexistingdir exists; and normalizing before checking would be confusing
//		 * when this would lose symbolic links information, I suppose (though this would
//		 * be nice to do systematically and correctly, also for the rest of the API).
//		 */
//		/**
//		 * TODO https://www.eclipse.org/forums/index.php?t=msg&th=1103986 1. check if it
//		 * is a link; if so, crash if wants to follow links. 2. Instead of crashing, add
//		 * a method that gets link target (one step): /link/stuff/ → /blah/bloh/stuff/;
//		 * add this to the set of currently followed links or throws if already in the
//		 * set; add method that follows through all steps then clears the set if it
//		 * ends. Also follow the links to other file systems if the user explicitly
//		 * allowed at creation time to get out of a given repository (otherwise,
//		 * dangerous).
//		 */
//		return new GitPath(fileSystem, revStrPossiblyResolved, absolutePath.dirAndFile);
	}

	@Override
	public WatchKey register(WatchService watcher, Kind<?>[] events, Modifier... modifiers) throws IOException {
		throw new UnsupportedOperationException();
	}

	/**
	 * Compares path according to their string form. The spec mandates
	 * “lexicographical” comparison, but I ignore what this means: apparently not
	 * that the root components must be considered first, then the rest, considering
	 * that the OpenJdk implementation of the Linux default file system sorts ""
	 * before "/" before "a"; and probably not that they must be sorted by
	 * lexicographic ordering of their string form, as it would then be a bit odd to
	 * specify that “The ordering defined by this method is provider specific”.
	 */
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
		return fileSystem.equals(p2.fileSystem) && Objects.equals(root, p2.root) && dirAndFile.equals(p2.dirAndFile);
	}

	@Override
	public int hashCode() {
		return Objects.hash(fileSystem, root, dirAndFile);
	}

	@Override
	public String toString() {
		final String rootStr = root == null ? "" : root.toStringForm();
		return rootStr + dirAndFile.toString();
	}

	Optional<ObjectId> getCommitId() throws IOException {
		final RootComponent effectiveRoot = getRootOrDefault();

		if (effectiveRoot.isObjectId()) {
			return Optional.of(effectiveRoot.getObjectId());
		}

		final Optional<ObjectId> commitId;
		final String effectiveRef = effectiveRoot.getGitRef();

		final Ref ref = getFileSystem().getRepository().exactRef(effectiveRef);
		if (ref == null) {
			LOGGER.debug("Rev str " + effectiveRef + " not found.");
			commitId = Optional.empty();
		} else {
			commitId = Optional.ofNullable(ref.getLeaf().getObjectId());
			if (commitId.isEmpty()) {
				LOGGER.debug("Ref " + ref.getName() + " points to nothing existing.");
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
