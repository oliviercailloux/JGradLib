package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import org.eclipse.jgit.lib.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableSet;
import com.google.common.graph.ImmutableGraph;

import io.github.oliviercailloux.git.GitHistory;
import io.github.oliviercailloux.git.fs.GitFileSystem;
import io.github.oliviercailloux.git.fs.GitPathRoot;
import io.github.oliviercailloux.utils.Utils;

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
		final GitPathRoot direct = gitFs.getPathRoot(objectId);
		return Optional.of(direct).filter(r -> graph.nodes().contains(r));
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
