package io.github.oliviercailloux.grade;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import io.github.oliviercailloux.git.fs.GitHistorySimple;
import io.github.oliviercailloux.git.git_hub.model.GitHubUsername;
import io.github.oliviercailloux.jaris.throwing.TOptional;
import java.io.IOException;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ByTimeAndGitHubIgnoringGrader<X extends Exception> implements Grader<X> {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(ByTimeAndGitHubIgnoringGrader.class);

	private Grader<X> delegate;
	private ZonedDateTime deadline;

	public ByTimeAndGitHubIgnoringGrader(ZonedDateTime deadline, Grader<X> complexGrader) {
		this.deadline = deadline;
		this.delegate = complexGrader;
	}

	@Override
	public MarksTree grade(GitHubUsername author, GitHistorySimple history) throws X {
//		{
//			final Set<GitPathRoot> pureNodes = IO_UNCHECKER.getUsing(() -> history.getGraph().nodes());
//			final Set<GitPathRootSha> nodes = IO_UNCHECKER.getUsing(() -> history.getGraphSha().nodes());
//			final ImmutableSet<GitPathRoot> noSucc = pureNodes.stream()
//					.filter(n -> history.getGraph().successors(n).isEmpty()).collect(ImmutableSet.toImmutableSet());
//			LOGGER.info("Leaves: {}, no succ: {}, pure nodes: {}, nodes: {}, successors: {}.", history.getLeaves(),
//					noSucc, pureNodes, nodes,
//					nodes.stream().collect(ImmutableMap.toImmutableMap(n -> n, n -> history.getGraph().successors(n))));
//		}
//
//		final ImmutableList<GitPathRoot> roots = history.getRoots().asList();
//		final GitPathRoot sha9 = Iterables.getOnlyElement(roots);
//		verify(sha9.toString().equals("/95c941d7c75870877959cbe84a17a06711dad4c6//"), sha9.toString());
//		final Set<GitPathRoot> nodes = IO_UNCHECKER.getUsing(() -> history.getGraph().nodes());
//		final ImmutableMap<GitPathRoot, Set<GitPathRoot>> successors = nodes.stream()
//				.collect(ImmutableMap.toImmutableMap(n -> n, n -> history.getGraph().successors(n)));
//		final Set<GitPathRoot> succSha9 = successors.get(sha9);
//		final GitPathRoot shaC = Iterables.getOnlyElement(succSha9);
//		verify(shaC.toString().equals("/c36cb1a6f924e9f5b52183a326cc82e2cb578461//"), shaC.toString());
//		final Set<GitPathRoot> succShaC = successors.get(shaC);
//		verify(succShaC.isEmpty());
//		final ImmutableSet<GitPathRoot> noSuccs = nodes.stream().filter(n -> history.getGraph().successors(n).isEmpty())
//				.collect(ImmutableSet.toImmutableSet());
//		final GitPathRoot noSucc = Iterables.getOnlyElement(noSuccs);
//		verify(noSucc.toString().equals("/c36cb1a6f924e9f5b52183a326cc82e2cb578461//"), noSucc.toString());
//		final ImmutableSet<GitPathRootSha> leaves = history.getLeaves();
//		final GitPathRootSha leaf = Iterables.getOnlyElement(leaves);
//		verify(leaf.toString().equals("/c36cb1a6f924e9f5b52183a326cc82e2cb578461//"), leaf.toString());
//		verify(leaves.equals(noSuccs));
//		LOGGER.info("Leaves: {}.", history.getLeaves());

		final Optional<Instant> earliestTimeCommitByGitHub;
		final GitHistorySimple beforeCommitByGitHub;
		try {
			earliestTimeCommitByGitHub = ByTimeGrader.earliestTimeCommitByGitHub(history);
			LOGGER.debug("Earliest: {}.", earliestTimeCommitByGitHub);
			beforeCommitByGitHub = TOptional.wrapping(earliestTimeCommitByGitHub)
					.map(t -> history.filtered(i -> i.isBefore(t))).orElse(history);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		final String commentGeneralCapped = earliestTimeCommitByGitHub
				.map(t -> "; ignored commits after " + t.atZone(deadline.getZone()).toString() + ", sent by GitHub")
				.orElse("");

//		verify(beforeCommitByGitHub.getLeaves().equals(noSuccs));
		final ImmutableCollection<Instant> tsesFromGitH = history.getTimestamps().values();
		LOGGER.debug("Tses from gitH: {}.", tsesFromGitH);
		final ImmutableSortedSet<Instant> tsFromTimer = ByTimeGrader.getTimestamps(beforeCommitByGitHub,
				deadline.toInstant(), Instant.MAX);
		LOGGER.debug("Tses from timer: {}.", tsFromTimer);
		final ImmutableSet<GitHistorySimple> cappeds = ByTimeGrader.getCapped(beforeCommitByGitHub,
				deadline.toInstant(), Instant.MAX);
//		final GitFileSystemHistory cappedOnly = Iterables.getOnlyElement(cappeds);
//		verify(cappedOnly.getLeaves().equals(noSuccs));
		final ImmutableSet.Builder<SubMarksTree> cappedBuilder = ImmutableSet.builder();
		for (GitHistorySimple capped : cappeds) {
			final MarksTree cappedGrade = delegate.grade(author, capped);
			final Instant i = ByTimeGrader.cappedAt(capped);
			final String cappingAt = cappeds.size() == 1 ? "No capping"
					: ("Capping at " + i.atZone(deadline.getZone()).toString());
			final String comment = cappingAt + commentGeneralCapped;
			cappedBuilder.add(SubMarksTree.given(Criterion.given(comment), cappedGrade));
		}
		final MarksTree byTimeGrade = MarksTree.composite(cappedBuilder.build());
		return byTimeGrade;
	}

	@Override
	public GradeAggregator getAggregator() {
		final GradeAggregator maxAmongAttempts = GradeAggregator.max(delegate.getAggregator());
		return maxAmongAttempts;
	}

}