package io.github.oliviercailloux.git.filter;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Verify.verify;

import io.github.oliviercailloux.gitjfs.ForwardingGitPath;
import io.github.oliviercailloux.gitjfs.ForwardingGitPathRootSha;
import io.github.oliviercailloux.gitjfs.GitPathRootSha;
import java.io.IOException;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Similar to a {@link GitPathRootSha} (which it wraps and delegates to) except
 * linked to a filteredFs.
 */
final class GitPathRootShaOnFilteredFs extends ForwardingGitPathRootSha implements IGitPathRootOnFilteredFs {

	static GitPathRootShaOnFilteredFs wrap(GitFilteringFs fs, GitPathRootSha delegate) {
		return new GitPathRootShaOnFilteredFs(fs, delegate);
	}

	private final GitFilteringFs fs;
	private final GitPathRootSha delegate;

	private GitPathRootShaOnFilteredFs(GitFilteringFs fs, GitPathRootSha delegate) {
		this.fs = checkNotNull(fs);
		this.delegate = checkNotNull(delegate);
	}

	@Override
	public GitPathRootShaCachedOnFilteredFs toShaCached() throws IOException, NoSuchFileException {
		return GitPathRootShaCachedOnFilteredFs.wrap(getFileSystem(), super.toShaCached());
	}

	@Override
	public GitFilteringFs getFileSystem() {
		return fs;
	}

	@Override
	public GitPathRootSha delegate() {
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
	@Deprecated
	public GitPathRootShaOnFilteredFs toAbsolutePath() {
		verify(delegate.toAbsolutePath().equals(delegate));
		return this;
	}

	@Override
	@Deprecated
	public GitPathRootShaOnFilteredFs getRoot() {
		verify(delegate.getRoot().equals(delegate));
		return this;
	}

	@Override
	@Deprecated
	public GitPathRootOnFilteredFs getFileName() {
		verify(delegate.getFileName() == null);
		return null;
	}

	@Override
	@Deprecated
	public GitPathRootShaOnFilteredFs getParent() {
		verify(delegate.getParent() == null);
		return null;
	}

	@Override
	public GitPathOnFilteredFs getName(int index) {
		return GitPathOnFilteredFs.wrap(fs, delegate.getName(index));
	}

	@Override
	public GitPathOnFilteredFs subpath(int beginIndex, int endIndex) {
		return GitPathOnFilteredFs.wrap(fs, delegate.subpath(beginIndex, endIndex));
	}

	@Override
	public GitPathOnFilteredFs normalize() {
		return GitPathOnFilteredFs.wrap(fs, delegate.normalize());
	}

	@Override
	public GitPathOnFilteredFs resolve(Path other) {
		return GitPathOnFilteredFs.wrap(fs, delegate.resolve(other));
	}

	@Override
	public GitPathOnFilteredFs relativize(Path other) {
		return GitPathOnFilteredFs.wrap(fs, delegate.relativize(other));
	}

	@Override
	public GitPathOnFilteredFs toRealPath(LinkOption... options) throws IOException {
		return GitPathOnFilteredFs.wrap(fs, delegate.toRealPath(options));
	}

}
