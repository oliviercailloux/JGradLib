package io.github.oliviercailloux.java_grade.ex;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Verify;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.graph.Graph;

import io.github.oliviercailloux.git.GitCloner;
import io.github.oliviercailloux.git.GitLocalHistory;
import io.github.oliviercailloux.git.GitUri;
import io.github.oliviercailloux.git.fs.GitFileSystemProvider;
import io.github.oliviercailloux.git.fs.GitPath;
import io.github.oliviercailloux.git.fs.GitRepoFileSystem;
import io.github.oliviercailloux.git.git_hub.model.GitHubHistory;
import io.github.oliviercailloux.git.git_hub.model.GitHubToken;
import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinatesWithPrefix;
import io.github.oliviercailloux.git.git_hub.services.GitHubFetcherV3;
import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.CriterionGradeWeight;
import io.github.oliviercailloux.grade.IGrade;
import io.github.oliviercailloux.grade.Mark;
import io.github.oliviercailloux.grade.WeightingGrade;
import io.github.oliviercailloux.grade.format.json.JsonGrade;
import io.github.oliviercailloux.java_grade.GraderOrchestrator;
import io.github.oliviercailloux.java_grade.JavaCriterion;
import io.github.oliviercailloux.java_grade.testers.MarkHelper;
import io.github.oliviercailloux.java_grade.utils.Summarize;
import io.github.oliviercailloux.json.JsonbUtils;
import io.github.oliviercailloux.supann.QueriesHelper;
import io.github.oliviercailloux.utils.Utils;

public class GitBrGrader {
	public static enum GitBrCriterion implements Criterion {
		COMMIT_A, COMMIT_A_START, COMMIT_A_EXISTS, COMMIT_A_CONTENTS, COMMIT_B, COMMIT_B_START, COMMIT_B_EXISTS,
		COMMIT_B_CONTENTS, COMMIT_C, COMMIT_C_START, COMMIT_C_EXISTS, COMMIT_C_CONTENTS, COMMIT_D, COMMIT_D_START,
		COMMIT_D_EXISTS, COMMIT_D_CONTENTS, BR, BR_1, BR_2, BR_3;

		@Override
		public String getName() {
			return toString();
		}
	}

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(GitBrGrader.class);

	private static final String PREFIX = "git-br";

	private static final Instant DEADLINE = ZonedDateTime.parse("2020-03-04T08:52:00+01:00").toInstant();
	public static final ObjectId START = ObjectId.fromString("20361320acd7c4738b2a77a8fdb38b133efcfb30");

	private static final Path WORK_DIR = Paths.get("../../Java L3/");

	String branchPrefix;

	public static void main(String[] args) throws Exception {
		QueriesHelper.setDefaultAuthenticator();

		final ImmutableList<RepositoryCoordinatesWithPrefix> repositories;
		try (GitHubFetcherV3 fetcher = GitHubFetcherV3.using(GitHubToken.getRealInstance())) {
			repositories = fetcher.getRepositoriesWithPrefix("oliviercailloux-org", PREFIX);
		}

		final GitBrGrader grader = new GitBrGrader();
		final ImmutableMap<String, IGrade> grades = repositories.stream().collect(ImmutableMap
				.toImmutableMap(RepositoryCoordinatesWithPrefix::getUsername, Utils.uncheck(r -> grader.grade(r))));

		final Path outDir = WORK_DIR;
		Files.writeString(outDir.resolve("all grades " + PREFIX + ".json"),
				JsonbUtils.toJsonObject(grades, JsonGrade.asAdapter()).toString());

		Summarize.summarize(PREFIX, outDir);
	}

	GitBrGrader() {
		branchPrefix = "origin";
		// Nothing.
	}

	public IGrade grade(RepositoryCoordinatesWithPrefix coord) throws IOException, GitAPIException {
		final Path projectsBaseDir = WORK_DIR.resolve(PREFIX);
		final Path projectDir = projectsBaseDir.resolve(coord.getRepositoryName());
		new GitCloner().download(GitUri.fromGitUri(coord.asURI()), projectDir);

		try (GitRepoFileSystem fs = new GitFileSystemProvider().newFileSystemFromGitDir(projectDir.resolve(".git"))) {
			final GitHubHistory gitHubHistory = GraderOrchestrator.getGitHubHistory(coord);
			final GitLocalHistory filtered = GraderOrchestrator.getFilteredHistory(fs, gitHubHistory, DEADLINE);
			final IGrade grade = grade(coord.getUsername(), fs, filtered);
			LOGGER.info("Grade {}: {}.", coord, grade);
			return grade;
		}
	}

