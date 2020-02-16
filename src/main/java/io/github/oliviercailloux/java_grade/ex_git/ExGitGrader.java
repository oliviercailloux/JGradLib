package io.github.oliviercailloux.java_grade.ex_git;

import static com.google.common.base.Preconditions.checkArgument;
import static io.github.oliviercailloux.java_grade.ex_git.ExGitCriterion.AFTER_MERGE1;
import static io.github.oliviercailloux.java_grade.ex_git.ExGitCriterion.AFTER_MERGE1_BOLD;
import static io.github.oliviercailloux.java_grade.ex_git.ExGitCriterion.ALTERNATIVE_APPROACH;
import static io.github.oliviercailloux.java_grade.ex_git.ExGitCriterion.CONTAINS_START;
import static io.github.oliviercailloux.java_grade.ex_git.ExGitCriterion.DEV2_EXISTS;
import static io.github.oliviercailloux.java_grade.ex_git.ExGitCriterion.DEV_CONTAINS_BOLD;
import static io.github.oliviercailloux.java_grade.ex_git.ExGitCriterion.DEV_EXISTS;
import static io.github.oliviercailloux.java_grade.ex_git.ExGitCriterion.GIT;
import static io.github.oliviercailloux.java_grade.ex_git.ExGitCriterion.HELLO2;
import static io.github.oliviercailloux.java_grade.ex_git.ExGitCriterion.MERGE1_COMMIT;
import static io.github.oliviercailloux.java_grade.ex_git.ExGitCriterion.MERGE1_CONTAINS_BOLD;
import static io.github.oliviercailloux.java_grade.ex_git.ExGitCriterion.MERGE2_COMMIT;
import static io.github.oliviercailloux.java_grade.ex_git.ExGitCriterion.MERGED1;
import static io.github.oliviercailloux.java_grade.ex_git.ExGitCriterion.ON_TIME;
import static io.github.oliviercailloux.java_grade.ex_git.ExGitCriterion.REPO_EXISTS;
import static io.github.oliviercailloux.java_grade.ex_git.ExGitCriterion.SINGLE_ROOT_COMMIT;
import static io.github.oliviercailloux.java_grade.ex_git.ExGitCriterion.USER_NAME;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Streams;
import com.google.common.graph.Traverser;
import com.google.common.primitives.Booleans;

import io.github.oliviercailloux.git.ComplexClient;
import io.github.oliviercailloux.git.GitLocalHistory;
import io.github.oliviercailloux.git.GitUtils;
import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinates;
import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.GradingException;
import io.github.oliviercailloux.grade.IGrade;
import io.github.oliviercailloux.grade.Mark;
import io.github.oliviercailloux.grade.comm.StudentOnGitHub;
import io.github.oliviercailloux.grade.context.FilesSource;
import io.github.oliviercailloux.grade.context.GitFullContext;
import io.github.oliviercailloux.grade.contexters.FullContextInitializer;
import io.github.oliviercailloux.grade.markers.Marks;
import io.github.oliviercailloux.java_grade.testers.MarkHelper;

public class ExGitGrader {
	private double maxGrade;
	private GitLocalHistory history;

	public ExGitGrader() {
		maxGrade = Double.NEGATIVE_INFINITY;
		history = null;
	}

