package io.github.oliviercailloux.git.fs;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileStore;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.WatchService;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.util.Set;
import java.util.function.Predicate;

import org.eclipse.jgit.lib.ObjectId;

import com.google.common.collect.ImmutableSet;
import com.google.common.graph.ImmutableGraph;

class GitFilteredFileSystem extends GitFileSystem {
	public static GitFilteredFileSystem given(GitAbstractFileSystem delegate, Predicate<ObjectId> kept) {
		return new GitFilteredFileSystem(delegate, kept);
	}

	private final GitAbstractFileSystem delegate;
	private final Predicate<ObjectId> kept;

	private GitFilteredFileSystem(GitAbstractFileSystem delegate, Predicate<ObjectId> kept) {
		this.delegate = delegate;
		this.kept = kept;
	}

	@Override
	public void close() {
		delegate.close();
	}

	@Override
	public GitPath getPath(String first, String... more) {
		return delegate.getPath(first, more);
	}

	@Override
	public GitPath getAbsolutePath(String first, String... more) throws InvalidPathException {
		return delegate.getAbsolutePath(first, more);
	}

	@Override
	public GitPath getAbsolutePath(ObjectId commitId, String... internalPath) {
		return delegate.getAbsolutePath(commitId, internalPath);
	}

	@Override
	public GitPathRoot getPathRoot(String rootStringForm) throws InvalidPathException {
		return delegate.getPathRoot(rootStringForm);
	}

	@Override
	public GitPathRoot getPathRoot(ObjectId commitId) {
		return delegate.getPathRoot(commitId);
	}

	@Override
	public GitPath getRelativePath(String... names) throws InvalidPathException {
		return delegate.getRelativePath(names);
	}

	@Override
	public Iterable<FileStore> getFileStores() {
		return delegate.getFileStores();
	}

	@Override
	public ImmutableSet<Path> getRootDirectories() throws UncheckedIOException {
		return delegate.getRootDirectories();
	}

	@Override
	public ImmutableGraph<GitPathRoot> getCommitsGraph() throws UncheckedIOException {
		return delegate.getCommitsGraph();
	}

	@Override
	public ImmutableSet<GitPathRoot> getRefs() throws IOException {
		return delegate.getRefs();
	}

	@Override
	public PathMatcher getPathMatcher(String syntaxAndPattern) {
		return delegate.getPathMatcher(syntaxAndPattern);
	}

	@Override
	public String getSeparator() {
		return delegate.getSeparator();
	}

	@Override
	public UserPrincipalLookupService getUserPrincipalLookupService() {
		return delegate.getUserPrincipalLookupService();
	}

	@Override
	public boolean isOpen() {
		return delegate.isOpen();
	}

	@Override
	public boolean isReadOnly() {
		return delegate.isReadOnly();
	}

	@Override
	public WatchService newWatchService() throws IOException {
		return delegate.newWatchService();
	}

	@Override
	public GitFileSystemProvider provider() {
		return delegate.provider();
	}

	@Override
	public Set<String> supportedFileAttributeViews() {
		return delegate.supportedFileAttributeViews();
	}
}
