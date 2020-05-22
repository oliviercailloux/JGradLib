package io.github.oliviercailloux.java_grade.ex_dep_git;

import static io.github.oliviercailloux.java_grade.ex_dep_git.ExDepGitCriterion.CHILD_OF_STARTING;
import static io.github.oliviercailloux.java_grade.ex_dep_git.ExDepGitCriterion.COMMIT;
import static io.github.oliviercailloux.java_grade.ex_dep_git.ExDepGitCriterion.DEP;
import static io.github.oliviercailloux.java_grade.ex_dep_git.ExDepGitCriterion.FIRST_COMMIT;
import static io.github.oliviercailloux.java_grade.ex_dep_git.ExDepGitCriterion.MERGE_COMMIT;
import static io.github.oliviercailloux.java_grade.ex_dep_git.ExDepGitCriterion.PARENT_OF_MY_BRANCH;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import org.eclipse.jgit.lib.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Sets;
import com.google.common.graph.Graph;
import com.google.common.graph.Graphs;
import com.google.common.graph.ImmutableGraph;

import io.github.oliviercailloux.git.GitCloner;
import io.github.oliviercailloux.git.GitLocalHistory;
import io.github.oliviercailloux.git.GitUri;
import io.github.oliviercailloux.git.fs.GitFileSystemProvider;
import io.github.oliviercailloux.git.fs.GitRepoFileSystem;
import io.github.oliviercailloux.git.git_hub.model.GitHubHistory;
import io.github.oliviercailloux.git.git_hub.model.GitHubToken;
import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinates;
import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinatesWithPrefix;
import io.github.oliviercailloux.git.git_hub.services.GitHubFetcherQL;
import io.github.oliviercailloux.git.git_hub.services.GitHubFetcherV3;
import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.IGrade;
import io.github.oliviercailloux.grade.Mark;
import io.github.oliviercailloux.grade.WeightingGrade;
import io.github.oliviercailloux.grade.contexters.PomPathsSupplier;
import io.github.oliviercailloux.grade.format.CsvGrades;
import io.github.oliviercailloux.grade.format.json.JsonGrade;
import io.github.oliviercailloux.grade.markers.MarkingPredicates;
import io.github.oliviercailloux.grade.markers.Marks;
import io.github.oliviercailloux.grade.markers.TimePenalizer;
import io.github.oliviercailloux.grade.mycourse.json.StudentsReaderFromJson;
import io.github.oliviercailloux.java_grade.GraderOrchestrator;
import io.github.oliviercailloux.java_grade.testers.JavaMarkHelper;
import io.github.oliviercailloux.json.JsonbUtils;
import io.github.oliviercailloux.utils.Utils;

public class ExDepGitGraderSimpler {

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(ExDepGitGraderSimpler.class);

	private static final Instant DEADLINE = ZonedDateTime.parse("2019-05-20T16:07:00+02:00").toInstant();

	private static final TimePenalizer TIME_PENALIZER = TimePenalizer.linear(0.05d / 20d);
	private static final String PREFIX = "dep-git";

	private static final Path WORK_DIR = Paths.get("../../Java L3/");

	private static final Path OUT_DIR = Paths.get("");

	public static final ObjectId COMMIT_STARTING = ObjectId.fromString("38da901544294bf2b5784e4de1456905a306a341");
	public static final ObjectId COMMIT_FOLLOWING = ObjectId.fromString("bba2a8b6fce54999474702b733237c07070ef308");

