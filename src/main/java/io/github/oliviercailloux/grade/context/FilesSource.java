package io.github.oliviercailloux.grade.context;

import java.nio.file.Path;
import java.util.Map;
import java.util.function.Predicate;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import io.github.oliviercailloux.git.FileContent;
import io.github.oliviercailloux.grade.GradingException;
import io.github.oliviercailloux.grade.contexters.FilesSourceImpl;
import io.github.oliviercailloux.grade.contexters.FullContextInitializer;

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
 * GitFileSystem has a git repo URI and a tmpFolder. It assumes it’s cloned
 * already, does not deal with updates, and reads from git history.
 *
 * Now checking out the default commit to a given folder is done as follows:
 * copyHierarchy(GitPath commit, Path outputFolder). Note that it can also be
 * used to “check out” a sub-folder rather than the whole workspace. (Useful for
 * running Maven on the folder, for example.)
 *
 * GitPath is bound to a commit, master by default, to a GFS, and delegates to a
 * relative path that stores the directory hierarchy and file name.
 *
 * A GFSProvider (subclass of FSProvider, currently GitContext) registers the
 * URI scheme autograde, gives GFS instances given a git repo URI and optionally
 * a tmp folder (otherwise, uses /tmp unless the URI has a file git-cloneable
 * equivalent). It clones using clone --no-checkout (just needs git history), or
 * checks that the remote there is the git-cloneable equivalent and updates,
 * before creating the GFS instance. GFSProvider has the logic for reading files
 * (using my client based on jgit). A FilteredFileSystem has internally a set of
 * matching paths, is created from a given file system, and convertible to a set
 * of FileContents, as is FilesSource currently. (Think about this more, perhaps
 * interface with Files.newDirectoryStream. Consider using Predicate<Path> to
 * filter in a way compatible with the Java API, and creating a Predicate<Path>
 * from a Predicate<FileContents> by transforming it with a static method.)
 *
 * I need a way of obtaining the commit history from the tmp folder containing
 * the git history. In order to determine which commit I want, after having
 * called GFSProvider and obtained a GitFS.
 *
 * A git repo URI has scheme autograde (mandatory for being an acceptable
 * parameter of GFSProvider), and has an equivalent git-cloneable URI. It has
 * syntax
 * autograde://host.xz[:port]/path/to/repo.git[/]?git-scheme=access-scheme,
 * where access-scheme is ssh, git, http or https, and with the git-cloneable
 * equivalent being access-scheme://host.xz[:port]/path/to/repo.git[/], or
 * autograde://user@host.xz/path/to/repo.git[/][?git-scheme=ssh], with
 * git-clonable equivalent ssh://user@host.xz/path/to/repo.git[/], or
 * autograde:///path/to/repo.git[/][?git-scheme=file], with git-cloneable
 * equivalent file:///path/to/repo.git[/]. (The git clone man page authorizes
 * the trailing slash while GitHub forbids it.) When given the latter form with
 * no optional tmp value, the GFSProvider does not clone or update and reads
 * directly from the given folder. Assume that even under Windows, the
 * file-URI-like paths (slash-separated, percent-encoded) are used (conversion
 * is easy anyway). In general, for
 * https://docs.oracle.com/en/java/javase/12/docs/api/java.base/java/nio/file/spi/FileSystemProvider.html#getPath(java.net.URI),
 * a URI may be more complete and identify a commit (possibly master), directory
 * and file. (How to do this in general?) A URI represents a resource (which
 * possibly can change, as with resources in the internet).
 *
 * I suppose I should use an in-memory unix-like path as a delegate into
 * GitPath, so as to be system independent and have URI-like paths.
 *
 * To proceed stepwise, I could start with replacing current uses of Path by
 * GitPath. I can throw UOE to getFileSystem. No, rather use systematically
 * paths relative to the project root?
 *
 * Do not cache file content in memory: read it as required. Caching is not
 * useful: either you fetch directly from GitHub network to in-memory FS, or it
 * doesn’t fit memory and you must put on disk anyway. I’ll never have to fetch
 * file by file. (Except perhaps reading several times the same file? But this
 * is a very special and simple case probably better handled with particular
 * code dealing just with that file.)
 *
 * The Grader creates a projectRoot path (for example, a GitPath that represents
 * the commit master), and works relative to this path.
 *
 * Remaining questions: who determines last commit to consider? Do I need a
 * GitHubRepoReader which clones or updates and has the history and timestamps?
 * Note that currently, FullCInit instance is a GFSProvider that first clones
 * the repo, analyzes its history, determines a default commit, and, given
 * commits, gives GFS instances. Is my Client used anywhere else? Grader should
 * never have to use it explicitly, I suppose. Also: path-relative-to-maven-pom
 * (e.g., src/main/java or pom.xml). => fileMatch("pom.xml", "blah") =>
 * MavenRelativePath.get("pom.xml") VS
 * thisReader.getMavenPath.relativize("pom.xml").
 *
 * Concretely, I could refactor {@link FullContextInitializer} into: clones
 * using a GFSProvider, obtaining a GitFS; analyzes history and determines the
 * desired commit; and gets a base path using that commit and the GitFS. (Think
 * about a better separation of concerns?)
 *
 * @author Olivier Cailloux
 *
 */
public interface FilesSource {
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
