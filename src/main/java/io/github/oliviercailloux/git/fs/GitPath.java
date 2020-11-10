package io.github.oliviercailloux.git.fs;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Verify.verify;
import static io.github.oliviercailloux.jaris.exceptions.Unchecker.URI_UNCHECKER;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.Files;
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
 * An instance of this class has an optional root component and a (possibly
 * empty) sequence of names (strings). If it has no root component, then its
 * sequence of names is non empty.
 * <p>
 * The root component, if it is present, consists in either a git reference (a
 * string which must start with <tt>refs/</tt>, such as
 * <tt>refs/heads/main</tt>) or a commit id (represented as an
 * {@link ObjectId}).
 * <p>
 * The sequence of names represents a path inside a given commit. It either
 * consists of a unique empty name, or contains no empty name. Each name is a
 * string that does not contain any slash.
 * <p>
 * This path is absolute iff the root component is present.
 *
 * <h1>String form</h1>
 * <p>
 * The string form of a path consists in the string form of its root component,
 * if it has one, followed by its internal path.
 * <p>
 * The string form of a root component is <tt>/gitref/</tt>, where
 * <tt>gitref</tt> is a git reference; or <tt>/sha1/</tt>, where <tt>sha1</tt>
 * is a commit id.
 * <p>
 * Its internal path is a string that starts with a slash iff the path is
 * absolute, and is composed of the names that constitute its sequence of names,
 * separated by slashes.
 * <p>
 * An absolute git path string is a git path string that starts with
 * <code>/</code>.
 *
 * <h1>Possible cases</h1>
 * <p>
 * It follows from these rules and from the {@link GitFileSystem default path
 * rule} that an instance of this class matches exactly one of these two cases
 * (each admitting a special case).
 * <ul>
 * <li>It has no root component. Equivalently, its string form contains no
 * leading slash. Equivalently, its string form contains no two consecutive
 * slashes. Equivalently, it is a relative path. Implies that its sequence of
 * names is not empty. An example of string form is
 * <code>"some/path"</code>.</li>
 * <ul>
 * <li>As a special case, its sequence of names may consist in a unique empty
 * name. Equivalently, it represents the default path of the file system, that
 * is, the branch <tt>main</tt> and the root directory in that branch.
 * Equivalently, its string form is {@code ""}.</li>
 * </ul>
 * <li>It has a root component. Equivalently, its string form contains a leading
 * slash. Equivalently, its string form contains two consecutive slashes exactly
 * once. Equivalently, it is an absolute path. Implies that its sequence of
 * names contain no empty name. An example of string form is
 * <code>"/refs/heads/main//some/path"</code>.</li>
 * <ul>
 * <li>As a special case, it may consist in a root component only. Equivalently,
 * its sequence of names is empty. Equivalently, its string form ends with two
 * slashes. An example of string form is <code>"/refs/heads/main//"</code>.</li>
 * </ul>
 *
 * <h1>Extended discussion</h1>
 * <p>
 * The string form of the root component starts and ends with a slash, and
 * contains more slashes iff it is a git reference. It has typically the form
 * <tt>/refs/category/someref/</tt>, where <tt>category</tt> is <tt>tags</tt>,
 * <tt>heads</tt> or <tt>remotes</tt>, but may have
 * <a href="https://git-scm.com/docs/git-check-ref-format">other</a>
 * <a href="https://stackoverflow.com/a/47208574/">forms</a>. This class
 * requires that the git reference starts with <tt>refs/</tt>, does not end with
 * <tt>/</tt> and does not contain <tt>//</tt> or <tt>\</tt> (these are also
 * official restrictions on git references as imposed by git).
 * <p>
 * This path is said to represent the default path of its file system, or
 * equivalently to be an empty path, iff it has no root component and its
 * sequence of names contains exactly one name that is empty. The sequence
 * containing exactly one name that is empty has <tt>""</tt> as string form.
 *
 * <h2>Rationale</h2>
 * <p>
 * The special git reference <tt>HEAD</tt> is not accepted for simplification.
 * <tt>HEAD</tt> makes sense only with respect to a workspace, whereas this
 * library is designed to work directly from the git directory, without
 * requiring a work space.
 * <p>
 * A {@link Ref} is not accepted as an input because a {@code Ref} has an object
 * id which does not update, whereas this object considers a git reference as
 * referring to object ids (in fact, commit ids) dynamically.
 * <p>
 * The fact that the path <tt>/someref//</tt> is considered as a root component
 * only, thus with an empty sequence of names, can appear surprising. Note first
 * that slash is a path separator, thus cannot be part of a name. The only other
 * possible choice is thus to consider that <tt>/someref//</tt> contains a
 * sequence of name of one element, being the empty string. An advantage of this
 * choice is that the sequence of names would be never empty (thereby easing
 * usage) and that it feels like a natural generalization of the case of
 * <tt>""</tt>, a path also containing one element being the empty string. But
 * from the wording of {@link Path#getFileName()}, it seems like this method
 * should return null iff the path is a root component only, and that what it
 * should return is distinct from the root component. Note that the Windows
 * implementation <a href=
 * "https://github.com/openjdk/jdk/tree/450452bb8cb617682a3eb28ae651cb829a45dcc6/test/jdk/java/nio/file/Path/PathOps.java#L290">treats</a>
 * C:\ as a root component only, and returns {@code null} on
 * {@code getFileName()}. (And I believe that also under Windows, <tt>\</tt> is
 * considered a root component only, equivalently, an empty sequence of names.)
 * For sure, under Linux, <tt>/</tt> is a root component only. Thus, to behave
 * similarly, we have to return {@code null} to {@code getFileName()} on the
 * path <tt>/someref//</tt>, hence, to treat it as a root component only. (This
 * choice would also break the nice (internal) guarantee that the internal path
 * in a git path behaves like the sequence of names in a linux path, as
 * <tt>/</tt> under Linux is an empty sequence.)
 *
 */