	public Map<Criterion, IGrade> grade(RepositoryCoordinates coord, StudentOnGitHub student) {
		final ImmutableMap.Builder<Criterion, IGrade> gradeBuilder = ImmutableMap.builder();
		final Path projectsBaseDir = Paths.get("/home/olivier/Professions/Enseignement/En cours/git");
		final Instant deadline = ZonedDateTime.parse("2019-03-20T23:59:59+01:00").toInstant();

		final GitFullContext fullContext = FullContextInitializer.withPath(coord, projectsBaseDir);
		final ComplexClient client = fullContext.getClient();
//		maxGrade = Stream.of(ExGitCriterion.values())
//				.collect(Collectors.summingDouble(CriterionAndPoints::getMaxPoints));
		try {
			history = client.getWholeHistory();
		} catch (IOException e) {
			throw new GradingException(e);
		}
		gradeBuilder.put(ON_TIME, Marks.timeGrade(fullContext, deadline, this::getPenalty));
		gradeBuilder.put(REPO_EXISTS, Marks.gitRepoGrade(fullContext));

		/** TODO generalize history for all situations (not necessarily a DAG?) */
		final Set<RevCommit> rootCommits = history.getRoots();
		{
			LOGGER.debug("First commits: {}.", rootCommits);
			final Mark mark;
			switch (rootCommits.size()) {
			case 0:
				mark = Mark.zero();
				break;
			case 1:
				mark = Mark.given(1d,
						String.format("Root commit: %s.", Iterables.getOnlyElement(rootCommits).getName()));
				break;
			default:
				mark = Mark.zero(String.format("Root commits: %s.", GitUtils.toOIds(rootCommits)));
				break;
			}
			gradeBuilder.put(SINGLE_ROOT_COMMIT, mark);
		}

		final Optional<RevCommit> firstRootCommitOpt = rootCommits.stream()
				.min(Comparator.comparing(GitUtils::getCreationTime));
		final FilesSource firstRootCommitReader = fullContext.getFilesReader(firstRootCommitOpt);

		final ImmutableSet<RevCommit> byGitHub = history.getGraph().nodes().stream()
				.filter(MarkHelper::committerIsGitHub).collect(ImmutableSet.toImmutableSet());
		{
			final Mark mark;
			if (!byGitHub.isEmpty()) {
				mark = Mark.zero(String.format("Committed by GitHub: %s.", GitUtils.toOIds(byGitHub)));
			} else {
				mark = Mark.one();
			}
			gradeBuilder.put(GIT, mark);
		}

		{
			final Mark mark;
			if (firstRootCommitOpt.isPresent()) {
				final RevCommit firstRootCommit = firstRootCommitOpt.get();
				final String contributerName = firstRootCommit.getAuthorIdent().getName();
				final String gitHubUsername = student.getGitHubUsername();
				if (contributerName.equals(gitHubUsername)) {
					mark = Mark.one();
				} else {
					mark = Mark.zero(
							String.format("Wrong contributer name: %s, expected %s", contributerName, gitHubUsername));
				}
			} else {
				mark = Mark.zero();
			}
			gradeBuilder.put(USER_NAME, mark);
		}

		final FilesSource startFiles = firstRootCommitReader.filterOnPath((p) -> p.equals(Paths.get("start.txt")));
		gradeBuilder.put(CONTAINS_START, Mark.given(Booleans.countTrue(!startFiles.asFileContents().isEmpty()), ""));
		gradeBuilder.put(HELLO2,
				Mark.given(Booleans.countTrue(startFiles.existsAndAllMatch(Predicates.containsPattern("hello2"))), ""));

		final Optional<RevCommit> devOpt = tryParseSpec(client, "refs/remotes/origin/dev");
		gradeBuilder.put(DEV_EXISTS, Mark.given(Booleans.countTrue(devOpt.isPresent()), ""));

		gradeBuilder.put(DEV_CONTAINS_BOLD,
				Mark.given(Booleans.countTrue(
						fullContext.getFilesReader(devOpt).filterOnPath((p) -> p.equals(Paths.get("bold.txt")))
								.existsAndAllMatch(Predicates.containsPattern("try 1"))),
						""));

		final Optional<RevCommit> dev2Opt = tryParseSpec(client, "refs/remotes/origin/dev2");
		gradeBuilder.put(DEV2_EXISTS, Mark.given(Booleans.countTrue(dev2Opt.isPresent()), ""));
		gradeBuilder.put(ALTERNATIVE_APPROACH,
				Mark.given(Booleans.countTrue(
						fullContext.getFilesReader(dev2Opt).filterOnPath((p) -> p.equals(Paths.get("bold.txt")))
								.existsAndAllMatch(Predicates.containsPattern("alternative approach"))),
						""));

		final ImmutableSet<RevCommit> mergeCommits = history.getGraph().nodes().stream()
				.filter((c) -> isMergeCommit1(c, devOpt, dev2Opt, firstRootCommitOpt.get()))
				.collect(ImmutableSet.toImmutableSet());
		final Optional<RevCommit> mergeCommitOpt;
		{
			final Mark mark;
			switch (mergeCommits.size()) {
			case 0:
				mark = Mark.zero("None found.");
				mergeCommitOpt = Optional.empty();
				break;
			case 1:
				final RevCommit mergeCommit = Iterables.getOnlyElement(mergeCommits);
				mark = Mark.given(1d, String.format("Merge commit found: %s.", mergeCommit.getName()));
				mergeCommitOpt = Optional.of(mergeCommit);
				break;
			default:
				mark = Mark.zero(String.format("Multiple merge commit found: %s.", GitUtils.toOIds(mergeCommits)));
				mergeCommitOpt = Optional.empty();
				break;
			}
			gradeBuilder.put(MERGE1_COMMIT, mark);
		}

		final FilesSource mergedBoldFiles = fullContext.getFilesReader(mergeCommitOpt)
				.filterOnPath((p) -> p.equals(Paths.get("bold.txt")));
		gradeBuilder.put(MERGE1_CONTAINS_BOLD,
				Mark.given(Booleans.countTrue(!mergedBoldFiles.asFileContents().isEmpty()), ""));
		gradeBuilder.put(MERGED1, Mark.given(
				Booleans.countTrue(mergedBoldFiles.existsAndNoneMatch(Predicates.containsPattern("<<<|===|>>>"))), ""));

		final Set<RevCommit> thenCommits = mergeCommitOpt.isPresent()
				? history.getGraph().predecessors(mergeCommitOpt.get())
				: ImmutableSet.of();
		final Optional<RevCommit> thenCommitOpt;
		{
			final Mark mark;
			switch (thenCommits.size()) {
			case 0:
				mark = Mark.zero("None found.");
				thenCommitOpt = Optional.empty();
				break;
			case 1:
				final RevCommit thenCommit = Iterables.getOnlyElement(thenCommits);
				mark = Mark.given(1d, String.format("Commit after merge1 found: %s.", thenCommit.getName()));
				thenCommitOpt = Optional.of(thenCommit);
				break;
			default:
				mark = Mark.zero(String.format("Multiple commits found having merged1 as parent: %s.",
						GitUtils.toOIds(thenCommits)));
				thenCommitOpt = Optional.empty();
				break;
			}
			gradeBuilder.put(AFTER_MERGE1, mark);
		}
		final FilesSource afterMergeBoldFiles = fullContext.getFilesReader(thenCommitOpt)
				.filterOnPath((p) -> p.equals(Paths.get("bold.txt")));
		LOGGER.debug("After merge bold files: {}.", afterMergeBoldFiles.getContents());
		gradeBuilder.put(AFTER_MERGE1_BOLD,
				Mark.given(Booleans.countTrue(
						!mergedBoldFiles.asFileContents().isEmpty() && !mergedBoldFiles.equals(afterMergeBoldFiles)),
						""));

		final Traverser<RevCommit> traverser = Traverser.forGraph(history.getGraph());

		final ImmutableSet<RevCommit> mergeCommits2 = history.getGraph().nodes().stream()
				.filter((c) -> c.getParentCount() == 2 && ImmutableSet.copyOf(c.getParents()).stream()
						.allMatch((p) -> isAwayFromAtLeast(traverser, p, 4, firstRootCommitOpt.get())))
				.collect(ImmutableSet.toImmutableSet());
		{
			final Mark mark;
			switch (mergeCommits2.size()) {
			case 0:
				mark = Mark.zero("None found.");
				break;
			case 1:
				mark = Mark.given(1d,
						String.format("Merge2 found: %s.", Iterables.getOnlyElement(mergeCommits2).getName()));
				break;
			default:
				mark = Mark.zero(String.format("Multiple merge 2 commits found: %s.", GitUtils.toOIds(mergeCommits2)));
				break;
			}
			gradeBuilder.put(MERGE2_COMMIT, mark);
		}

		final ImmutableMap<Criterion, IGrade> grade = gradeBuilder.build();
		return grade;
	}

