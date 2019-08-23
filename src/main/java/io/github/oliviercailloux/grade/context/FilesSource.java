package io.github.oliviercailloux.grade.context;

import java.nio.file.Path;
import java.util.Map;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import io.github.oliviercailloux.git.FileContent;
import io.github.oliviercailloux.grade.GradingException;
import io.github.oliviercailloux.grade.contexters.FilesSourceImpl;

/**
 * An abstraction over file hierarchies, which can be provided by the network (a
 * GitHub repository), a cache of file contents in memory (previously fetched
 * from GitHub), a git repository on the local hard disk, …
 *
 * The key for accessing a file is a Path, and those paths are always relative
 * to the project root.
 *
 * TODO Refactor this using a GitFileSystem, as follows.
 *
 * <h1>GFS</h1>
 * <li>one file-store</li>
 * <li>Assumes cloned</li>
 * <li>Does not update</li>
 * <li>getRootDirectories(): the commits</li>
 *
 * <h1>GitPath</h1>
 * <li>:GFS</li>
 * <li>commit, symbolic name or SHA, may be "". Utiliser (revstr, ObjectId).
 * ObjectId représente un SHA-1. Revstr, voir Repository#resolve, est plus large
 * que juste SHA ou Ref. Conserver RevCommit, un ObjectId qui représente un
 * commit (try (RevWalk walk = new RevWalk(repository)) commit =
 * walk.parseCommit(revSpec);) et le RevTree correspondant.</li>
 * <li>Pour lecture: getPaths(folder: GitPath, recurse: boolean) via le treewalk
 * (voir getFileContents), renvoyer Map<GitPath, ObjectId> où les répertoires
 * correspondent à des tree et les fichiers à des blobs. getStream(GitPath) :
 * trouver l’objet correspondant ? Voir getBlobId.</li>
 *
 * <h1>GitFSProvider</h1>
 * <li>Registers URI scheme autograder</li>
 * <li>newFS(gitRepo: URI, gitFolder: Path): GFS, gitFolder Path must be a
 * default file system path because of limitations of git underlying client;
 * gitRepo URI must use autograde scheme per API rules; gitFolder is optional,
 * defaults to /tmp/repo/.git unless URI has a file git-cloneable equivalent.
 * Does clone --no-checkout, or checks that the remote is the git-cloneable
 * equivalent if folder already exists and updates it</li>
 * <li>Any valid git repo URI (with scheme autograde) has an equivalent
 * git-cloneable URI.</li>
 * <li>subclass of FSProvider, currently GitContext</li> A git repo URI has
 * scheme autograde (mandatory for being an acceptable parameter of
 * GFSProvider), and has an equivalent git-cloneable URI. It has syntax
 * autograde://host.xz[:port]/path/to/repo.git[/]?git-scheme=access-scheme,
 * where access-scheme is ssh, git, http or https, and with the git-cloneable
 * equivalent being access-scheme://host.xz[:port]/path/to/repo.git[/], or
 * autograde://user@host.xz/path/to/repo.git[/][?git-scheme=ssh], with
 * git-clonable equivalent ssh://user@host.xz/path/to/repo.git[/], or
 * autograde:///path/to/repo.git[/][?git-scheme=file], with git-cloneable
 * equivalent file:///path/to/repo.git[/]. (The git clone man page authorizes
 * the trailing slash while GitHub forbids it.) When given the latter form with
 * no optional tmp value, the GFSProvider does not clone or update and reads
 * directly from the given folder. Even under Windows, the file-URI-like paths
 * (slash-separated, percent-encoded) are used. In general, for
 * https://docs.oracle.com/en/java/javase/12/docs/api/java.base/java/nio/file/spi/FileSystemProvider.html#getPath(java.net.URI),
 * a URI may be more complete and identify a commit (possibly master), directory
 * and file. (How to do this in general?) A URI represents a resource (which
 * possibly can change, as with resources in the internet).
 * <li>Builds history?</li>
 *
 * <h1>Autres</h1> MarkHelper: creates a Predicate<Path> from a
 * Predicate<FileContents> by transforming it with a static method?
 *
 * <h1>Details to integrate</h1>
 * <li>getPath(URI) can repr. a path without commit or one with commit?</li>
 * <li>Grader: grades using a FS (giving access to commits as roots and to
 * paths) when needs access to commits. Otherwise, grades using a Path (and
 * works relative to this). Short term: also needs the Map<Commit, TS>. Later:
 * Using the URI of the FS, the Grader may also access (through DI) the time
 * stamps of the commits.</li>
 *
 * Note that currently, FullCInit instance is a GFSProvider that first clones
 * the repo, analyzes its history, determines a default commit, and, given
 * commits, gives GFS instances.
 *
 * Now checking out the default commit to a given folder is done using
 * https://stackoverflow.com/a/50418060. Note that it can also be used to “check
 * out” a sub-folder rather than the whole workspace. (Useful for running Maven
 * on the folder, for example.)
 *
 * @author Olivier Cailloux
 *
 */
public interface FilesSource {
	@SuppressWarnings("unused")
	public static final Logger LOGGER = LoggerFactory.getLogger(FilesSource.class);

	public static FilesSource fromMemory(Map<Path, String> contents) {
		return new FilesSourceImpl(contents.keySet(), (p) -> contents.getOrDefault(p, ""));
	}

	public static FilesSource empty() {
		return new FilesSourceImpl(ImmutableSet.of(), (p) -> "");
	}

	/**
	 * Two FilesSource are equal iff they have the same contents.
	 */
	@Override
	boolean equals(Object o2);

	/**
	 * The content is cached if this provides significant benefit compared to
	 * fetching the content again from the source of this interface (typically:
	 * caches iff the content comes from the network or a hard disk, does not cache
	 * iff it is in memory already).
	 *
	 * @param relativePath relative to the project root.
	 * @return an empty string if no file at the corresponding path (including if it
	 *         is a directory).
	 * @throws GradingException wrapping, for example, an IOException while fetching
	 *                          the content.
	 */
	public String getContent(Path relativePath) throws GradingException;

	boolean noneMatch(Predicate<? super String> predicate);

	boolean existsAndNoneMatch(Predicate<? super String> predicate);

	boolean existsAndAllMatch(Predicate<? super String> predicate);

	boolean anyMatch(Predicate<? super String> predicate);

	ImmutableSet<FileContent> asFileContents();

	/**
	 * Uses and populates the cache used by {@link #getContent(Path)} if this method
	 * has to load content. The method loads content only when the given predicate
	 * calls {@link FileContent#getContent()}.
	 */
	FilesSource filter(Predicate<FileContent> predicate);

	FilesSource filterOnContent(Predicate<? super String> predicate);

	ImmutableMap<Path, String> getContents();

	FilesSource filterOnPath(Predicate<Path> predicate);

}