public class GitPath implements Path {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GitPath.class);

	private static final Comparator<GitPath> COMPARATOR = Comparator.<GitPath, String>comparing(Path::toString);

	private static final String QUERY_PARAMETER_ROOT = "root";

	private static final String QUERY_PARAMETER_INTERNAL_PATH = "internal-path";

	static GitPath fromQueryString(GitFileSystem fs, Map<String, String> splitQuery) {
		final Optional<String> rootValue = Optional.ofNullable(splitQuery.get(QUERY_PARAMETER_ROOT));
		final Optional<String> internalPathValue = Optional.ofNullable(splitQuery.get(QUERY_PARAMETER_INTERNAL_PATH));

		if (rootValue.isPresent()) {
			final String rootString = rootValue.get();
			checkArgument(internalPathValue.isPresent());
			final String internalPathString = internalPathValue.get();
			final Path internalPath = GitFileSystem.JIM_FS_EMPTY.resolve(internalPathString);
			checkArgument(internalPath.isAbsolute());
			return absolute(fs, RootComponent.stringForm(rootString), internalPath);
		}

		final Path internalPath = GitFileSystem.JIM_FS_EMPTY.resolve(internalPathValue.orElse(""));
		return relative(fs, internalPath);
	}

	static GitPath absolute(GitFileSystem fs, RootComponent root, Path internalPath) {
		checkNotNull(root);
		checkArgument(internalPath.isAbsolute());
		return new GitPath(fs, root, internalPath);
	}

	static GitPath relative(GitFileSystem fs, Path internalPath) {
		checkArgument(!internalPath.isAbsolute());
		return new GitPath(fs, null, internalPath);
	}

	private final GitFileSystem fileSystem;

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

	private GitPath(GitFileSystem fileSystem, RootComponent root, Path dirAndFile) {
		checkNotNull(dirAndFile);

		checkArgument(dirAndFile.getFileSystem().provider().getScheme().equals(Jimfs.URI_SCHEME));
		checkArgument(dirAndFile.isAbsolute() == (root != null));
		checkArgument(dirAndFile.isAbsolute() == (dirAndFile.getRoot() != null));
		final boolean noSlashInNames = Streams.stream(dirAndFile).noneMatch(p -> p.toString().contains("/"));
		verify(noSlashInNames);
		final boolean hasEmptyName = Streams.stream(dirAndFile).anyMatch(p -> p.toString().isEmpty());
		if (hasEmptyName) {
			verify(dirAndFile.getNameCount() == 1);
			checkArgument(root == null);
		}
		if (root == null) {
			checkArgument(dirAndFile.getNameCount() >= 1);
		}

		this.fileSystem = checkNotNull(fileSystem);
		this.root = root;
		this.dirAndFile = dirAndFile;
	}

	@Override
	public GitFileSystem getFileSystem() {
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
		return new GitPath(fileSystem, root, GitFileSystem.JIM_FS_SLASH);
	}

	/**
	 * Returns the root component of this path, if this path has one (equivalently,
	 * if it is absolute).
	 * <p>
	 * To obtain the root component that this path (possibly implicitly) refers to,
	 * including in the case it is relative, use
	 * {@code toAbsolutePath().getRootComponent()}.
	 *
	 * @return the root component.
	 * @throws IllegalStateException if this path is relative.
	 */
	public RootComponent getRootComponent() {
		checkState(isAbsolute());
		verify(root != null);
		return root;
	}

	/**
	 * Returns the root component of this path, if it has one, otherwise, the
	 * default root component of the associated path system.
	 */
	RootComponent getRootComponentOrDefault() {
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

	/**
	 * Returns a path that is this path with redundant name elements eliminated.
	 * <p>
	 * All occurrences of "{@code .}" are considered redundant. If a "{@code ..}" is
	 * preceded by a non-"{@code ..}" name then both names are considered redundant
	 * (the process to identify such names is repeated until it is no longer
	 * applicable).
	 * <p>
	 * This method does not access the file system; the path may not locate a file
	 * that exists. Eliminating "{@code ..}" and a preceding name from a path may
	 * result in the path that locates a different file than the original path. This
	 * can arise when the preceding name is a symbolic link.
	 *
	 * @return the resulting path or this path if it does not contain redundant name
	 *         elements; an empty path is returned if this path does not have a root
	 *         component and all name elements are redundant
	 *
	 * @see #getParent
	 */
	@Override
	public GitPath normalize() {
		return new GitPath(fileSystem, root, dirAndFile.normalize());
	}

	/**
	 * Resolve the given path against this path.
	 *
	 * <p>
	 * If the {@code other} parameter is an {@link #isAbsolute() absolute} path
	 * (equivalently, if it has a root component), then this method trivially
	 * returns {@code other}. If {@code other} is an <i>empty path</i> then this
	 * method trivially returns this path. Otherwise this method considers this path
	 * to be a directory and resolves the given path against this path: this method
	 * <em>joins</em> the given path to this path and returns a resulting path that
	 * {@link #endsWith ends} with the given path.
	 *
	 * @see #relativize
	 */
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

	/**
	 * Constructs a relative path between this path and a given path.
	 *
	 * <p>
	 * Relativization is the inverse of {@link #resolve(Path) resolution}. This
	 * method attempts to construct a {@link #isAbsolute relative} path that when
	 * {@link #resolve(Path) resolved} against this path, yields a path that locates
	 * the same file as the given path. For example, if this path is {@code "a/b"}
	 * and the given path is {@code "a/b/c/d"} then the resulting relative path
	 * would be {@code "c/d"}. A relative path between two paths can be constructed
	 * iff the two paths both are relative, or both are absolute and have the same
	 * root component. If this path and the given path are {@link #equals equal}
	 * then an <i>empty path</i> is returned.
	 *
	 * <p>
	 * For any two {@link #normalize normalized} paths <i>p</i> and <i>q</i>, where
	 * <i>q</i> does not have a root component, <blockquote>
	 * <i>p</i>{@code .relativize(}<i>p</i>
	 * {@code .resolve(}<i>q</i>{@code )).equals(}<i>q</i>{@code )} </blockquote>
	 *
	 * <p>
	 * TODO When symbolic links are supported, then whether the resulting path, when
	 * resolved against this path, yields a path that can be used to locate the
	 * {@link Files#isSameFile same} file as {@code other} is implementation
	 * dependent. For example, if this path is {@code "/a/b"} and the given path is
	 * {@code "/a/x"} then the resulting relative path may be {@code
	 * "../x"}. If {@code "b"} is a symbolic link then is implementation dependent
	 * if {@code "a/b/../x"} would locate the same file as {@code "/a/x"}.
	 *
	 * @param other the path to relativize against this path
	 *
	 * @return the resulting relative path, or an empty path if both paths are equal
	 *
	 * @throws IllegalArgumentException if {@code other} is not a {@code Path} that
	 *                                  can be relativized against this path
	 */
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

	/**
	 * Returns a URI referring to the git file system instance associated to this
	 * path, and referring to this specific file in that file system.
	 *
	 * @see GitFileSystemProvider#getPath(URI)
	 */
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
		final String escapedDirAndFile = QueryUtils.QUERY_ENTRY_ESCAPER.escape(dirAndFile.toString());
		final StringBuilder queryBuilder = new StringBuilder();
		if (root != null) {
			queryBuilder
					.append(QUERY_PARAMETER_ROOT + "=" + QueryUtils.QUERY_ENTRY_ESCAPER.escape(root.toStringForm()));
			queryBuilder.append("&" + QUERY_PARAMETER_INTERNAL_PATH + "=" + escapedDirAndFile);
		} else {
			if (!dirAndFile.toString().isEmpty()) {
				queryBuilder.append(QUERY_PARAMETER_INTERNAL_PATH + "=" + escapedDirAndFile);
			}
		}
		final String query = queryBuilder.toString();

		final URI fsUri = fileSystem.toUri();
		final URI uriBasis = URI_UNCHECKER
				.getUsing(() -> new URI(fsUri.getScheme(), fsUri.getAuthority(), fsUri.getPath(), null, null));
		final String qMark = query.isEmpty() ? "" : "?";
		/**
		 * As the query part is encoded already, we don’t want to use the URI
		 * constructor with query parameter, which would in turn encode the %-escapers
		 * as if they were %-signs.
		 */
		return URI.create(uriBasis + qMark + query);
	}

	/**
	 * Returns a {@code Path} object representing the absolute path of this path.
	 *
	 * <p>
	 * If this path is already {@link Path#isAbsolute absolute} then this method
	 * simply returns this path. Otherwise, this method resolves the path against
	 * the {@link GitFileSystem default directory} and thus returns a path with a
	 * root component referring to the <tt>main</tt> branch.
	 *
	 * <p>
	 * This method does not access the underlying file system and requires no
	 * specific permission.
	 *
	 */
	@Override
	public GitPath toAbsolutePath() {
		if (isAbsolute()) {
			return this;
		}
		return fileSystem.mainSlash.resolveRelative(this);
	}

	GitPath toRelativePath() {
		if (!isAbsolute()) {
			return this;
		}
		verify(dirAndFile.isAbsolute());
		return new GitPath(fileSystem, null, GitFileSystem.JIM_FS_SLASH.relativize(dirAndFile));
	}

	/**
	 * At the moment, throws {@code UnsupportedOperationException}.
	 */
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

	/**
	 * At the moment, throws {@code UnsupportedOperationException}.
	 */
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

	/**
	 * Tests this path for equality with the given object.
	 *
	 * <p>
	 * This method returns {@code true} iff the given object is a git path
	 * associated to the same git file system as this path, and the paths have equal
	 * root components (or they are both absent) and internal paths. The internal
	 * paths are compared in a case-sensitive way (conforming to the Linux concept
	 * of path equality). Equivalently, two git paths are equal iff they are
	 * associated to the same git file system and have the same {@link GitPath
	 * string forms}.
	 *
	 * <p>
	 * This method does not access the file system and the files are not required to
	 * exist.
	 *
	 * @see Files#isSameFile(Path, Path) (TODO)
	 */
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

	/**
	 * Returns the {@link GitPath string form} of this path.
	 *
	 * <p>
	 * If this path was created by converting a path string using the
	 * {@link FileSystem#getPath getPath} method then the path string returned by
	 * this method may differ from the original String used to create the path.
	 *
	 */
	@Override
	public String toString() {
		final String rootStr = root == null ? "" : root.toStringForm();
		return rootStr + dirAndFile.toString();
	}

	/**
	 * if this path is a reference, and this method returns an objectId, then it
	 * implies existence of that commit root; otherwise (thus if the given path
	 * contains an object id and not a reference), this method simply returns the
	 * object id without checking for its validity.
	 */
	public Optional<ObjectId> getCommitId() throws IOException {
		final RootComponent effectiveRoot = getRootComponentOrDefault();

		if (effectiveRoot.isCommitId()) {
			return Optional.of(effectiveRoot.getCommitId());
		}

		final Optional<ObjectId> commitId;
		final String effectiveRef = effectiveRoot.getGitRef();

		/**
		 * Should perhaps think about this. A Ref is symbolic and targets another Ref
		 * (which may be an unborn branch), or non-symbolic and stores an OId. It may be
		 * an annotated tag and hence is symbolic (?). Use rep.resolve to get an OId.
		 * findRef for short-hand forms. getRefsByPrefix can be useful. getRef accepts
		 * non-ref items such as MERGE_HEAD, …
		 */
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