	public IGrade grade(String owner, GitRepoFileSystem fs, GitLocalHistory history) throws IOException {
		fs.getHistory();
		LOGGER.debug("Graph history: {}.", history.getGraph().edges());
		final GitLocalHistory manual = history
				.filter(o -> !MarkHelper.committerIsGitHub(fs.getCachedHistory().getCommit(o)));
		LOGGER.debug("Graph manual: {}.", manual.getGraph().edges());
		final Graph<ObjectId> graph = Utils.asGraph(n -> manual.getGraph().successors(manual.getCommit(n)),
				ImmutableSet.copyOf(manual.getTips()));
		LOGGER.debug("Graph copied from manual: {}.", graph.edges());

		if (graph.nodes().isEmpty()) {
			return Mark.zero("Found no manual commit (not counting commits from GitHub).");
		}

		final Set<RevCommit> unfiltered = fs.getHistory().getGraph().nodes();
		@SuppressWarnings("unlikely-arg-type")
		final boolean containsAll = unfiltered.containsAll(graph.nodes());
		Verify.verify(containsAll);

		final ImmutableMap.Builder<Criterion, IGrade> gradeBuilder = ImmutableMap.builder();

		final ImmutableList<ObjectId> ownWithCorrectIdentity = graph.nodes().stream()
				.filter(o -> MarkHelper.committerAndAuthorIs(fs.getCachedHistory().getCommit(o), owner))
				.collect(ImmutableList.toImmutableList());
		gradeBuilder.put(JavaCriterion.ID, Mark.binary(ownWithCorrectIdentity.size() >= 1));

		if (!graph.nodes().containsAll(ownWithCorrectIdentity)) {
			LOGGER.warn("Risk of accessing a commit beyond deadline (e.g. master!).");
		}

		final ImmutableSet<ObjectId> ownSet = ImmutableSet.copyOf(ownWithCorrectIdentity);

		final Optional<ObjectId> br1 = fs.getCommitId(fs.getAbsolutePath(branchPrefix + "/br1"))
				.filter(o -> ownSet.contains(o));
		final Optional<ObjectId> br2 = fs.getCommitId(fs.getAbsolutePath(branchPrefix + "/br2"))
				.filter(o -> ownSet.contains(o));
		final Optional<ObjectId> br3 = fs.getCommitId(fs.getAbsolutePath(branchPrefix + "/br3"))
				.filter(o -> ownSet.contains(o));

//		final MutableGraph<ObjectId> ownGraph = Graphs.inducedSubgraph(graph, ownWithCorrectIdentity);
		final Set<ObjectId> startCandidates = new LinkedHashSet<>();
		final GitLocalHistory ownHistory = history.filter(c -> ownSet.contains(c));
		final ImmutableSet<RevCommit> roots = ownHistory.getRoots();
		LOGGER.debug("Roots: {}.", roots);
		ImmutableSet<ObjectId> parentsOfOwnRoots = roots.stream().flatMap(o -> graph.successors(o).stream())
				.collect(ImmutableSet.toImmutableSet());
		if (parentsOfOwnRoots.size() == 1) {
			startCandidates.add(parentsOfOwnRoots.iterator().next());
		}
		final Set<ObjectId> parentsOfBr1 = br1.map(graph::successors).orElse(ImmutableSet.of());
		if (parentsOfBr1.size() == 1) {
			startCandidates.add(parentsOfBr1.iterator().next());
		}
		Verify.verify(startCandidates.size() != 2);
		startCandidates.add(START);
		final ObjectId effectiveStart = startCandidates.iterator().next();
		final boolean startedAtStart = effectiveStart.equals(START);

		final Set<ObjectId> childrenOfStart = Sets.intersection(graph.predecessors(effectiveStart), ownSet);
		final Optional<ObjectId> commitA;
		if (br1.isPresent()) {
			commitA = br1;
		} else {
			final ImmutableSet<ObjectId> matchingForA = childrenOfStart.stream().filter(o -> matchesCommitA(fs, o))
					.collect(ImmutableSet.toImmutableSet());
			if (matchingForA.size() != 1) {
				final ImmutableSet<ObjectId> mightBeA;
				if (matchingForA.size() > 1) {
					mightBeA = matchingForA;
					LOGGER.warn("Multiple candidates for A (all matching): {}.", mightBeA);
				} else {
					mightBeA = ImmutableSet.copyOf(childrenOfStart);
					if (mightBeA.size() > 1) {
						LOGGER.warn("Multiple candidates for A (none matching): {}.", mightBeA);
					}
				}
				commitA = mightBeA.isEmpty() ? Optional.empty() : Optional.of(mightBeA.iterator().next());
			} else {
				commitA = Optional.of(matchingForA.iterator().next());
			}
		}
		Verify.verify(Utils.implies(commitA.isEmpty(), childrenOfStart.isEmpty()), String.format(
				"Commit A: %s; childrenOfStart: %s, effective start: %s.", commitA, childrenOfStart, effectiveStart));

		final boolean contentA;
		if (commitA.isPresent()) {
			contentA = matchesCommitA(fs, commitA.get());
		} else {
			contentA = false;
		}

		final WeightingGrade gradeA = WeightingGrade.from(ImmutableList.of(
				CriterionGradeWeight.from(GitBrCriterion.COMMIT_A_START,
						Mark.binary(startedAtStart && commitA.isPresent()), 0.2d),
				CriterionGradeWeight.from(GitBrCriterion.COMMIT_A_EXISTS, Mark.binary(commitA.isPresent()), 0.4d),
				CriterionGradeWeight.from(GitBrCriterion.COMMIT_A_CONTENTS, Mark.binary(contentA), 0.4d)));
		gradeBuilder.put(GitBrCriterion.COMMIT_A, gradeA);

		final Set<ObjectId> childrenOfStartNotA = commitA.isEmpty() ? childrenOfStart
				: Sets.difference(childrenOfStart, ImmutableSet.of(commitA.get()));
		final Optional<ObjectId> commitB = childrenOfStartNotA.isEmpty() ? Optional.empty()
				: Optional.of(childrenOfStartNotA.iterator().next());
		Verify.verify(commitB.isEmpty() == (childrenOfStart.isEmpty() || childrenOfStart.size() == 1));

		final boolean contentB;
		if (commitB.isPresent()) {
			contentB = matchesCommitB(fs, commitB.get());
		} else {
			contentB = false;
		}

		final WeightingGrade gradeB = WeightingGrade.from(ImmutableList.of(
				CriterionGradeWeight.from(GitBrCriterion.COMMIT_B_START,
						Mark.binary(startedAtStart && commitB.isPresent()), 0.2d),
				CriterionGradeWeight.from(GitBrCriterion.COMMIT_B_EXISTS, Mark.binary(commitB.isPresent()), 0.4d),
				CriterionGradeWeight.from(GitBrCriterion.COMMIT_B_CONTENTS, Mark.binary(contentB), 0.4d)));
		gradeBuilder.put(GitBrCriterion.COMMIT_B, gradeB);

		final Optional<RevCommit> revCommitB = commitB.map(history::getCommit);
		final Set<RevCommit> childrenOfB = revCommitB.map(c -> history.getGraph().predecessors(c))
				.orElse(ImmutableSet.of());
		final Optional<ObjectId> commitC;
		if (childrenOfB.size() == 1) {
			commitC = Optional.of(childrenOfB.iterator().next());
		} else if (childrenOfB.size() > 1) {
			LOGGER.warn("Multiple candidates for C: {}.", childrenOfB);
			commitC = Optional.empty();
		} else {
			commitC = Optional.empty();
		}

		final boolean contentC;
		if (commitC.isPresent()) {
			contentC = matchesCommitC(fs, commitC.get());
		} else {
			contentC = false;
		}

		final WeightingGrade gradeC = WeightingGrade.from(ImmutableList.of(
				CriterionGradeWeight.from(GitBrCriterion.COMMIT_C_START,
						Mark.binary(startedAtStart && commitC.isPresent()), 0.2d),
				CriterionGradeWeight.from(GitBrCriterion.COMMIT_C_EXISTS, Mark.binary(commitC.isPresent()), 0.4d),
				CriterionGradeWeight.from(GitBrCriterion.COMMIT_C_CONTENTS, Mark.binary(contentC), 0.4d)));
		gradeBuilder.put(GitBrCriterion.COMMIT_C, gradeC);

		final Optional<RevCommit> revCommitC = commitC.map(history::getCommit);
		final Set<RevCommit> childrenOfC = revCommitC.map(c -> history.getGraph().predecessors(c))
				.orElse(ImmutableSet.of());
		final Optional<RevCommit> revCommitA = commitA.map(history::getCommit);
		final Set<RevCommit> childrenOfA = revCommitA.map(c -> history.getGraph().predecessors(c))
				.orElse(ImmutableSet.of());

		final Set<RevCommit> candidatesD = Sets.intersection(childrenOfC, childrenOfA);
		final Optional<ObjectId> commitD;
		if (candidatesD.size() == 1) {
			commitD = Optional.of(candidatesD.iterator().next());
		} else if (candidatesD.size() > 1) {
			LOGGER.warn("Multiple candidates for D: {}.", candidatesD);
			commitD = Optional.empty();
		} else {
			commitD = Optional.empty();
		}

		final boolean contentD;
		if (commitD.isPresent()) {
			contentD = matchesCommitD(fs, commitD.get());
		} else {
			contentD = false;
		}

		final WeightingGrade gradeD = WeightingGrade.from(ImmutableList.of(
				CriterionGradeWeight.from(GitBrCriterion.COMMIT_D_START,
						Mark.binary(startedAtStart && commitD.isPresent()), 0.2d),
				CriterionGradeWeight.from(GitBrCriterion.COMMIT_D_EXISTS, Mark.binary(commitD.isPresent()), 0.4d),
				CriterionGradeWeight.from(GitBrCriterion.COMMIT_D_CONTENTS, Mark.binary(contentD), 0.4d)));
		gradeBuilder.put(GitBrCriterion.COMMIT_D, gradeD);

		final WeightingGrade gradeBranches = WeightingGrade.proportional(GitBrCriterion.BR_1,
				Mark.binary(br1.isPresent() && br1.equals(commitA)), GitBrCriterion.BR_2,
				Mark.binary(br2.isPresent() && br2.equals(commitC)), GitBrCriterion.BR_3,
				Mark.binary(br3.isPresent() && br3.equals(commitD)));
		gradeBuilder.put(GitBrCriterion.BR, gradeBranches);

		final ImmutableMap<Criterion, IGrade> subGrades = gradeBuilder.build();
		final ImmutableMap.Builder<Criterion, Double> builder = ImmutableMap.builder();
		builder.put(JavaCriterion.ID, 1d);
		builder.put(GitBrCriterion.COMMIT_A, 2d);
		builder.put(GitBrCriterion.COMMIT_B, 2d);
		builder.put(GitBrCriterion.COMMIT_C, 2d);
		builder.put(GitBrCriterion.COMMIT_D, 2d);
		builder.put(GitBrCriterion.BR, 4d);
		return WeightingGrade.from(subGrades, builder.build());
	}