	public static void main(String[] args) throws Exception {
		final ImmutableList<RepositoryCoordinatesWithPrefix> repositories;
		try (GitHubFetcherV3 fetcher = GitHubFetcherV3.using(GitHubToken.getRealInstance())) {
			repositories = fetcher.getRepositoriesWithPrefix("oliviercailloux-org", PREFIX);
		}

		final ExDepGitGraderSimpler grader = new ExDepGitGraderSimpler();
		final ImmutableMap<RepositoryCoordinates, IGrade> grades = repositories.stream()
				.collect(ImmutableMap.toImmutableMap(Function.identity(), r -> grader.grade(r)));

		Files.writeString(OUT_DIR.resolve("all grades " + PREFIX + ".json"),
				JsonbUtils.toJsonObject(grades, JsonGrade.asAdapter()).toString());

		final StudentsReaderFromJson usernames = new StudentsReaderFromJson();
		usernames.read(WORK_DIR.resolve("usernamesGH-manual.json"));
		Files.writeString(OUT_DIR.resolve("all grades " + PREFIX + ".csv"), CsvGrades.asCsv(grades.entrySet().stream()
				.filter(e -> e.getValue() instanceof WeightingGrade).collect(ImmutableMap.toImmutableMap(
						e -> usernames.getStudentOnGitHub(e.getKey().getOwner()), e -> (WeightingGrade) e.getValue())),
				7d));
	}

	ExDepGitGraderSimpler() {
		// Nothing.
	}

	public ImmutableMap<Instant, IGrade> getPossibleGrades(RepositoryCoordinates coord) throws IOException {
		final GitHubHistory gitHubHistory;
		try (GitHubFetcherQL fetcher = GitHubFetcherQL.using(GitHubToken.getRealInstance())) {
			gitHubHistory = fetcher.getGitHubHistory(coord);
		}
		final ImmutableGraph<ObjectId> patched = gitHubHistory.getPatchedKnowns();
		LOGGER.warn("Patched: {}.", patched);

		final Duration bound = TIME_PENALIZER.getTardinessBound();
		final Instant limit = DEADLINE.plus(bound).plus(Duration.ofMinutes(1));
		/**
		 * All out of this (generous) limit: this is not even to be evaluated; student
		 * is not submitting anything at that point but perhaps testing with her
		 * repository. This strategy also ensures that beyond that limit point,
		 * evaluations will not change.
		 */

		/**
		 * Consider those from last-before-the-deadline to last-before-limit; filter by
		 * excluding all other ones each in turn.
		 */
		final ImmutableSortedSet<Instant> pushedOnlyDates = ImmutableSortedSet
				.copyOf(gitHubHistory.getCorrectedAndCompletedPushedDates().values());
		final Instant start = Optional.ofNullable(pushedOnlyDates.floor(DEADLINE)).orElse(Instant.MIN);
		final ImmutableSortedSet<Instant> considered = pushedOnlyDates.subSet(start, true, limit, true);

		final Path projectsBaseDir = Paths.get("/home/olivier/Professions/Enseignement/En cours/dep-git");
		final Path projectDir = projectsBaseDir.resolve(coord.getRepositoryName());
		new GitCloner().download(GitUri.fromGitUri(coord.asURI()), projectDir);
		final ImmutableMap.Builder<Instant, IGrade> possibleGradesBuilder = ImmutableMap.builder();
		try (GitRepoFileSystem fs = new GitFileSystemProvider().newFileSystemFromGitDir(projectDir.resolve(".git"))) {
			for (Instant acceptUntil : considered) {
				final GitLocalHistory filtered = fs.getHistory()
						.filter(o -> !gitHubHistory.getCommitDate(o).isAfter(acceptUntil));
				possibleGradesBuilder.put(acceptUntil, grade(coord.getOwner(), fs, filtered));
				@SuppressWarnings("unused")
				final IGrade timeGrade = Marks.timeGrade(acceptUntil, DEADLINE, t -> 1d - TIME_PENALIZER.penalty(t));
				/**
				 * Should add timeMark with criterion ON_TIME to the received grade, and with
				 * weight −1.
				 */
			}
		}
		return possibleGradesBuilder.build();
	}

