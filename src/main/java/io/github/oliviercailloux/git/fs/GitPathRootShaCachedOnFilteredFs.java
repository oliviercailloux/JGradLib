package io.github.oliviercailloux.git.fs;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Verify.verify;

import io.github.oliviercailloux.gitjfs.ForwardingGitPath;
import io.github.oliviercailloux.gitjfs.ForwardingGitPathRootShaCached;
import io.github.oliviercailloux.gitjfs.GitPath;
import io.github.oliviercailloux.gitjfs.GitPathRoot;
import io.github.oliviercailloux.gitjfs.GitPathRootShaCached;
import java.io.IOException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Similar to a {@link GitPathRootShaCached} (which it wraps and delegates to)
 * except linked to a filteredFs.
 */
final class GitPathRootShaCachedOnFilteredFs extends ForwardingGitPathRootShaCached
		implements IGitPathRootOnFilteredFs {

	static GitPathRootShaCachedOnFilteredFs wrap(GitFilteringFs fs, GitPathRootShaCached delegate) {
		return new GitPathRootShaCachedOnFilteredFs(fs, delegate);
	}

	private final GitFilteringFs fs;
	private final GitPathRootShaCached delegate;

	private GitPathRootShaCachedOnFilteredFs(GitFilteringFs fs, GitPathRootShaCached delegate) {
		this.fs = checkNotNull(fs);
		this.delegate = checkNotNull(delegate);
	}

	@Override
	public GitFilteringFs getFileSystem() {
		return fs;
	}

	@Override
	public GitPathRootShaCached delegate() {
		return delegate;
	}

	@Override
	public boolean equals(Object o2) {
		return ForwardingGitPath.defaultEquals(this, o2);
	}

	@Override
	public int hashCode() {
		return Objects.hash(fs, toString());
	}

	@Override
	public String toString() {
		return delegate().toString();
	}

	@Override
	public GitPathRoot toAbsolutePath() {
		verify(delegate.toAbsolutePath().equals(delegate));
		return this;
	}

	@Override
	public GitPathRoot getRoot() {
		verify(delegate.getRoot().equals(delegate));
		return this;
	}

	@Override
	public GitPathOnFilteredFs getFileName() {
		return GitPathOnFilteredFs.wrap(fs, delegate.getFileName());
	}

	@Override
	@Deprecated
	public GitPathRoot getParent() {
		verify(delegate.getParent() == null);
		return null;
	}

	@Override
	public GitPath getName(int index) {
		return GitPathOnFilteredFs.wrap(fs, delegate.getName(index));
	}

	@Override
	public GitPath subpath(int beginIndex, int endIndex) {
		return GitPathOnFilteredFs.wrap(fs, delegate.subpath(beginIndex, endIndex));
	}

	@Override
	public GitPath normalize() {
		return GitPathOnFilteredFs.wrap(fs, delegate.normalize());
	}

	@Override
	public GitPath resolve(Path other) {
		return GitPathOnFilteredFs.wrap(fs, delegate.resolve(other));
	}

	@Override
	public GitPath relativize(Path other) {
		return GitPathOnFilteredFs.wrap(fs, delegate.relativize(other));
	}

	@Override
	public GitPath toRealPath(LinkOption... options) throws IOException {
		return GitPathOnFilteredFs.wrap(fs, delegate.toRealPath(options));
	}

}
