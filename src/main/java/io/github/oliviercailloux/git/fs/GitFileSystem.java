package io.github.oliviercailloux.git.fs;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

import org.eclipse.jgit.lib.ObjectId;

import com.google.common.collect.ImmutableSet;
import com.google.common.graph.ImmutableGraph;

public abstract class GitFileSystem extends FileSystem {
	protected GitFileSystem() {
	}

	/**
	 * <p>
	 * Converts an absolute git path string, or a sequence of strings that when
	 * joined form an absolute git path string, to an absolute {@code GitPath}.
	 * </p>
	 * <p>
	 * If {@code more} does not specify any elements then the value of the
	 * {@code first} parameter is the path string to convert.
	 * </p>
	 * <p>
	 * If {@code more} specifies one or more elements then each non-empty string,
	 * including {@code first}, is considered to be a sequence of name elements (see
	 * {@link Path}) and is joined to form a path string using <code>/</code> as
	 * separator. If {@code first} does not end with <code>//</code> (but ends with
	 * <code>/</code>, as required), and if {@code more} does not start with
	 * <code>/</code>, then a <code>/</code> is added so that there will be two
	 * slashes joining {@code first} to {@code more}.
	 * </p>
	 * <p>
	 * For example, if {@code getAbsolutePath("/refs/heads/main/","foo","bar")} is
	 * invoked, then the path string {@code "/refs/heads/main//foo/bar"} is
	 * converted to a {@code Path}.
	 * </p>
	 * <p>
	 * No check is performed to ensure that the path refers to an existing git
	 * object in this git file system.
	 * </p>
	 *
	 * @param first the string form of the root component, possibly followed by
	 *              other path segments. Must start with <tt>/refs/</tt> or
	 *              <tt>/heads/</tt> or <tt>/tags/</tt> or be a slash followed by a
	 *              40-characters long sha-1; must contain at most once
	 *              <code>//</code>; if does not contain <code>//</code>, must end
	 *              with <code>/</code>.
	 * @param more  may start with <code>/</code>.
	 * @return an absolute git path.
	 * @throws InvalidPathException if {@code first} does not contain a syntaxically
	 *                              valid root component
	 * @see GitPath
	 */
	public abstract GitPath getAbsolutePath(String first, String... more) throws InvalidPathException;

	/**
	 * Returns a git path referring to a commit designated by its id. No check is
	 * performed to ensure that the commit exists.
	 *
	 * @param commitId     the commit to refer to
	 * @param internalPath may start with a slash.
	 * @return an absolute path
	 * @see GitPath
	 */
	public abstract GitPath getAbsolutePath(ObjectId commitId, String... internalPath);

	/**
	 * Returns a git path referring to a commit designated by its id. No check is
	 * performed to ensure that the commit exists.
	 *
	 * @param rootStringForm the string form of the root component. Must start with
	 *                       <tt>/refs/</tt> or <tt>/heads/</tt> or <tt>/tags/</tt>
	 *                       or be a 40-characters long sha-1 surrounded by slash
	 *                       characters; must end with <tt>/</tt>; may not contain
	 *                       <tt>//</tt> nor <tt>\</tt>.
	 * @return a git path root
	 * @throws InvalidPathException if {@code rootStringForm} does not contain a
	 *                              syntaxically valid root component
	 * @see GitPathRoot
	 */
	public abstract GitPathRoot getPathRoot(String rootStringForm) throws InvalidPathException;

	/**
	 * Returns a git path referring to a commit designated by its id. No check is
	 * performed to ensure that the commit exists.
	 *
	 * @param commitId the commit to refer to
	 * @return an git path root
	 * @see GitPathRoot
	 */
	public abstract GitPathRoot getPathRoot(ObjectId commitId);

	/**
	 * <p>
	 * Converts a relative git path string, or a sequence of strings that when
	 * joined form a relative git path string, to a relative {@code GitPath}.
	 * </p>
	 * <p>
	 * Each non-empty string in {@code names} is considered to be a sequence of name
	 * elements (see {@link Path}) and is joined to form a path string using
	 * <code>/</code> as separator.
	 * </p>
	 * <p>
	 * For example, if {@code getRelativePath("foo","bar")} is invoked, then the
	 * path string {@code "foo/bar"} is converted to a {@code Path}.
	 * </p>
	 * <p>
	 * An <em>empty</em> path is returned iff names contain only empty strings. It
	 * then implicitly refers to the main branch of this git file system.
	 * </p>
	 * <p>
	 * No check is performed to ensure that the path refers to an existing git
	 * object in this git file system.
	 * </p>
	 *
	 * @param names the internal path; its first element (if any) may not start with
	 *              <code>/</code>.
	 * @return a relative git path.
	 * @throws InvalidPathException if the first non-empty string in {@code names}
	 *                              start with <code>/</code>.
	 * @see GitPath
	 */
	public abstract GitPath getRelativePath(String... names) throws InvalidPathException;

	/**
	 * Retrieve the set of all commits of this repository reachable from some ref.
	 * This is equivalent to calling {@link #getRootDirectories()}, but with a more
	 * precise type.
	 *
	 * @return absolute path roots, all referring to commit ids (no ref).
	 * @throws UncheckedIOException if an I/O error occurs (using an Unchecked
	 *                              variant to mimic the behavior of
	 *                              {@link #getRootDirectories()})
	 */
	public abstract ImmutableGraph<GitPathRoot> getCommitsGraph() throws UncheckedIOException;

	/**
	 * Returns a set containing one git path root for each git ref (of the form
	 * <tt>/refs/…</tt>) contained in this git file system. This does not consider
	 * HEAD or other special references, but considers both branches and tags.
	 *
	 * @return git path roots referencing git refs (not commit ids).
	 *
	 * @throws IOException if an I/O error occurs
	 */
	public abstract ImmutableSet<GitPathRoot> getRefs() throws IOException;

	/**
	 * <p>
	 * Returns a gitjfs URI that identifies this git file system, and this specific
	 * git file system instance while it is open.
	 * </p>
	 * <p>
	 * While this instance is open, giving the returned URI to
	 * {@link GitFileSystemProvider#getFileSystem(URI)} will return this file system
	 * instance; giving it to {@link GitFileSystemProvider#getPath(URI)} will return
	 * the default path associated to this file system.
	 * </p>
	 *
	 * @return the URI that identifies this file system.
	 */
	public abstract URI toUri();

	@Override
	public abstract void close();

}
