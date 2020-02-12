package io.github.oliviercailloux.git;

import static com.google.common.base.Preconditions.checkArgument;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

import org.eclipse.jgit.lib.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.graph.Graphs;
import com.google.common.graph.ImmutableGraph;

public class GitRawHistoryDecorator<E extends ObjectId> implements GitHistory<E> {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GitRawHistoryDecorator.class);

	public static interface GitRawHistory<E extends ObjectId> {
		public ImmutableGraph<E> getGraph();

		public ImmutableGraph<ObjectId> getRawGraph();

		public Instant getCommitDate(E objectId);

		public ImmutableMap<E, Instant> getCommitDates();
	}

	public static <E extends ObjectId> GitHistory<E> wrap(GitRawHistory<E> raw) {
		return new GitRawHistoryDecorator<>(raw);
	}

	private final GitRawHistory<E> raw;

	protected GitRawHistoryDecorator(GitRawHistory<E> raw) {
		this.raw = raw;
		checkArgument(!Graphs.hasCycle(raw.getGraph()));
		final ImmutableSet<E> dateKeys = raw.getCommitDates().keySet();
		final Set<E> nodes = raw.getGraph().nodes();
		checkArgument(dateKeys.equals(nodes),
				String.format("Commit dates: %s; graph nodes: %s, dates contain nodes: %s, missing: %s.",
						dateKeys.size(), nodes.size(), dateKeys.containsAll(nodes), Sets.difference(dateKeys, nodes)));
	}

	@Override
	public ImmutableGraph<E> getGraph() {
		return raw.getGraph();
	}

	@Override
	public ImmutableGraph<ObjectId> getRawGraph() {
		return raw.getRawGraph();
	}

	@Override
	public ImmutableSet<E> getRoots() {
		/**
		 * We could start from any given node and simply follow the successor
		 * (has-as-parent) relation, but that finds only one root.
		 */
		final ImmutableSet<E> roots = getGraph().nodes().stream().filter((n) -> getGraph().successors(n).isEmpty())
				.collect(ImmutableSet.toImmutableSet());
		return roots;
	}

	@Override
	public ImmutableSet<E> getTips() {
		final ImmutableSet<E> tips = getGraph().nodes().stream().filter((n) -> getGraph().predecessors(n).isEmpty())
				.collect(ImmutableSet.toImmutableSet());
		return tips;
	}

	@Override
	public Instant getCommitDate(E objectId) {
		return raw.getCommitDate(objectId);
	}

	@Override
	public ImmutableMap<E, Instant> getCommitDates() {
		return raw.getCommitDates();
	}

	@Override
	public boolean equals(Object o2) {
		if (!(o2 instanceof GitHistory<?>)) {
			return false;
		}

		final GitHistory<?> h2 = (GitHistory<?>) o2;
		return getGraph().equals(h2.getGraph()) && getCommitDates().equals(h2.getCommitDates());
	}

	@Override
	public int hashCode() {
		return Objects.hash(getGraph(), getCommitDates());
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this).add("graph", getGraph()).add("commitDates", getCommitDates())
				.toString();
	}
}
