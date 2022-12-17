package io.github.oliviercailloux.git.fs;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Verify.verify;

import io.github.oliviercailloux.gitjfs.impl.GitPathRootImpl;
import java.io.IOException;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/**
 * Similar to a GitPathRoot (which it wraps and delegates to) except linked to a
 * filteredFs.
 */
public class GitPathRootOnFilteredFs extends PathWrapper implements Path {

	static GitPathRootOnFilteredFs wrap(GitFilteringFs fs, GitPathRootImpl delegate) {
		return new GitPathRootOnFilteredFs(fs, delegate);
	}

	private final GitFilteringFs fs;
	private final GitPathRootImpl delegate;

	private GitPathRootOnFilteredFs(GitFilteringFs fs, GitPathRootImpl delegate) {
		this.fs = checkNotNull(fs);
		this.delegate = checkNotNull(delegate);
	}

	@Override
	public GitFilteringFs getFileSystem() {
		return fs;
	}

	@Override
	protected GitPathRootImpl delegate() {
		return delegate;
	}

	@Override
	public GitPathRootOnFilteredFs toAbsolutePath() {
		verify(delegate.toAbsolutePath().equals(delegate));
		return this;
	}

	@Override
	public GitPathRootOnFilteredFs getRoot() {
		verify(delegate.getRoot().equals(delegate));
		return this;
	}

	@Override
	public GitPathOnFilteredFs getFileName() {
		return GitPathOnFilteredFs.wrap(fs, delegate.getFileName());
	}

	@Override
	public GitPathRootOnFilteredFs getParent() {
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
