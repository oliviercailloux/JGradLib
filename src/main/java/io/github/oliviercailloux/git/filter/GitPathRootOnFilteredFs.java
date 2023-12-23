package io.github.oliviercailloux.git.filter;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Verify.verify;

import com.google.common.collect.ImmutableSet;
import io.github.oliviercailloux.gitjfs.Commit;
import io.github.oliviercailloux.gitjfs.ForwardingGitPath;
import io.github.oliviercailloux.gitjfs.ForwardingGitPathRoot;
import io.github.oliviercailloux.gitjfs.GitPath;
import io.github.oliviercailloux.gitjfs.GitPathRoot;
import io.github.oliviercailloux.gitjfs.GitPathRootSha;
import io.github.oliviercailloux.gitjfs.GitPathRootShaCached;
import java.io.IOException;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;
import org.eclipse.jgit.lib.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Similar to a GitPathRoot (which it wraps and delegates to) except linked to a
 * filteredFs.
 */
final class GitPathRootOnFilteredFs extends ForwardingGitPathRoot implements IGitPathRootOnFilteredFs {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GitPathRootOnFilteredFs.class);

	static GitPathRootOnFilteredFs wrap(GitFilteringFs fs, GitPathRoot delegate) {
		return new GitPathRootOnFilteredFs(fs, delegate);
	}

	private final GitFilteringFs fs;
	private final GitPathRoot delegate;

	private GitPathRootOnFilteredFs(GitFilteringFs fs, GitPathRoot delegate) {
		this.fs = checkNotNull(fs);
		this.delegate = checkNotNull(delegate);
	}

	@Override
	public GitFilteringFs getFileSystem() {
		return fs;
	}

	@Override
	public GitPathRoot delegate() {
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

	@Deprecated
	@Override
	public GitPathRoot toAbsolutePath() {
		verify(delegate.toAbsolutePath().equals(delegate));
		return this;
	}

	@Deprecated
	@Override
	public GitPathRoot getRoot() {
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

	@Override
	public Commit getCommit() throws IOException, NoSuchFileException {
		final GitPathRootShaCached cached = this.toShaCached();
		final Commit underlying = cached.getCommit();
		final ImmutableSet<ObjectId> underlyingParents = ImmutableSet.copyOf(underlying.parents());
		final Set<GitPathRootShaCached> filteredParents = fs.graph().predecessors(cached);
		final ImmutableSet<ObjectId> filteredParentIds = filteredParents.stream().map(GitPathRootSha::getStaticCommitId)
				.collect(ImmutableSet.toImmutableSet());
		if (!underlyingParents.equals(filteredParentIds)) {
			LOGGER.warn("Commit’s filtered parents {} should replace the returned ones {}.", filteredParentIds,
					underlyingParents);
		}
		return underlying;
	}

}
