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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.Date;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.common.collect.Streams;
import com.google.common.graph.Traverser;

import io.github.oliviercailloux.git.Client;
import io.github.oliviercailloux.git.GitHistory;
import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinates;
import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.CsvGrades;
import io.github.oliviercailloux.grade.Grade;
import io.github.oliviercailloux.grade.GraderOrchestrator;
import io.github.oliviercailloux.grade.GradingException;
import io.github.oliviercailloux.grade.Mark;
import io.github.oliviercailloux.grade.context.FilesSource;
import io.github.oliviercailloux.grade.context.GitFullContext;
import io.github.oliviercailloux.grade.contexters.FullContextInitializer;
import io.github.oliviercailloux.grade.json.JsonGrade;
import io.github.oliviercailloux.grade.markers.Marks;
import io.github.oliviercailloux.grade.mycourse.StudentOnGitHub;
import io.github.oliviercailloux.java_grade.testers.MarkHelper;

public class ExGitGrader {
	private double maxGrade;
	private GitHistory history;

	public ExGitGrader() {
		maxGrade = Double.NEGATIVE_INFINITY;
		history = null;
	}

	public ImmutableSet<Mark> grade(RepositoryCoordinates coord, StudentOnGitHub student) {
		final ImmutableSet.Builder<Mark> gradeBuilder = ImmutableSet.builder();
		final Path projectsBaseDir = Paths.get("/home/olivier/Professions/Enseignement/En cours/git");
		final Instant deadline = ZonedDateTime.parse("2019-03-20T23:59:59+01:00").toInstant();

		final GitFullContext fullContext = FullContextInitializer.withPath(coord, projectsBaseDir);
		final Client client = fullContext.getClient();
		maxGrade = Stream.of(ExGitCriterion.values()).collect(Collectors.summingDouble(Criterion::getMaxPoints));
		try {
			history = client.getWholeHistory();
		} catch (IOException | GitAPIException e) {
			throw new GradingException(e);
		}
		gradeBuilder.add(Marks.timeMark(ON_TIME, fullContext, deadline, this::getPenalty));
		gradeBuilder.add(Marks.gitRepo(REPO_EXISTS, fullContext));

		final Set<RevCommit> rootCommits = history.getRoots();
		{
			LOGGER.debug("First commits: {}.", rootCommits);
			final Mark mark;
			switch (rootCommits.size()) {
			case 0:
				mark = Mark.min(SINGLE_ROOT_COMMIT);
				break;
			case 1:
				mark = Mark.of(SINGLE_ROOT_COMMIT, SINGLE_ROOT_COMMIT.getMaxPoints(),
						String.format("Root commit: %s.", Iterables.getOnlyElement(rootCommits).getName()));
				break;
			default:
				mark = Mark.min(SINGLE_ROOT_COMMIT, String.format("Root commits: %s.", toString(rootCommits)));
				break;
			}
			gradeBuilder.add(mark);
		}

		final Optional<RevCommit> firstRootCommitOpt = rootCommits.stream()
				.min(Comparator.comparing(this::getCreatedAt));
		final FilesSource firstRootCommitReader = fullContext.getFilesReader(firstRootCommitOpt);

		final ImmutableSet<RevCommit> byGitHub = history.getGraph().nodes().stream()
				.filter(MarkHelper::committerIsGitHub).collect(ImmutableSet.toImmutableSet());
		{
			final Mark mark;
			if (!byGitHub.isEmpty()) {
				mark = Mark.min(GIT, String.format("Committed by GitHub: %s.", toString(byGitHub)));
			} else {
				mark = Mark.max(GIT);
			}
			gradeBuilder.add(mark);
		}

		{
			final Mark mark;
			if (firstRootCommitOpt.isPresent()) {
				final RevCommit firstRootCommit = firstRootCommitOpt.get();
				final String contributerName = firstRootCommit.getAuthorIdent().getName();
				final String gitHubUsername = student.getGitHubUsername();
				if (contributerName.equals(gitHubUsername)) {
					mark = Mark.max(USER_NAME);
				} else {
					mark = Mark.min(USER_NAME,
							String.format("Wrong contributer name: %s, expected %s", contributerName, gitHubUsername));
				}
			} else {
				mark = Mark.min(USER_NAME);
			}
			gradeBuilder.add(mark);
		}

		final FilesSource startFiles = firstRootCommitReader.filterOnPath((p) -> p.equals(Paths.get("start.txt")));
		gradeBuilder.add(Mark.binary(CONTAINS_START, !startFiles.asFileContents().isEmpty()));
		gradeBuilder.add(Mark.binary(HELLO2, startFiles.existsAndAllMatch(Predicates.containsPattern("hello2"))));

		final Optional<RevCommit> devOpt = tryParseSpec(client, "refs/remotes/origin/dev");
		gradeBuilder.add(Mark.binary(DEV_EXISTS, devOpt.isPresent()));

		gradeBuilder.add(Mark.binary(DEV_CONTAINS_BOLD,
				fullContext.getFilesReader(devOpt).filterOnPath((p) -> p.equals(Paths.get("bold.txt")))
						.existsAndAllMatch(Predicates.containsPattern("try 1"))));

		final Optional<RevCommit> dev2Opt = tryParseSpec(client, "refs/remotes/origin/dev2");
		gradeBuilder.add(Mark.binary(DEV2_EXISTS, dev2Opt.isPresent()));
		gradeBuilder.add(Mark.binary(ALTERNATIVE_APPROACH,
				fullContext.getFilesReader(dev2Opt).filterOnPath((p) -> p.equals(Paths.get("bold.txt")))
						.existsAndAllMatch(Predicates.containsPattern("alternative approach"))));

		final ImmutableSet<RevCommit> mergeCommits = history.getGraph().nodes().stream()
				.filter((c) -> isMergeCommit1(c, devOpt, dev2Opt, firstRootCommitOpt.get()))
				.collect(ImmutableSet.toImmutableSet());
		final Optional<RevCommit> mergeCommitOpt;
		{
			final Mark mark;
			switch (mergeCommits.size()) {
			case 0:
				mark = Mark.min(MERGE1_COMMIT, "None found.");
				mergeCommitOpt = Optional.empty();
				break;
			case 1:
				final RevCommit mergeCommit = Iterables.getOnlyElement(mergeCommits);
				mark = Mark.of(MERGE1_COMMIT, MERGE1_COMMIT.getMaxPoints(),
						String.format("Merge commit found: %s.", mergeCommit.getName()));
				mergeCommitOpt = Optional.of(mergeCommit);
				break;
			default:
				mark = Mark.min(MERGE1_COMMIT,
						String.format("Multiple merge commit found: %s.", toString(mergeCommits)));
				mergeCommitOpt = Optional.empty();
				break;
			}
			gradeBuilder.add(mark);
		}

		final FilesSource mergedBoldFiles = fullContext.getFilesReader(mergeCommitOpt)
				.filterOnPath((p) -> p.equals(Paths.get("bold.txt")));
		gradeBuilder.add(Mark.binary(MERGE1_CONTAINS_BOLD, !mergedBoldFiles.asFileContents().isEmpty()));
		gradeBuilder.add(
				Mark.binary(MERGED1, mergedBoldFiles.existsAndNoneMatch(Predicates.containsPattern("<<<|===|>>>"))));

		final Set<RevCommit> thenCommits = mergeCommitOpt.isPresent()
				? history.getGraph().predecessors(mergeCommitOpt.get())
				: ImmutableSet.of();
		final Optional<RevCommit> thenCommitOpt;
		{
			final Mark mark;
			switch (thenCommits.size()) {
			case 0:
				mark = Mark.min(AFTER_MERGE1, "None found.");
				thenCommitOpt = Optional.empty();
				break;
			case 1:
				final RevCommit thenCommit = Iterables.getOnlyElement(thenCommits);
				mark = Mark.of(AFTER_MERGE1, AFTER_MERGE1.getMaxPoints(),
						String.format("Commit after merge1 found: %s.", thenCommit.getName()));
				thenCommitOpt = Optional.of(thenCommit);
				break;
			default:
				mark = Mark.min(AFTER_MERGE1,
						String.format("Multiple commits found having merged1 as parent: %s.", toString(thenCommits)));
				thenCommitOpt = Optional.empty();
				break;
			}
			gradeBuilder.add(mark);
		}
		final FilesSource afterMergeBoldFiles = fullContext.getFilesReader(thenCommitOpt)
				.filterOnPath((p) -> p.equals(Paths.get("bold.txt")));
		LOGGER.debug("After merge bold files: {}.", afterMergeBoldFiles.getContents());
		gradeBuilder.add(Mark.binary(AFTER_MERGE1_BOLD,
				!mergedBoldFiles.asFileContents().isEmpty() && !mergedBoldFiles.equals(afterMergeBoldFiles)));

		final Traverser<RevCommit> traverser = Traverser.forGraph(history.getGraph());

		final ImmutableSet<RevCommit> mergeCommits2 = history.getGraph().nodes().stream()
				.filter((c) -> c.getParentCount() == 2 && ImmutableSet.copyOf(c.getParents()).stream()
						.allMatch((p) -> isAwayFromAtLeast(traverser, p, 4, firstRootCommitOpt.get())))
				.collect(ImmutableSet.toImmutableSet());
		{
			final Mark mark;
			switch (mergeCommits2.size()) {
			case 0:
				mark = Mark.min(MERGE2_COMMIT, "None found.");
				break;
			case 1:
				mark = Mark.of(MERGE2_COMMIT, MERGE2_COMMIT.getMaxPoints(),
						String.format("Merge2 found: %s.", Iterables.getOnlyElement(mergeCommits2).getName()));
				break;
			default:
				mark = Mark.min(MERGE2_COMMIT,
						String.format("Multiple merge 2 commits found: %s.", toString(mergeCommits2)));
				break;
			}
			gradeBuilder.add(mark);
		}

		final ImmutableSet<Mark> grade = gradeBuilder.build();
		final Set<Criterion> diff = Sets.symmetricDifference(ImmutableSet.copyOf(ExGitCriterion.values()),
				grade.stream().map(Mark::getCriterion).collect(ImmutableSet.toImmutableSet()));
		assert diff.isEmpty() : diff;
		return grade;
	}

