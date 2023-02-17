package io.github.oliviercailloux.git.fs;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.github.oliviercailloux.jaris.exceptions.Unchecker.IO_UNCHECKER;

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
	private ImmutableGraph<GitPathRootSha> graph;

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
	public GitPath getAbsolutePath(String first, String... more) throws InvalidPathException {
		return GitPathOnFilteredFs.wrap(this, super.getAbsolutePath(first, more));
	}

	@Override
	public GitPath getAbsolutePath(ObjectId commitId, String internalPath1, String... internalPath) {
		return GitPathOnFilteredFs.wrap(this, super.getAbsolutePath(commitId, internalPath1, internalPath));
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
	public GitPathRoot getPathRoot(String rootStringForm) throws InvalidPathException {
		return GitPathRootOnFilteredFs.wrap(this, super.getPathRoot(rootStringForm));
	}

	@Override
	public GitPath getRelativePath(String... names) throws InvalidPathException {
		return GitPathOnFilteredFs.wrap(this, super.getRelativePath(names));
	}

	@Override
	public ImmutableGraph<GitPathRootSha> getCommitsGraph() throws IOException {
		if (graph == null) {
			final ImmutableGraph<GitPathRootSha> wholeGraph = super.getCommitsGraph();
			final MutableGraph<GitPathRootShaCached> wholeCached = GraphUtils.transform(wholeGraph,
					p -> p.toShaCached());
			final MutableGraph<GitPathRootShaCached> closed = GraphUtils.transitiveClosure(wholeCached);
			final MutableGraph<GitPathRootShaCached> filtered = Graphs.inducedSubgraph(closed,
					CheckedStream.<GitPathRootShaCached, IOException>wrapping(closed.nodes().stream())
							.filter(p -> filter.test(p.getCommit())).collect(ImmutableSet.toImmutableSet()));
			final org.jgrapht.Graph<GitPathRootShaCached, EndpointPair<GitPathRootShaCached>> adapted = new MutableGraphAdapter<>(
					filtered);
			TransitiveReduction.INSTANCE.reduce(adapted);
			final MutableGraph<GitPathRootSha> wrapped = GraphUtils.transform(filtered,
					p -> GitPathRootShaCachedOnFilteredFs.wrap(this, p));
			graph = ImmutableGraph.copyOf(wrapped);
		}
		return graph;
	}

	@Override
	public ImmutableSet<GitPathRootRef> getRefs() throws IOException {
		final ImmutableSet<GitPathRootRef> refsWhole = super.getRefs();
//		return CheckedStream.wrapping(refsWhole.stream()).map(GitPathRoot::toShaCached).map(GitPathRootShaCached::getCommit).filter(filter::test).collect(ImmutableSet.toImmutableSet());
		return CheckedStream.<GitPathRootRef, IOException>wrapping(refsWhole.stream())
				.filter(p -> filter.test(p.getCommit())).map(p -> GitPathRootRefOnFilteredFs.wrap(this, p))
				.collect(ImmutableSet.toImmutableSet());
	}

	@Override
	public ImmutableSet<Path> getRootDirectories() {
		final ImmutableGraph<GitPathRootSha> commitsGraph = IO_UNCHECKER.getUsing(this::getCommitsGraph);
		return ImmutableSet.copyOf(commitsGraph.nodes());
	}

	@Override
	public PathMatcher getPathMatcher(String syntaxAndPattern) {
		throw new UnsupportedOperationException();
	}

	@Override
	public ImmutableSet<DiffEntry> diff(GitPathRoot first, GitPathRoot second)
			throws IOException, NoSuchFileException {
		if (!filter.test(first.getCommit())) {
			throw new NoSuchFileException(first.toString());
		}
		if (!filter.test(second.getCommit())) {
			throw new NoSuchFileException(second.toString());
		}

		return super.diff(first, second);
	}

}
