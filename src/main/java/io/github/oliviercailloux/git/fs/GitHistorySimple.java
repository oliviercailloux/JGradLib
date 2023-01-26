package io.github.oliviercailloux.git.fs;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.graph.Graph;
import com.google.common.graph.ImmutableGraph;
import com.google.common.graph.MutableGraph;
import io.github.oliviercailloux.git.GitHubHistory;
import io.github.oliviercailloux.gitjfs.Commit;
import io.github.oliviercailloux.gitjfs.GitFileSystem;
import io.github.oliviercailloux.gitjfs.GitPathRootSha;
import io.github.oliviercailloux.gitjfs.GitPathRootShaCached;
import io.github.oliviercailloux.jaris.collections.CollectionUtils;
import io.github.oliviercailloux.jaris.collections.GraphUtils;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
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
 * <p>
 * TODO consider using only (or mostly) paths instead of object ids.
 *
 * <h1>Old doc from GitFileSystemHistory</h1>
 *
 * As a history, this is mainly and primarily a graph. It contains only id paths
 * (meaning paths that are object ids, not git refs). That is because as a graph
 * of nodes representing the parent relation, it should not represent twice the
 * same logical commit.
 * <p>
 * OR this notion should be extended with methods to represent the refs as well,
 * that would not appear in the graph.
 *
 * <p>
 * Idea: add getRefsMatching(TPredicate<GitPathRoot> refPredicate):
 * ImmutableSet<GitPathRoot>.
 *
 * static filter(Set<GitPathRoot>, TPredicate<GitPath>): ImmutableSet<GitPath>.
 * NB I can use this to filter on oids in order to implement anyMatch if nothing
 * better. But what about allMatchAndExists?
 *
 * <p>
 * OR filter only on refs, thus filter(T.P<GPR> refPredicate, TP<GP>); and
 * provide:?
 * <p>
 * Use cases.
 * <li>given branch / given file name pattern / given content pattern / exists?
 * <li>in all oids: given file name pattern, given content pattern, exists?
 * <li>all match and exists: given branch, given file name pattern=> all and
 * some (i.e., the one) have the right pattern.
 * <li>check whether all commits have a given author.
 * <li>Is there any file named "some file" in any commit, anywhere? VS is there
 * some commit containing "afile.txt" satisfying x VS is there a branch "origin"
 * with "afile.txt" satisfying x VS is there in branch "dev" a file named "some
 * file", anywhere?
 * <p>
 * To do this:
 * <li>anyMathAmongRefs(TP<GPR> refPredicate, TP<GP> filePredicate)
 * <li>anyMatch(TP<GP> filePredicate) // all roots are oids
 * <li><b>Most promising!
 * <li>And consider: filter(TP<ObjectId>) throws IOE.
 * <p>
 * Or, more general:
 * <li>getPathsAmongRefs(TP<GPR>, TP<GP>).
 * <li>getPathsAmongOids(TP<GP>).
 * <p>
 * Or, more elegant:
 * <li>getPathsAmongRefs(TP<GP>) <= special interface, if it’s a
 * CombinedPredicate, it can filter on roots as well as on files.
 * <li>Decide to use exclusively parameters of GPR rather than ObjectId, for
 * consistency: this permits to filter on commits that have zero files, for
 * example (using Files.list); and getCommitDate() should use the same param as
 * filter because we might want to filter by date.
 * <li>Remains not easy: checks whether there exists some branch in which the
 * file "somefile" satisfies some predicate. But it can be done through
 * #getRefs() and it is an edge case.
 * <li>FAILS because we want: paths in "origin" satisfying x. Can’t be done with
 * predicate on all paths: we need to let "origin/" pass, otherwise it stops
 * there; but then even if no file satisfy; we end up with a non empty set. OR
 * we let the predicate pass origin iff it contains afile satisfying x, but then
 * 1) it explores everything inside for nothing; 2) it sends back a set of paths
 * whereas we really only care about filtering roots. We really need a solution
 * with a predicate over symbolic roots.
 * <p>
 * <li>getRefsMatching(TP<GPR>): Set<GPR>
 * <li>For finding in "dev" all files named "some file": use getRefsMatching()
 * to get dev, then use a static method to find the paths.
 *
 */
public class GitHistorySimple {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GitHistorySimple.class);

	/**
	 * @param dates its keyset must contain all nodes of the graph.
	 */
	public static GitHistorySimple create(GitFileSystem fs, Map<ObjectId, Instant> dates) throws IOException {
		return new GitHistorySimple(fs, dates);
	}

	/**
	 * @param dates its keyset must contain all nodes of the graph.
	 */
	public static GitHistorySimple usingCommitterDates(GitFileSystem fs) throws IOException {
		final ImmutableGraph<GitPathRootSha> graphOfPaths = fs.getCommitsGraph();
		final Graph<Commit> graph = GraphUtils.transform(graphOfPaths, GitPathRootSha::getCommit);
		final ImmutableMap<Commit, Instant> dated = CollectionUtils.toMap(graph.nodes(),
				c -> c.committerDate().toInstant());
		final ImmutableMap<ObjectId, Instant> dates = CollectionUtils.transformKeys(dated, Commit::id);

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

	/**
	 * graph successors = children (time-based view)
	 */
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
	 * @throws IllegalArgumentException iff the given path corresponds to no node of
	 *                                  the {@link #graph() graph}.
	 */
	public Instant getTimestamp(GitPathRootShaCached path) {
		final ObjectId id = path.getCommit().id();
		return getTimestamp(id);
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
	 * @throws IOException
	 */
	public GitFileSystem filterDate(Predicate<Instant> filter) {
		return GitFilteringFs.filter(fs, p -> filter.test(dates.get(p.id())));
	}

	/**
	 * Returns a git history that only shows paths whose timestamp match the given
	 * filter.
	 *
	 * @param filter indicates which elements should be kept
	 * @return a filtering file system
	 * @throws IOException TODO to remove
	 */
	public GitHistorySimple filtered(Predicate<Instant> filter) throws IOException {
		return GitHistorySimple.create(filterDate(filter), dates);
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