	private String toString(Set<RevCommit> commits) {
		return "[" + commits.stream().map(RevCommit::getName).collect(Collectors.joining(", ")) + "]";
	}

	private Optional<RevCommit> tryParseSpec(Client client, String revSpec) {
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

	/**
	 * TODO check if redundant with client
	 */
	private Instant getCreatedAt(RevCommit commit) {
		final PersonIdent authorIdent = commit.getAuthorIdent();
		final Date when = authorIdent.getWhen();
		// final TimeZone tz = authorIdent.getTimeZone();
		return when.toInstant();
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

	public static void main(String[] args) throws Exception {
		final String prefix = "git";
		final GraderOrchestrator orch = new GraderOrchestrator(prefix);
		orch.readUsernames(Paths.get("../../Java L3/usernamesGH-manual.json"));

		orch.readRepositories();
		final ImmutableMap<StudentOnGitHub, RepositoryCoordinates> repositories = orch.getRepositoriesByStudent();

		final ExGitGrader grader = new ExGitGrader();

		final ImmutableSet<Grade> grades = repositories.entrySet().stream()
				.map((e) -> Grade.of(e.getKey(), grader.grade(e.getValue(), e.getKey())))
				.collect(ImmutableSet.toImmutableSet());

		Files.writeString(Paths.get("allgrades " + prefix + ".json"), JsonGrade.asJsonArray(grades).toString());
		Files.writeString(Paths.get("allgrades " + prefix + ".csv"), CsvGrades.asCsv(grades));
//		new MyCourseCsvWriter().asMyCourseCsv("Devoir " + prefix, 112226, grades, 10d);
	}
}
