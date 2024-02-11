package io.github.oliviercailloux.java_grade.graders;

import static com.google.common.base.Verify.verify;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.common.graph.Graphs;
import com.google.common.graph.ImmutableGraph;

import io.github.oliviercailloux.git.filter.GitHistorySimple;
import io.github.oliviercailloux.gitjfs.GitFileSystem;
import io.github.oliviercailloux.gitjfs.GitPathRootShaCached;
import io.github.oliviercailloux.grade.BatchGitHistoryGrader;
import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.GitFileSystemWithHistoryFetcherByPrefix;
import io.github.oliviercailloux.grade.GitFsGrader;
import io.github.oliviercailloux.grade.GradeAggregator;
import io.github.oliviercailloux.grade.Mark;
import io.github.oliviercailloux.grade.MarksTree;
import io.github.oliviercailloux.jaris.exceptions.Unchecker;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Set;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TwoFiles implements GitFsGrader<RuntimeException> {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(TwoFiles.class);

	public static final String PREFIX = "two-files";

	public static void main(String[] args) throws Exception {
		final BatchGitHistoryGrader<RuntimeException> grader =
				BatchGitHistoryGrader.given(() -> GitFileSystemWithHistoryFetcherByPrefix
						.getRetrievingByPrefixAndUsingCommitDates(PREFIX));
		grader.getAndWriteGrades(ZonedDateTime.parse("2023-03-22T14:18:00+01:00[Europe/Paris]"),
				Duration.ofMinutes(5), new TwoFiles(), 0.15d, Path.of("grades " + PREFIX),
				PREFIX + " " + Instant.now());
	}

	private static final Criterion C0 = Criterion.given("Anything committed");
	private static final Criterion C1 = Criterion.given("Commit that deletes Some file");
	private static final Criterion C2 = Criterion.given("Commit that modifies Another file");
	private static final Criterion C3 = Criterion.given("Commit that deletes Another file");

	private static record ConsecutiveCommits (GitPathRootShaCached parent,
			GitPathRootShaCached child) {

	}

	@Override
	public MarksTree grade(GitHistorySimple data) {
		final ImmutableGraph<GitPathRootShaCached> graph = data.graph();
		verify(!graph.nodes().isEmpty());
		final GitFileSystem fs = data.fs();

		final ImmutableSet<GitPathRootShaCached> commitsOrdered =
				data.roots().stream().flatMap(r -> Graphs.reachableNodes(graph, r).stream())
						.collect(ImmutableSet.toImmutableSet());
		final ImmutableSet<GitPathRootShaCached> commitsOrderedExceptRoots =
				Sets.difference(commitsOrdered, data.roots()).immutableCopy();
		LOGGER.info("Commits ordered (except for roots): {}.", commitsOrderedExceptRoots);
		final int nbCommits = commitsOrderedExceptRoots.size();

		final MarksTree anyCommitMark = Mark.binary(!commitsOrderedExceptRoots.isEmpty(), String.format(
				"Found %s commit%s, not counting the root ones", nbCommits, nbCommits == 1 ? "" : "s"), "");

		final ImmutableSet.Builder<ConsecutiveCommits> builder = ImmutableSet.builder();
		for (GitPathRootShaCached child : commitsOrderedExceptRoots) {
			final Set<GitPathRootShaCached> parents = graph.predecessors(child);
			parents.stream().map(p -> new ConsecutiveCommits(p, child)).forEach(builder::add);
		}
		final ImmutableSet<ConsecutiveCommits> consecutives = builder.build();
		final ImmutableSet<ImmutableSet<DiffEntry>> diffs = consecutives.stream()
				.map(Unchecker.IO_UNCHECKER.wrapFunction(c -> fs.diff(c.parent, c.child)))
				.collect(ImmutableSet.toImmutableSet());

		final Mark c1m = diffs.stream().map(this::commit1Mark).max(Mark.byPoints())
				.orElse(Mark.zero("No commit, no comment"));
		final Mark c2m = diffs.stream().map(this::commit2Mark).max(Mark.byPoints())
				.orElse(Mark.zero("No commit, no comment"));
		final Mark c3m = diffs.stream().map(this::commit3Mark).max(Mark.byPoints())
				.orElse(Mark.zero("No commit, no comment"));

		return MarksTree.composite(ImmutableMap.of(C0, anyCommitMark, C1, c1m, C2, c2m, C3, c3m));
	}

	@Override
	public GradeAggregator getAggregator() {
		return GradeAggregator.staticAggregator(ImmutableMap.of(C0, 2d, C1, 4d, C2, 7d, C3, 4d),
				ImmutableMap.of());
	}

	private Mark commit1Mark(ImmutableSet<DiffEntry> diff) {
		verify(diff.size() >= 1);
		final boolean unique = diff.size() == 1;
		if (!unique) {
			return Mark.zero("More than one diff");
		}
		final DiffEntry theDiff = Iterables.getOnlyElement(diff);
		final boolean deletion = theDiff.getChangeType() == ChangeType.DELETE;
		final boolean rightName = theDiff.getOldPath().equals("Some file.txt");
		return Mark.binary(unique && deletion && rightName);
	}

	private Mark commit2Mark(ImmutableSet<DiffEntry> diff) {
		verify(diff.size() >= 1);
		final boolean unique = diff.size() == 1;
		if (!unique) {
			return Mark.zero("More than one diff");
		}
		final DiffEntry theDiff = Iterables.getOnlyElement(diff);
		final boolean rightChange = theDiff.getChangeType() == ChangeType.MODIFY;
		final boolean rightName = theDiff.getOldPath().equals("Some folder/Another file.txt");
		return Mark.binary(unique && rightChange && rightName);
	}

	private Mark commit3Mark(ImmutableSet<DiffEntry> diff) {
		verify(diff.size() >= 1);
		final boolean unique = diff.size() == 1;
		if (!unique) {
			return Mark.zero("More than one diff");
		}
		final DiffEntry theDiff = Iterables.getOnlyElement(diff);
		final boolean deletion = theDiff.getChangeType() == ChangeType.DELETE;
		final boolean rightName = theDiff.getOldPath().equals("Some folder/Another file.txt");
		return Mark.binary(unique && deletion && rightName);
	}
}
