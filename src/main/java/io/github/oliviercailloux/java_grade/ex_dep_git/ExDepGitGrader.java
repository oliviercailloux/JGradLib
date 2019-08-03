package io.github.oliviercailloux.java_grade.ex_dep_git;

import static io.github.oliviercailloux.java_grade.ex_dep_git.ExDepGitCriterion.CHILD_OF_STARTING;
import static io.github.oliviercailloux.java_grade.ex_dep_git.ExDepGitCriterion.COMMIT;
import static io.github.oliviercailloux.java_grade.ex_dep_git.ExDepGitCriterion.DEP;
import static io.github.oliviercailloux.java_grade.ex_dep_git.ExDepGitCriterion.FIRST_COMMIT;
import static io.github.oliviercailloux.java_grade.ex_dep_git.ExDepGitCriterion.MERGE_COMMIT;
import static io.github.oliviercailloux.java_grade.ex_dep_git.ExDepGitCriterion.ON_TIME;
import static io.github.oliviercailloux.java_grade.ex_dep_git.ExDepGitCriterion.PARENT_OF_MY_BRANCH;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.graph.Graph;
import com.google.common.graph.Graphs;
import com.google.common.graph.ImmutableGraph;

import io.github.oliviercailloux.git.Checkouter;
import io.github.oliviercailloux.git.Client;
import io.github.oliviercailloux.git.GitUtils;
import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinates;
import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.GraderOrchestrator;
import io.github.oliviercailloux.grade.GradingException;
import io.github.oliviercailloux.grade.IGrade;
import io.github.oliviercailloux.grade.Mark;
import io.github.oliviercailloux.grade.WeightingGrade;
import io.github.oliviercailloux.grade.comm.StudentOnGitHub;
import io.github.oliviercailloux.grade.context.FilesSource;
import io.github.oliviercailloux.grade.context.GitFullContext;
import io.github.oliviercailloux.grade.contexters.FullContextInitializer;
import io.github.oliviercailloux.grade.format.CsvGrades;
import io.github.oliviercailloux.grade.format.json.JsonGrade;
import io.github.oliviercailloux.grade.markers.MarkingPredicates;
import io.github.oliviercailloux.grade.markers.Marks;
import io.github.oliviercailloux.java_grade.testers.MarkHelper;
import io.github.oliviercailloux.json.JsonbUtils;
import io.github.oliviercailloux.utils.Utils;

public class ExDepGitGrader {

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(ExDepGitGrader.class);
	private static final Instant DEADLINE = ZonedDateTime.parse("2019-05-20T16:07:00+02:00").toInstant();

	public static void main(String[] args) throws Exception {
		final String prefix = "dep-git";
		final GraderOrchestrator orch = new GraderOrchestrator(prefix);
		final Path srcDir = Paths.get("../../Java L3/");
		final Path outDir = Paths.get("");
		orch.readUsernames(srcDir.resolve("usernamesGH-manual.json"));

		orch.readRepositories();
		orch.setSingleRepo("BoBeauf");

		final ImmutableMap<StudentOnGitHub, RepositoryCoordinates> repositories = orch.getRepositoriesByStudent();

		final ExDepGitGrader grader = new ExDepGitGrader();

		final ImmutableMap<StudentOnGitHub, WeightingGrade> grades = repositories.entrySet().stream()
				.collect(ImmutableMap.toImmutableMap(Entry::getKey, (e) -> grader.grade(e.getValue())));

		LOGGER.info("Grades: {}.", grades);

		Files.writeString(outDir.resolve("all grades " + prefix + ".json"),
				JsonbUtils.toJsonObject(grades, JsonGrade.asAdapter()).toString());
		Files.writeString(outDir.resolve("all grades " + prefix + ".csv"), CsvGrades.asCsv(grades, 7d));
	}

	public ExDepGitGrader() {
		timeMark = null;
		fullContext = null;
		commitsReceptionTime = null;
		commitsManual = null;
	}

	private IGrade timeMark;
	private GitFullContext fullContext;
	private ImmutableMap<ObjectId, Instant> commitsReceptionTime;
	private ImmutableList<RevCommit> commitsManual;

