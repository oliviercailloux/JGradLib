package io.github.oliviercailloux.grade.context;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Streams;
import com.google.common.graph.ImmutableGraph;
import com.google.common.graph.Traverser;

import io.github.oliviercailloux.git.Client;
import io.github.oliviercailloux.git.GitUtils;

public class GradingContextWithTimeline {

	private final Client client;

	/**
	 * @param commitsReceptionTime in case of uncertaintly, indicate the earlier
	 *                             time that the commit may exist: before that, we
	 *                             know it didn’t exist. This permits to ensure that
	 *                             conclusions about lateness are certain.
	 */
	public static GradingContextWithTimeline given(Client client, Map<ObjectId, Instant> commitsReceptionTime) {
		return new GradingContextWithTimeline(client, commitsReceptionTime);
	}

	private GradingContextWithTimeline(Client client, Map<ObjectId, Instant> commitsReceptionTime) {
		this.client = requireNonNull(client);
		this.commitsReceptionTime = ImmutableMap.copyOf(requireNonNull(commitsReceptionTime));
	}

	private final ImmutableMap<ObjectId, Instant> commitsReceptionTime;

	public ImmutableMap<ObjectId, Instant> getCommitsReceptionTime() {
		return commitsReceptionTime;
	}

	public Optional<Instant> getReceivedAt(ObjectId id) {
		return Optional.ofNullable(commitsReceptionTime.get(id));
	}

	public Optional<RevCommit> getLatestNotIgnored(Instant ignoreAfter) {
		requireNonNull(ignoreAfter);
		final ImmutableGraph<RevCommit> graph = client.getAllHistoryCached().getGraph();
		final Iterable<RevCommit> commits = graph.nodes();
		return getLatestNotIgnoredAmong(commits, ignoreAfter);
	}

	private Optional<RevCommit> getLatestNotIgnoredAmong(Iterable<RevCommit> commits, Instant ignoreAfter) {
		final ImmutableList<RevCommit> commitsOnTime = Streams.stream(commits)
				.filter((c) -> !commitsReceptionTime.get(c.getId()).isAfter(ignoreAfter))
				.collect(ImmutableList.toImmutableList());
		LOGGER.debug("Commits: {}; time: {}; ignore after {}; on time: {}.", ImmutableList.copyOf(commits),
				commitsReceptionTime, ignoreAfter, commitsOnTime);
		final Comparator<RevCommit> comparingReceptionTime = Comparator
				.comparing((c) -> commitsReceptionTime.get(c.getId()));
		final Optional<RevCommit> amongLatest = commitsOnTime.stream().findFirst();
		checkState(commitsOnTime.stream().allMatch((c) -> comparingReceptionTime.compare(amongLatest.get(), c) >= 0));
		/**
		 * In case two commits have been received at the same moment and are both the
		 * latest ones not ignored.
		 */
		final ImmutableList<RevCommit> allLatest = commitsOnTime.stream()
				.filter((c) -> comparingReceptionTime.compare(amongLatest.get(), c) == 0)
				.collect(ImmutableList.toImmutableList());
		final Optional<RevCommit> adoptedLatest;
		if (allLatest.size() < 2) {
			adoptedLatest = amongLatest;
		} else {
			LOGGER.info("Found multiple latest: {}.",
					allLatest.stream().map(RevCommit::getName).collect(ImmutableList.toImmutableList()));
			/**
			 * Previous code checked that they are all in a parent-child relationship, but
			 * that’s incorrect, given the partial knowledge of commit times given by
			 * GitHub. It could be, for example, that we don’t know anyting about reception
			 * times (if they are more than three months old), and therefore that everything
			 * is ex-æquo.
			 *
			 * Also, perhaps it could be that the two latest commits are not in
			 * parent-children relationship, both being child of a parent that is ignored.
			 *
			 * Rather use the declared times.
			 */
//			final RevCommit shouldBeParent = amongLatest.get();
//			final Set<RevCommit> allItsChildren = Graphs.reachableNodes(graph, shouldBeParent);
//			checkState(allLatest.get(0).equals(shouldBeParent));
//			checkState(allItsChildren.containsAll(allLatest.subList(1, allLatest.size())));
			final Map<ZonedDateTime, List<RevCommit>> grouped = commitsOnTime.stream()
					.collect(Collectors.groupingBy(GitUtils::getCreationTime));
			final Set<ZonedDateTime> times = grouped.keySet();
			final ZonedDateTime latestDeclaredTime = Collections.max(times);
			final List<RevCommit> allLatestDeclared = grouped.get(latestDeclaredTime);
			checkState(allLatestDeclared.size() == 1);
			final RevCommit theOne = allLatestDeclared.iterator().next();
			checkState(commitsReceptionTime.get(theOne).equals(commitsReceptionTime.get(amongLatest.get())));
			adoptedLatest = Optional.of(theOne);
		}
		return adoptedLatest;
	}

	public Optional<RevCommit> getLatestNotIgnoredChildOf(RevCommit startCommit, Instant ignoreAfter) {
		requireNonNull(ignoreAfter);
		final ImmutableGraph<RevCommit> graph = client.getAllHistoryCached().getGraph();
		final Iterable<RevCommit> commits = Traverser.forGraph(graph).breadthFirst(startCommit);
		return getLatestNotIgnoredAmong(commits, ignoreAfter);
	}

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GradingContextWithTimeline.class);
}