	private boolean matchesCommitA(GitRepoFileSystem fs, ObjectId commit) {
		final GitPath fileA = fs.getAbsolutePath(commit.getName(), "hello.txt");
		return Files.exists(fileA)
				&& Utils.getOrThrowIO(() -> Files.readString(fileA)).replace("\"", "").strip().equals("first try");
	}

	private boolean matchesCommitB(GitRepoFileSystem fs, ObjectId commit) {
		final GitPath fileB = fs.getAbsolutePath(commit.getName(), "hello.txt");
		return Files.exists(fileB)
				&& Utils.getOrThrowIO(() -> Files.readString(fileB)).replace("\"", "").strip().equals("second try");
	}

	private boolean matchesCommitC(GitRepoFileSystem fs, ObjectId commit) {
		final GitPath fileC = fs.getAbsolutePath(commit.getName(), "supplements.txt");
		return Files.exists(fileC)
				&& Utils.getOrThrowIO(() -> Files.readString(fileC)).replace("\"", "").strip().equals("Hello, world");
	}

	private boolean matchesCommitD(GitRepoFileSystem fs, ObjectId commit) {
		final GitPath fileD = fs.getAbsolutePath(commit.getName(), "hello.txt");
		return Files.exists(fileD) && Utils.getOrThrowIO(() -> Files.readString(fileD)).replace("\"", "").strip()
				.equals("first try\nsecond try");
	}

}