	public WeightingGrade grade(RepositoryCoordinates coord) {
		final ImmutableMap.Builder<Criterion, IGrade> gradeBuilder = ImmutableMap.builder();
		final Path projectsBaseDir = Paths.get("/home/olivier/Professions/Enseignement/En cours/dep-git");

		final FullContextInitializer spec = (FullContextInitializer) FullContextInitializer.withPath(coord,
				projectsBaseDir);
		commitsReceptionTime = spec.getCommitsReceptionTime();
		fullContext = spec;
		final Client client = fullContext.getClient();
		final Optional<RevCommit> mainCommit = fullContext.getMainCommit();
		LOGGER.info("Main commit: {}.", mainCommit);
		if (mainCommit.isPresent()) {
			final Checkouter co = Checkouter.aboutAndUsing(coord, projectsBaseDir);
			try {
				co.checkout(mainCommit.get());
			} catch (IOException | GitAPIException e) {
				throw new GradingException(e);
			}
		}

		timeMark = Marks.timeGrade(fullContext, DEADLINE, this::getTimeScore);
		gradeBuilder.put(ON_TIME, timeMark);
		gradeBuilder.put(COMMIT, commitMark());

		final RevCommit startingCommit;
		final RevCommit followingCommit;
		try {
			startingCommit = client.getCommit(ObjectId.fromString("38da901544294bf2b5784e4de1456905a306a341"));
			followingCommit = client.getCommit(ObjectId.fromString("bba2a8b6fce54999474702b733237c07070ef308"));
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}

//		try {
//			fullContext.getClient().getAllBranches().stream().filter((r)->r.getName().equals("refs/remotes/origin/my-branch"));
//		} catch (IOException | GitAPIException e) {
//			throw new IllegalStateException(e);
//		}
		final ImmutableGraph<RevCommit> graph = client.getAllHistoryCached().getGraph();
		final Graph<RevCommit> closure = Graphs.transitiveClosure(graph);

		final Optional<RevCommit> myBranchOpt = tryParseSpec(client, "refs/remotes/origin/my-branch");
		{
			IGrade firstCommitMark = Mark.zero();
			for (RevCommit commit : commitsManual) {
				final boolean childOfStarting = closure.hasEdgeConnecting(commit, startingCommit);
				final boolean parentOfMyBranch;
				if (myBranchOpt.isPresent()) {
					final RevCommit myBranch = myBranchOpt.get();
					parentOfMyBranch = closure.hasEdgeConnecting(myBranch, commit);
				} else {
					parentOfMyBranch = false;
				}
				final WeightingGrade newMark = WeightingGrade.proportional(CHILD_OF_STARTING,
						Mark.ifPasses(childOfStarting), PARENT_OF_MY_BRANCH, Mark.ifPasses(parentOfMyBranch));
				if (newMark.getPoints() > firstCommitMark.getPoints()) {
					firstCommitMark = newMark;
				}
			}
			gradeBuilder.put(FIRST_COMMIT, firstCommitMark);
		}

		{
			Mark mergeCommitMark = Mark.zero();
			for (RevCommit commit : commitsManual) {
				final Set<RevCommit> successors = graph.successors(commit);
				final ImmutableSet<RevCommit> manualParents = successors.stream().filter(Predicates.in(commitsManual))
						.collect(ImmutableSet.toImmutableSet());
				final boolean goodStart = successors.size() == 2 && manualParents.size() == 1;

				final boolean hasExpectedParents;
				if (goodStart) {
					final RevCommit manualParent = manualParents.iterator().next();
					hasExpectedParents = successors.contains(followingCommit)
							&& graph.successors(manualParent).equals(ImmutableSet.of(startingCommit));
				} else {
					hasExpectedParents = false;
				}
				final boolean childOfStarting = closure.hasEdgeConnecting(commit, startingCommit);
				assert Utils.implies(hasExpectedParents, childOfStarting);
				final double points = (goodStart && !hasExpectedParents) ? 1d / 4d : (hasExpectedParents ? 1d : 0d);
				final Mark newMark = Mark.given(points, "");
				if (newMark.getPoints() > mergeCommitMark.getPoints()) {
					mergeCommitMark = newMark;
				}
			}
			gradeBuilder.put(MERGE_COMMIT, mergeCommitMark);
		}

		{
			Mark depMark = Mark.zero("No dependency to jgit found");
			Instant latestFound = Instant.MIN;
			for (RevCommit commit : commitsManual) {
				final String pom;
				final FilesSource filesReader = fullContext.getFilesReader(Optional.of(commit));
				pom = filesReader.getContent(Path.of("pom.xml"));
				final Predicate<CharSequence> lastVersion = MarkingPredicates
						.containsOnce(Pattern.compile("<dependencies>" + Utils.ANY_REG_EXP + "<dependency>"
								+ Utils.ANY_REG_EXP + "<groupId>org\\.eclipse\\.jgit</groupId>" + Utils.ANY_REG_EXP
								+ "<artifactId>org\\.eclipse\\.jgit</artifactId>" + Utils.ANY_REG_EXP
								+ "<version>5\\.3\\.1\\.201904271842-r</version>" + "[\\h\\s]*" + "</dependency>"));
				final Predicate<CharSequence> wrongVersion = MarkingPredicates
						.containsOnce(Pattern.compile("<dependencies>" + Utils.ANY_REG_EXP + "<dependency>"
								+ Utils.ANY_REG_EXP + "<groupId>org\\.eclipse\\.jgit</groupId>" + Utils.ANY_REG_EXP
								+ "<artifactId>org\\.eclipse\\.jgit</artifactId>" + Utils.ANY_REG_EXP
								+ "<version>5\\.3\\.0\\.201903130848-r</version>" + "[\\h\\s]*" + "</dependency>"));
				final boolean hasLast = lastVersion.test(pom);
				final boolean hasWrong = wrongVersion.test(pom);
				if (hasLast || hasWrong) {
					final Instant thisTime = commitsReceptionTime.get(commit);
					if (thisTime.compareTo(latestFound) > 0) {
						latestFound = thisTime;
						if (hasLast) {
							depMark = Mark.one();
						} else {
							depMark = Mark.given(1d / 2d, "Expected last version 5.3.1.201904271842-r");
						}
					}
				}
			}
			gradeBuilder.put(DEP, depMark);
		}

		final ImmutableMap<Criterion, IGrade> subGrades = gradeBuilder.build();
		return WeightingGrade.from(subGrades,
				ImmutableMap.of(ON_TIME, -1d, COMMIT, 1d, FIRST_COMMIT, 2d, MERGE_COMMIT, 2d, DEP, 2d));
	}

