package io.github.oliviercailloux.git.fs;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Verify.verify;
import static io.github.oliviercailloux.jaris.exceptions.Unchecker.IO_UNCHECKER;

import com.google.common.base.VerifyException;
import com.google.common.collect.ImmutableSet;
import com.google.common.graph.EndpointPair;
import com.google.common.graph.Graphs;
import com.google.common.graph.ImmutableGraph;
import com.google.common.graph.MutableGraph;
import io.github.oliviercailloux.gitjfs.Commit;
import io.github.oliviercailloux.gitjfs.ForwardingGitFileSystem;
import io.github.oliviercailloux.gitjfs.GitFileSystem;
import io.github.oliviercailloux.gitjfs.GitPath;
import io.github.oliviercailloux.gitjfs.GitPathRoot;
import io.github.oliviercailloux.gitjfs.GitPathRootRef;
import io.github.oliviercailloux.gitjfs.GitPathRootSha;
import io.github.oliviercailloux.gitjfs.GitPathRootShaCached;
import io.github.oliviercailloux.jaris.collections.GraphUtils;
import io.github.oliviercailloux.jaris.exceptions.CheckedStream;
import io.github.oliviercailloux.jaris.throwing.TPredicate;
import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.ObjectId;
import org.jgrapht.alg.TransitiveReduction;
import org.jgrapht.graph.guava.MutableGraphAdapter;

public class GitFilteringFs extends ForwardingGitFileSystem {

	/**
	 * Returns a file system that only shows paths that match the given filter.
	 *
	 * @param delegate the original file system
	 * @param filter   indicates which elements should be kept
	 * @return a filtering file system
	 */
	public static GitFilteringFs filter(GitFileSystem delegate, TPredicate<Commit, IOException> filter) {
		return new GitFilteringFs(delegate, filter);
	}

	private final GitFileSystem delegate;
	private final TPredicate<Commit, IOException> filter;
	private ImmutableGraph<GitPathRootShaCached> graph;

	private GitFilteringFs(GitFileSystem delegate, TPredicate<Commit, IOException> filter) {
		this.delegate = checkNotNull(delegate);
		this.filter = checkNotNull(filter);
		graph = null;
	}

	@Override
	protected GitFileSystem delegate() {
		return delegate;
	}

	@Override
	public GitPath getPath(String first, String... more) {
		return GitPathOnFilteredFs.wrap(this, super.getPath(first, more));
	}

	@Override
	public GitPathRoot getPathRoot(String rootStringForm) throws InvalidPathException {
		return GitPathRootOnFilteredFs.wrap(this, super.getPathRoot(rootStringForm));
	}

	@Override
	public GitPathRootSha getPathRoot(ObjectId commitId) {
		return GitPathRootShaOnFilteredFs.wrap(this, super.getPathRoot(commitId));
	}

	@Override
	public GitPathRootRef getPathRootRef(String rootStringForm) throws InvalidPathException {
		return GitPathRootRefOnFilteredFs.wrap(this, super.getPathRootRef(rootStringForm));
	}

	@Override
	public GitPath getAbsolutePath(String first, String... more) throws InvalidPathException {
		return GitPathOnFilteredFs.wrap(this, super.getAbsolutePath(first, more));
	}

	@Override
	public GitPath getAbsolutePath(ObjectId commitId, String internalPath1, String... internalPath) {
		return GitPathOnFilteredFs.wrap(this, super.getAbsolutePath(commitId, internalPath1, internalPath));
	}

	@Override
	public GitPath getRelativePath(String... names) throws InvalidPathException {
		return GitPathOnFilteredFs.wrap(this, super.getRelativePath(names));
	}