	public IGrade grade(RepositoryCoordinates coord) {
		final Path projectsBaseDir = WORK_DIR.resolve(PREFIX);
		final Path projectDir = projectsBaseDir.resolve(coord.getRepositoryName());
		new GitCloner().download(GitUri.fromGitUri(coord.asURI()), projectDir);

		try (GitRepoFileSystem fs = new GitFileSystemProvider().newFileSystemFromGitDir(projectDir.resolve(".git"))) {
			final GitHubHistory gitHubHistory = GraderOrchestrator.getGitHubHistory(coord);
			final GitLocalHistory filtered = GraderOrchestrator.getFilteredHistory(fs, gitHubHistory, DEADLINE);
			final IGrade grade = grade(coord.getOwner(), fs, filtered);
			LOGGER.info("Grade {}: {}.", coord, grade);
			return grade;
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	public IGrade grade(String owner, GitRepoFileSystem fs, GitLocalHistory history) throws IOException {
		fs.getHistory();
		LOGGER.debug("Graph history: {}.", history.getGraph().edges());
		final GitLocalHistory manual = history
				.filter(o -> !JavaMarkHelper.committerIsGitHub(fs.getCachedHistory().getCommit(o)));
		LOGGER.debug("Graph manual: {}.", manual.getGraph().edges());
		final Graph<ObjectId> graph = Utils.asGraph(n -> manual.getGraph().successors(manual.getCommit(n)),
				ImmutableSet.copyOf(manual.getTips()));
		LOGGER.debug("Graph copied from manual: {}.", graph.edges());

		if (graph.nodes().isEmpty()) {
			return Mark.zero("Found no manual commit (not counting commits from GitHub).");
		}

		final ImmutableMap.Builder<Criterion, IGrade> gradeBuilder = ImmutableMap.builder();

		final Predicate<ObjectId> mine = o -> JavaMarkHelper.committerAndAuthorIs(fs.getCachedHistory().getCommit(o),
				"Olivier Cailloux");
		final Predicate<ObjectId> notMine = mine.negate();
//		final Set<ObjectId> myCommits = Sets.filter(graph.nodes(), mine::test);
		final Set<ObjectId> notMyCommits = Sets.filter(graph.nodes(), notMine::test);

		final ImmutableList<ObjectId> ownWithCorrectIdentity = graph.nodes().stream()
				.filter(o -> JavaMarkHelper.committerAndAuthorIs(fs.getCachedHistory().getCommit(o), owner))
				.collect(ImmutableList.toImmutableList());
		final long ownWithCorrectIdentityCount = ownWithCorrectIdentity.size();
		gradeBuilder.put(COMMIT, Mark.binary(ownWithCorrectIdentityCount >= 1, "", ""));

		final Graph<ObjectId> closure = ImmutableGraph.copyOf(Graphs.transitiveClosure(graph));
		LOGGER.debug("Closure: {}.", closure.edges());
		final Optional<ObjectId> myBranch = fs.getCommitId(fs.getAbsolutePath("refs/heads/my-branch"));

		{
			IGrade firstCommitMark = Mark.zero();
			for (ObjectId commit : notMyCommits) {
				final boolean childOfStarting = closure.hasEdgeConnecting(commit, COMMIT_STARTING);
				final boolean parentOfMyBranch;
				if (myBranch.isPresent()) {
					parentOfMyBranch = closure.hasEdgeConnecting(myBranch.get(), commit);
				} else {
					parentOfMyBranch = false;
				}
				final Mark childGrade = childOfStarting ? Mark.one() : Mark.zero();
				final Mark parentGrade = Mark.binary(parentOfMyBranch, "", "");
				final WeightingGrade newMark = WeightingGrade.proportional(CHILD_OF_STARTING, childGrade,
						PARENT_OF_MY_BRANCH, parentGrade);
				if (newMark.getPoints() > firstCommitMark.getPoints()) {
					firstCommitMark = newMark;
				}
			}
			gradeBuilder.put(FIRST_COMMIT, firstCommitMark);
		}

		{
			Mark mergeCommitMark = Mark.zero();
			LOGGER.debug("Testing merge with {}.", notMyCommits);
			for (ObjectId commitId : notMyCommits) {
				final Set<ObjectId> parents = graph.successors(commitId);
				final ImmutableSet<ObjectId> parentsNotMine = parents.stream().filter(notMine)
						.collect(ImmutableSet.toImmutableSet());
				final boolean goodStart = parents.size() == 2 && parentsNotMine.size() == 1;

				final boolean hasExpectedParents;
				if (goodStart) {
					final ObjectId parent = parents.iterator().next();
					hasExpectedParents = parents.contains(COMMIT_FOLLOWING)
							&& graph.successors(parent).equals(ImmutableSet.of(COMMIT_STARTING));
				} else {
					hasExpectedParents = false;
				}
				final boolean childOfStarting = closure.hasEdgeConnecting(commitId, manual.getCommit(COMMIT_STARTING));
				assert Utils.implies(hasExpectedParents, childOfStarting);
				final double points = (goodStart && !hasExpectedParents) ? 1d / 4d : (hasExpectedParents ? 1d : 0d);
				final Mark newMark = Mark.given(points, "");
				LOGGER.debug("Tested merge with {}. Good start {}, exp par {}, child of st {}.", commitId, goodStart,
						hasExpectedParents, childOfStarting);
				if (newMark.getPoints() > mergeCommitMark.getPoints()) {
					mergeCommitMark = newMark;
				}
			}
			gradeBuilder.put(MERGE_COMMIT, mergeCommitMark);
		}

		{
			final ImmutableSortedSet<ObjectId> byCommitDate = graph.nodes().stream().filter(notMine)
					.collect(ImmutableSortedSet.toImmutableSortedSet(
							Comparator.comparing(o -> fs.getCachedHistory().getCommitDateById(o))));
			/**
			 * The pom can be put at any commit because the wording was not clear; so we’ll
			 * take the last one that has some tentative, if any.
			 */
			Mark depMark = Mark.zero("No dependency to jgit found");
			for (ObjectId commitId : byCommitDate.descendingSet()) {
				final ImmutableSet<Path> possibleMavenRoots = PomPathsSupplier
						.getPossiblePoms(fs.getAbsolutePath(commitId.getName()));
				final Pattern lastVersionPattern = Pattern.compile("<dependencies>" + Utils.ANY_REG_EXP + "<dependency>"
						+ Utils.ANY_REG_EXP + "<groupId>org\\.eclipse\\.jgit</groupId>" + Utils.ANY_REG_EXP
						+ "<artifactId>org\\.eclipse\\.jgit</artifactId>" + Utils.ANY_REG_EXP
						+ "<version>5\\.3\\.1\\.201904271842-r</version>" + "[\\h\\s]*" + "</dependency>");
				final Pattern wrongVersionPattern = Pattern.compile("<dependencies>" + Utils.ANY_REG_EXP
						+ "<dependency>" + Utils.ANY_REG_EXP + "<groupId>org\\.eclipse\\.jgit</groupId>"
						+ Utils.ANY_REG_EXP + "<artifactId>org\\.eclipse\\.jgit</artifactId>" + Utils.ANY_REG_EXP
						+ "<version>5\\.3\\.0\\.201903130848-r</version>" + "[\\h\\s]*" + "</dependency>");
				final boolean hasLast = possibleMavenRoots.stream()
						.allMatch(MarkingPredicates.pathContainsOnce(lastVersionPattern));
				final boolean hasWrong = possibleMavenRoots.stream()
						.allMatch(MarkingPredicates.pathContainsOnce(wrongVersionPattern));
				if (hasLast || hasWrong) {
					if (hasLast) {
						depMark = Mark.one();
					} else {
						depMark = Mark.given(1d / 2d, "Expected last version 5.3.1.201904271842-r");
					}
					break;
				}
			}
			gradeBuilder.put(DEP, depMark);
		}

		final ImmutableMap<Criterion, IGrade> subGrades = gradeBuilder.build();
		return WeightingGrade.from(subGrades, ImmutableMap.of(COMMIT, 1d, FIRST_COMMIT, 2d, MERGE_COMMIT, 2d, DEP, 2d));
	}
}