	IGrade commitMark() {
		final Client client = fullContext.getClient();
		final Set<RevCommit> commits;
		try {
			commits = client.getAllCommits();
		} catch (IOException | GitAPIException e) {
			throw new IllegalStateException(e);
		}
		final ImmutableList<ZonedDateTime> commitDeclaredTimes = commits.stream().map(GitUtils::getCreationTime)
				.collect(ImmutableList.toImmutableList());
		LOGGER.debug("Times: {}.", commitDeclaredTimes);
		LOGGER.debug("Real times: {}.", commitsReceptionTime.values());
		final ImmutableList<RevCommit> commitsOwn = commits.stream()
				.filter((c) -> !c.getAuthorIdent().getName().equals("Olivier Cailloux"))
				.collect(ImmutableList.toImmutableList());
		LOGGER.info("All: {}; own: {}.", toOIds(commits), toOIds(commitsOwn));
		final Predicate<? super RevCommit> byGH = MarkHelper::committerIsGitHub;
		LOGGER.info("GH: {}.", toOIds(commits.stream().filter(byGH).collect(ImmutableList.toImmutableList())));
		commitsManual = commitsOwn.stream().filter(byGH.negate()).collect(ImmutableList.toImmutableList());
		final String comment = (!commitsOwn.isEmpty() ? "Own: " + toOIds(commitsOwn) + ". " : "")
				+ (!commitsManual.isEmpty() ? "Using git: " + toOIds(commitsManual) : "No commits using git");
		final double points = (!commitsManual.isEmpty()) ? 1d : 0d;
		final Mark commitMark = Mark.given(points, comment);
		return commitMark;
	}

	double getTimeScore(Duration tardiness) {
		LOGGER.debug("Tardiness: {}.", tardiness);
		final long secondsLate = tardiness.toSeconds();
		final double penalty = Math.min(1d, 0.05d / 20d * secondsLate);
		return 1d - penalty;
	}

	private ImmutableList<String> toOIds(Collection<RevCommit> commits) {
		return commits.stream().map(RevCommit::getName).collect(ImmutableList.toImmutableList());
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
}