	private Optional<RevCommit> tryParseSpec(ComplexClient client, String revSpec) {
		final Optional<RevCommit> devOpt;
		try {
			devOpt = client.tryResolve(revSpec).map(t -> {
				try {
					return client.getCommit(t);
				} catch (IOException e) {
					throw new IllegalStateException(e);
				}
			});
		} catch (IOException e) {
			throw new GradingException(e);
		}
		return devOpt;
	}

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(ExGitGrader.class);

	double getPenalty(Duration tardiness) {
		final long seconds = tardiness.getSeconds();
		final double hours = seconds / 3600d;
		checkArgument(hours > 0d);
		double penalty = 0d;
		double remainingHours = hours;
		final ImmutableMap<Double, Double> slopesByEndRange = ImmutableMap.of(24d, -4d / 10d / 24d * maxGrade, 6d,
				-6d / 10d / 6d * maxGrade, Double.POSITIVE_INFINITY, -0d);
		final ImmutableSet<Double> endRanges = slopesByEndRange.keySet();

		for (double endRange : endRanges) {
			final double inThisRange = Math.min(remainingHours, endRange);
			remainingHours -= endRange;
			final double slope = slopesByEndRange.get(endRange);
			penalty += slope * inThisRange;
			if (remainingHours <= 0d) {
				break;
			}
		}
		assert penalty < 0d;
		return penalty;
	}

	void setMaxGrade(double maxGrade) {
		this.maxGrade = maxGrade;
	}

	private boolean isMergeCommit1(RevCommit commit, Optional<RevCommit> devOpt, Optional<RevCommit> dev2Opt,
			RevCommit firstCommit) {
		if (commit.getParentCount() != 2) {
			return false;
		}
		final ImmutableSet<RevCommit> parents = ImmutableSet.copyOf(commit.getParents());

		final boolean parentsAreDev = devOpt.isPresent() && dev2Opt.isPresent()
				&& parents.equals(ImmutableSet.of(devOpt.get(), dev2Opt.get()));
		final boolean parentsAreSecond = parents.stream()
				.allMatch((c) -> c.getParentCount() == 1 && c.getParent(0).equals(firstCommit));
		return parentsAreDev || parentsAreSecond;
	}

	private boolean isAwayFromAtLeast(Traverser<RevCommit> traverser, RevCommit start, int distance, RevCommit target) {
		final boolean reached = Streams.stream(traverser.depthFirstPreOrder(start)).limit(distance)
				.anyMatch(Predicates.equalTo(target));
		LOGGER.debug("Checking is away ({}): {}, {} → reached? {}.", distance, start, target, reached);
		return !reached;
	}
}
