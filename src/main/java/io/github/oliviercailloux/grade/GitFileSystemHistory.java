package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static io.github.oliviercailloux.jaris.exceptions.Unchecker.IO_UNCHECKER;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.eclipse.jgit.lib.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableSet;
import com.google.common.graph.ImmutableGraph;

import io.github.oliviercailloux.git.GitHistory;
import io.github.oliviercailloux.git.fs.GitFileSystem;
import io.github.oliviercailloux.git.fs.GitPath;
import io.github.oliviercailloux.git.fs.GitPathRoot;
import io.github.oliviercailloux.jaris.exceptions.Throwing;
import io.github.oliviercailloux.utils.Utils;

/**
 *
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
 * Idea: add getRefsMatching(Throwing.Predicate<GitPathRoot> refPredicate):
 * ImmutableSet<GitPathRoot>.
 *
 * static filter(Set<GitPathRoot>, Throwing.Predicate<GitPath>):
 * ImmutableSet<GitPath>. NB I can use this to filter on oids in order to
 * implement anyMatch if nothing better. But what about allMatchAndExists?
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
public class GitFileSystemHistory {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GitFileSystemHistory.class);

	private static ImmutableSet<GitPath> getPathsMatching(Stream<GitPathRoot> startingPaths,
			Throwing.Predicate<GitPath, IOException> predicate) throws IOException {
		final Predicate<GitPath> wrappedPredicate = IO_UNCHECKER.wrapPredicate(predicate);
		final Throwing.Function<GitPathRoot, Stream<GitPath>, IOException> matchingFiles = r -> Files
				.find(r, 100, (p, a) -> wrappedPredicate.test((GitPath) p)).map(p -> (GitPath) p);
		final Function<GitPathRoot, Stream<GitPath>> wrappedMatchingFiles = IO_UNCHECKER.wrapFunction(matchingFiles);
		try {
			return startingPaths.flatMap(wrappedMatchingFiles).collect(ImmutableSet.toImmutableSet());
		} catch (UncheckedIOException exc) {
			throw exc.getCause();
		}
	}

	public static GitFileSystemHistory create(GitFileSystem gitFs, GitHistory history) {
		return new GitFileSystemHistory(gitFs, history);
	}

	private ImmutableGraph<GitPathRoot> graph;
	private final GitFileSystem gitFs;
	private final GitHistory history;

	private GitFileSystemHistory(GitFileSystem gitFs, GitHistory history) {
		this.gitFs = checkNotNull(gitFs);
		this.history = checkNotNull(history);
		this.graph = null;
		for (ObjectId o : history.getGraph().nodes()) {
			checkArgument(Files.exists(gitFs.getPathRoot(o)));
		}
	}

	/**
	 * Returns a graph representing the has-as-child relation: the successors of a
	 * node are its children; following the successors (children) relation goes
	 * forward in time; following the predecessors (parents) relation goes back in
	 * time; a pair (a, b) in the graph represents a parent a and its child b.
	 *
	 * @return a DAG (thus, irreflexive)
	 */
	public ImmutableGraph<GitPathRoot> getGraph() {
		if (graph == null) {
			graph = Utils.asImmutableGraph(history.getGraph(), gitFs::getPathRoot);
		}
		return graph;
	}

	public ImmutableSet<GitPathRoot> getRefs() throws IOException {
		return getRefsStream().collect(ImmutableSet.toImmutableSet());
	}

	private Stream<GitPathRoot> getRefsStream() throws IOException {
		final Throwing.Predicate<? super GitPathRoot, IOException> inThisHistory = p -> history.getGraph().nodes()
				.contains(p.getCommit().getId());
		final Predicate<? super GitPathRoot> wrappedInThisHistory = IO_UNCHECKER.wrapPredicate(inThisHistory);
		try {
			return gitFs.getRefs().stream().filter(wrappedInThisHistory);
		} catch (UncheckedIOException exc) {
			throw exc.getCause();
		}
	}

	/**
	 * The children from which everything starts; the starting points in time of the
	 * git history; equivalently, the smallest set of nodes from which all nodes are
	 * reachable by following the “successors” (children) relation.
	 *
	 * @return empty iff the graph is empty.
	 */
	public ImmutableSet<GitPathRoot> getRoots() {
		return history.getRoots().stream().map(gitFs::getPathRoot).collect(ImmutableSet.toImmutableSet());
	}

	/**
	 * @return the nodes of the {@link #getGraph() graph} that have no children (no
	 *         successor); equivalently, the smallest set of nodes such that
	 *         starting from any node and following the “successors” (children)
	 *         relation necessarily ends in the set.
	 *
	 * @return empty iff the graph is empty.
	 */
	public ImmutableSet<GitPathRoot> getLeaves() {
		return history.getLeaves().stream().map(gitFs::getPathRoot).collect(ImmutableSet.toImmutableSet());
	}

	/**
	 * @throws IOException
	 * @throws NoSuchFileException
	 */
	public Instant getCommitDate(GitPathRoot commit) throws NoSuchFileException, IOException {
		final ObjectId id;
		if (commit.isCommitId()) {
			id = commit.getStaticCommitId();
		} else {
			id = commit.getCommit().getId();
		}
		if (!history.getGraph().nodes().contains(id)) {
			throw new NoSuchFileException(commit.toString());
		}
		return history.getCommitDate(id);
	}

	public boolean isEmpty() {
		return history.getGraph().nodes().isEmpty();
	}

	public GitFileSystemHistory filter(Throwing.Predicate<GitPathRoot, IOException> predicate) throws IOException {
		final Predicate<GitPathRoot> wrappedPredicate = IO_UNCHECKER.wrapPredicate(predicate);
		try {
			return GitFileSystemHistory.create(gitFs, history.filter(o -> wrappedPredicate.test(gitFs.getPathRoot(o))));
		} catch (UncheckedIOException e) {
			throw e.getCause();
		}
	}

	public Mark allAndSomeCommitMatch(Throwing.Predicate<GitPathRoot, IOException> p) throws IOException {
		final int nbMatch = filter(p).getGraph().nodes().size();
		return Mark.binary(nbMatch >= 1 && nbMatch == getGraph().nodes().size());
	}

	public Mark anyCommitMatches(Throwing.Predicate<GitPathRoot, IOException> p) throws IOException {
		final boolean match = !filter(p).isEmpty();
		return Mark.binary(match);
	}

	public Mark anyRefMatches(Throwing.Predicate<GitPathRoot, IOException> p) throws IOException {
		return Mark.binary(!getRefsMatching(p).isEmpty());
	}

	public ImmutableSet<GitPathRoot> getRefsMatching(Throwing.Predicate<GitPathRoot, IOException> predicate)
			throws IOException {
		final Predicate<GitPathRoot> wrappedPredicate = IO_UNCHECKER.wrapPredicate(predicate);
		try {
			return getRefsStream().filter(wrappedPredicate).collect(ImmutableSet.toImmutableSet());
		} catch (UncheckedIOException exc) {
			throw exc.getCause();
		}
	}

	@Deprecated
	public ImmutableSet<GitPath> getPathsMatching(Throwing.Predicate<GitPath, IOException> predicate)
			throws IOException {
		return getPathsMatching(getGraph().nodes().stream(), predicate);
	}

	public GitHistory asGitHistory() {
		return history;
	}

	@Override
	public boolean equals(Object o2) {
		if (!(o2 instanceof GitFileSystemHistory)) {
			return false;
		}
		final GitFileSystemHistory h2 = (GitFileSystemHistory) o2;
		return gitFs.equals(h2.gitFs) && history.equals(h2.history);
	}

	@Override
	public int hashCode() {
		return Objects.hash(gitFs, history);
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this).add("GitFs", gitFs).add("History", history).toString();
	}

}
