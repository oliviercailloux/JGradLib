package io.github.oliviercailloux.grade.context;

import java.nio.file.Path;
import java.util.function.Predicate;

import io.github.oliviercailloux.git.FileContent;
import io.github.oliviercailloux.grade.GradingException;

/**
 * An abstraction over file hierarchies, which can be provided by the network (a
 * GitHub repository), a cache of file contents in memory (previously fetched
 * from GitHub), a git repository on the local hard disk, …
 *
 * The key for accessing a file is a Path, and those paths are always relative
 * to the project root.
 *
 * TODO the key should be a URL or some other means equivalent to a Path but
 * file-system independent, so that it can equally represent a relative path on
 * a web server or on a disk. Currently I suppose on Windows a path for
 * retrieving from a file system would use '\' and thus would fail if used for
 * retrieving content on the web. Although I can perhaps follow the JDK advice
 * by resolving systematically against the root path, if the root path is right
 * (using '/' on the web and '\' on a Windows file system), it should work.
 * Except that the git client uses only '/' for its path separation.
 *
 * @author Olivier Cailloux
 *
 */
public interface FilesReader {
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

	/**
	 * Uses and populates the cache used by {@link #getContent(Path)} if this method
	 * has to load content. The method loads content only when the given predicate
	 * calls {@link FileContent#getContent()}.
	 */
	public MultiContent getMultiContent(Predicate<FileContent> predicate) throws GradingException;

}
