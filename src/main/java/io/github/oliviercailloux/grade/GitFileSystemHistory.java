package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static io.github.oliviercailloux.jaris.exceptions.Unchecker.IO_UNCHECKER;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
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
 * <p>
 * To do this:
 * <li>anyMathAmongRefs(TP<GPR> refPredicate, TP<GP> filePredicate)
 * <li>anyMatch(TP<GP> filePredicate) // all roots are oids
 * <li><b>Most promising!
 * <li>And consider: filter(TP<ObjectId>) throws IOE.
 */
public class GitFileSystemHistory {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GitFileSystemHistory.class);

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

	public Optional<GitPathRoot> asDirect(GitPathRoot path) throws IOException {
		checkArgument(path.getFileSystem().equals(gitFs));
		if (!path.exists()) {
			return Optional.empty();
		}
		final ObjectId objectId = path.getCommit().getId();
		if (history.getGraph().nodes().contains(objectId)) {
			return Optional.of(gitFs.getPathRoot(objectId));
		}
		return Optional.empty();
	}

	public GitFileSystem getGitFilesystem() {
		return gitFs;
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
		final Throwing.Predicate<? super GitPathRoot, IOException> predicate = p -> history.getGraph().nodes()
				.contains(p.getCommit().getId());
		try {
			return gitFs.getRefs().stream().filter(IO_UNCHECKER.wrapPredicate(predicate))
					.collect(ImmutableSet.toImmutableSet());
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
	 * @throws IllegalArgumentException iff the given commit id is not a node of the
	 *                                  {@link #getGraph() graph}.
	 */
	public Instant getCommitDate(ObjectId commitId) {
		return history.getCommitDate(commitId);
	}

	public GitHistory asGitHistory() {
		return history;
	}

	public GitFileSystemHistory filter(Predicate<ObjectId> predicate) {
		return GitFileSystemHistory.create(gitFs, history.filter(predicate));
	}

	/**
	 * TODO need potentially: anyMatch on oids; anyMath on refs; allMatchAndExists
	 * on oids and refs; getRefsMatching; getOidsMatching; getFilesFromRefsMatching;
	 * getFilesFromOidsMatching… ? Also, predicates even on oids should use
	 * GitPathRoots as arguments because we want to use r.resolve() and test
	 * existence, … Thus perhaps filter (that returns an history) is enough? No
	 * because can’t filter (and return an history) on refs, only on oids.
	 *
	 * So: filter and return an history the oids; using a predicate<oid> or a
	 * predicate<path>; AND provide stuff about refs (perhaps static method?).
	 */
	public boolean anyMatch(Throwing.Predicate<GitPathRoot, IOException> predicate) throws IOException {
		try {
			return graph.nodes().stream().anyMatch(IO_UNCHECKER.wrapPredicate(predicate));
		} catch (UncheckedIOException exc) {
			throw exc.getCause();
		}
	}

	public boolean allMatchAndExists(Throwing.Predicate<GitPathRoot, IOException> predicate) throws IOException {
		try {
			return !graph.nodes().isEmpty() && graph.nodes().stream().allMatch(IO_UNCHECKER.wrapPredicate(predicate));
		} catch (UncheckedIOException exc) {
			throw exc.getCause();
		}
	}

	public Stream<GitPath> getFilesMatching(Throwing.Predicate<GitPath, IOException> predicate) throws IOException {
		final Predicate<GitPath> wrappedPredicate = IO_UNCHECKER.wrapPredicate(predicate);
		final Function<? super GitPathRoot, ? extends Stream<Path>> streamer = IO_UNCHECKER
				.wrapFunction(r -> Files.find(r, 100,
						(p, a) -> (a.isRegularFile() || a.isSymbolicLink()) && wrappedPredicate.test((GitPath) p)));
		try {
			return graph.nodes().stream().flatMap(streamer).map(p -> (GitPath) p);
		} catch (UncheckedIOException exc) {
			throw exc.getCause();
		}
	}

	public ImmutableSet<GitPathRoot> getFilesFromRefsMatching(
			Throwing.Predicate<GitPathRoot, IOException> predicateOnRoot,
			Throwing.Predicate<GitPath, IOException> predicateOnFile) throws IOException {
//		final Throwing.Predicate<? super GitPathRoot, IOException> predicate = p -> history.getGraph().nodes()
//				.contains(p.getCommit().getId());
//		try {
//			return gitFs.getRefs().stream().filter(IO_UNCHECKER.wrapPredicate(predicate))
//					.collect(ImmutableSet.toImmutableSet());
//		} catch (UncheckedIOException exc) {
//			throw exc.getCause();
//		}
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
