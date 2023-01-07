package io.github.oliviercailloux.git.fs;

import static com.google.common.base.Preconditions.checkArgument;
import static io.github.oliviercailloux.jaris.exceptions.Unchecker.IO_UNCHECKER;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.graph.ImmutableGraph;
import com.google.common.graph.MutableGraph;
import io.github.oliviercailloux.git.GitHubHistory;
import io.github.oliviercailloux.gitjfs.GitFileSystem;
import io.github.oliviercailloux.gitjfs.GitPathRoot;
import io.github.oliviercailloux.gitjfs.GitPathRootSha;
import io.github.oliviercailloux.gitjfs.GitPathRootShaCached;
import io.github.oliviercailloux.jaris.collections.GraphUtils;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import org.eclipse.jgit.lib.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A history of commits. It can use the author dates, the commit dates, or dates
 * from any other source. It guarantees that every node in the graph has an
 * associated date.
 * <p>
 * An alternative design would admit partial date information (some nodes being
 * associated to no date). But this complicates use, and is really only useful,
 * probably, for push dates coming from GitHub, which are incomplete. Better,
 * for that specific use case, complete the information, as done in
 * {@link GitHubHistory}.
 */
public class GitHistorySimple {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GitHistorySimple.class);

	/**
	 * @param graph successors = children (time-based view)
	 * @param dates its keyset must contain all nodes of the graph.
	 * @throws IOException
	 */
	public static GitHistorySimple create(GitFileSystem fs, Map<ObjectId, Instant> dates) throws IOException {
		return new GitHistorySimple(fs, dates);
	}

	/**
	 * @param graph successors = children (time-based view)
	 * @param dates its keyset must contain all nodes of the graph.
	 * @throws IOException
	 */
	public static GitHistorySimple usingCommitterDates(GitFileSystem fs) throws IOException {
		final ImmutableGraph<GitPathRootSha> graphOfPaths = fs.getCommitsGraph();

		final Function<GitPathRoot, Instant> getDate = IO_UNCHECKER.wrapFunction();

		final ImmutableMap<ObjectId, Instant> dates = graphOfPaths.nodes().stream().collect(ImmutableMap
				.toImmutableMap(GitPathRootSha::getStaticCommitId, p -> p.getCommit().committerDate().toInstant()));

		return GitHistorySimple.create(fs, dates);
	}

	private final GitFileSystem fs;

	private final ImmutableMap<ObjectId, Instant> dates;

	private ImmutableSet<GitPathRootShaCached> roots;

	private ImmutableSet<GitPathRootShaCached> leaves;

	private final ImmutableGraph<GitPathRootShaCached> graph;

	private GitHistorySimple(GitFileSystem fs, Map<ObjectId, Instant> dates) throws IOException {
		this.fs = fs;
		final ImmutableGraph<GitPathRootSha> commitsGraph = fs.getCommitsGraph();
		final MutableGraph<GitPathRootShaCached> cached = GraphUtils.transform(commitsGraph, p -> p.toShaCached());
		graph = ImmutableGraph.copyOf(cached);
		final ImmutableSet<ObjectId> commits = graph.nodes().stream().map(p -> p.getCommit().id())
				.collect(ImmutableSet.toImmutableSet());
		this.dates = ImmutableMap.copyOf(Maps.filterKeys(dates, k -> commits.contains(k)));
		checkArgument(commits.equals(this.dates.keySet()));
		roots = null;
		leaves = null;
	}

	public GitFileSystem fs() {
		return fs;
	}

	public ImmutableGraph<GitPathRootShaCached> graph() {
		return graph;
	}

	/**
	 * The parents from which everything starts, that is, the smallest set of nodes
	 * from which all nodes are reachable by following the “successors” (children)
	 * relation; equivalently, the starting points in time of the git history, that
	 * is, the nodes that have no predecessor.
	 * <p>
	 * Usually there’s a single root, but git allows for <a href=
	 * "https://git-scm.com/docs/git-checkout#Documentation/git-checkout.txt---orphanltnewbranchgt">multiple
	 * roots</a>.
	 *
	 * @return empty iff the graph is empty.
	 */
	public ImmutableSet<GitPathRootShaCached> roots() {
		if (roots == null) {
			/**
			 * We could start from any given node and simply follow the predecessor
			 * (parents) relation, but that finds only one root.
			 */
			roots = graph.nodes().stream().filter(n -> graph.predecessors(n).isEmpty())
					.collect(ImmutableSet.toImmutableSet());
		}
		return roots;
	}

	/**
	 * @return the nodes of the {@link #graph() graph} that have no children (no
	 *         successor); equivalently, the smallest set of nodes such that
	 *         starting from any node and following the “successors” (children)
	 *         relation necessarily ends in the set.
	 *
	 * @return empty iff the graph is empty.
	 */
	public ImmutableSet<GitPathRootShaCached> leaves() {
		if (leaves == null) {
			leaves = graph.nodes().stream().filter(n -> graph.successors(n).isEmpty())
					.collect(ImmutableSet.toImmutableSet());
		}
		return leaves;
	}

	/**
	 * @throws IllegalArgumentException iff the given commit id corresponds to no
	 *                                  node of the {@link #graph() graph}.
	 */
	public Instant getTimestamp(ObjectId commitId) {
		checkArgument(dates.containsKey(commitId));
		return dates.get(commitId);
	}

	/**
	 * @return a map whose key set corresponds to the nodes of the {@link #graph()
	 *         graph}
	 */
	public ImmutableMap<ObjectId, Instant> getTimestamps() {
		return dates;
	}

	/**
	 * Returns a file system that only shows paths whose timestamp match the given
	 * filter.
	 *
	 * @param filter indicates which elements should be kept
	 * @return a filtering file system
	 */
	public GitFileSystem filterDate(Predicate<Instant> filter) {
		return GitFilteringFs.filter(fs, p -> filter.test(dates.get(p.id())));
	}

	@Override
	public boolean equals(Object o2) {
		if (!(o2 instanceof GitHistorySimple)) {
			return false;
		}
		final GitHistorySimple h2 = (GitHistorySimple) o2;
		return fs.equals(h2.fs) && dates.equals(h2.dates);
	}

	@Override
	public int hashCode() {
		return Objects.hash(fs, dates);
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this).add("Graph", graph).add("Dates", dates).toString();
	}

}