	@Override
	public ImmutableGraph<GitPathRootShaCached> graph() throws IOException {
		if (graph == null) {
			final ImmutableGraph<GitPathRootShaCached> wholeGraph = super.graph();
			final MutableGraph<GitPathRootShaCached> closed = GraphUtils.transitiveClosure(wholeGraph);
			final MutableGraph<GitPathRootShaCached> filtered = Graphs.inducedSubgraph(closed,
					CheckedStream.<GitPathRootShaCached, IOException>wrapping(closed.nodes().stream())
							.filter(p -> filter.test(p.getCommit())).collect(ImmutableSet.toImmutableSet()));
			final org.jgrapht.Graph<GitPathRootShaCached, EndpointPair<GitPathRootShaCached>> adapted = new MutableGraphAdapter<>(
					filtered);
			TransitiveReduction.INSTANCE.reduce(adapted);
			final MutableGraph<GitPathRootShaCached> wrapped = GraphUtils.transform(filtered,
					p -> GitPathRootShaCachedOnFilteredFs.wrap(this, p));
			graph = ImmutableGraph.copyOf(wrapped);
		}
		return graph;
	}

	@Override
	public ImmutableSet<GitPathRootRef> refs() throws IOException {
		final ImmutableSet<GitPathRootRef> refsWhole = super.refs();
//		return CheckedStream.wrapping(refsWhole.stream()).map(GitPathRoot::toShaCached).map(GitPathRootShaCached::getCommit).filter(filter::test).collect(ImmutableSet.toImmutableSet());
		return CheckedStream.<GitPathRootRef, IOException>wrapping(refsWhole.stream())
				.filter(p -> filter.test(p.getCommit())).map(p -> GitPathRootRefOnFilteredFs.wrap(this, p))
				.collect(ImmutableSet.toImmutableSet());
	}

	@Override
	public ImmutableSet<DiffEntry> diff(GitPathRoot first, GitPathRoot second) throws IOException, NoSuchFileException {
		checkArgument(this.equals(first.getFileSystem()));
		checkArgument(this.equals(second.getFileSystem()));
		verify(first.getFileSystem().equals(second.getFileSystem()));

		if (!filter.test(first.getCommit())) {
			throw new NoSuchFileException(first.toString());
		}
		if (!filter.test(second.getCommit())) {
			throw new NoSuchFileException(second.toString());
		}

		return super.diff(underlyingPathRoot(first), underlyingPathRoot(second));
	}

	@SuppressWarnings("unused")
	private GitPath underlyingPath(GitPath filtered) {
		checkArgument(filtered.getFileSystem().equals(this));
		if (filtered instanceof GitPathOnFilteredFs g) {
			return g.delegate();
		}
		if (filtered instanceof GitPathRootOnFilteredFs g) {
			return g.delegate();
		}
		if (filtered instanceof GitPathRootRefOnFilteredFs g) {
			return g.delegate();
		}
		if (filtered instanceof GitPathRootShaOnFilteredFs g) {
			return g.delegate();
		}
		if (filtered instanceof GitPathRootShaCachedOnFilteredFs g) {
			return g.delegate();
		}
		if (filtered instanceof GitPathRootOnFilteredFs g) {
			return g.delegate();
		}
		throw new VerifyException();
	}

	private GitPathRoot underlyingPathRoot(GitPathRoot filtered) {
		checkArgument(filtered.getFileSystem().equals(this));
		if (filtered instanceof GitPathRootOnFilteredFs g) {
			return g.delegate();
		}
		if (filtered instanceof GitPathRootRefOnFilteredFs g) {
			return g.delegate();
		}
		if (filtered instanceof GitPathRootShaOnFilteredFs g) {
			return g.delegate();
		}
		if (filtered instanceof GitPathRootShaCachedOnFilteredFs g) {
			return g.delegate();
		}
		if (filtered instanceof GitPathRootOnFilteredFs g) {
			return g.delegate();
		}
		throw new VerifyException();
	}

	@Override
	public ImmutableSet<Path> getRootDirectories() {
		final ImmutableGraph<GitPathRootShaCached> commitsGraph = IO_UNCHECKER.getUsing(this::graph);
		return ImmutableSet.copyOf(commitsGraph.nodes());
	}

	@Override
	public PathMatcher getPathMatcher(String syntaxAndPattern) {
		throw new UnsupportedOperationException();
	}

}
