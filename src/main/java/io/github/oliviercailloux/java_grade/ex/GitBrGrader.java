package io.github.oliviercailloux.java_grade.ex;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Verify;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.graph.ImmutableGraph;

import io.github.oliviercailloux.git.GitCloner;
import io.github.oliviercailloux.git.GitLocalHistory;
import io.github.oliviercailloux.git.GitUri;
import io.github.oliviercailloux.git.fs.GitFileSystem;
import io.github.oliviercailloux.git.fs.GitFileSystemProvider;
import io.github.oliviercailloux.git.fs.GitPath;
import io.github.oliviercailloux.git.fs.GitPathRoot;
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
import io.github.oliviercailloux.grade.markers.Marks;
import io.github.oliviercailloux.java_grade.GraderOrchestrator;
import io.github.oliviercailloux.java_grade.JavaCriterion;
import io.github.oliviercailloux.java_grade.testers.JavaMarkHelper;
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
//		repositories = ImmutableList
//				.of(RepositoryCoordinatesWithPrefix.from("oliviercailloux-org", PREFIX, "…"));

		final GitBrGrader grader = new GitBrGrader();
		final Map<String, IGrade> gradesB = new LinkedHashMap<>();
		for (RepositoryCoordinatesWithPrefix repository : repositories) {
			gradesB.put(repository.getUsername(), grader.grade(repository));
			final Path outDir = WORK_DIR;
			Files.writeString(outDir.resolve("all grades " + PREFIX + ".json"),
					JsonbUtils.toJsonObject(gradesB, JsonGrade.asAdapter()).toString());
			Summarize.summarize(PREFIX, outDir);
		}
	}

	GitBrGrader() {
		branchPrefix = "origin";
	}

	public IGrade grade(RepositoryCoordinatesWithPrefix coord) throws IOException {
		final Path projectsBaseDir = WORK_DIR.resolve(PREFIX);
		final Path projectDir = projectsBaseDir.resolve(coord.getRepositoryName());
		new GitCloner().download(GitUri.fromUri(coord.asURI()), projectDir);

		try (GitFileSystem fs = GitFileSystemProvider.getInstance()
				.newFileSystemFromGitDir(projectDir.resolve(".git"))) {
			final GitHubHistory gitHubHistory = GraderOrchestrator.getGitHubHistory(coord);
			final IGrade grade = grade(coord.getUsername(), fs, gitHubHistory);
			LOGGER.info("Grade {}: {}.", coord, grade);
			return grade;
		}
	}

	public IGrade grade(String owner, GitFileSystem fs, GitHubHistory gitHubHistory) throws IOException {
		final GitLocalHistory filtered = GraderOrchestrator.getFilteredHistory(fs, gitHubHistory, DEADLINE);
		final Set<ObjectId> keptIds = ImmutableSet.copyOf(filtered.getGraph().nodes());
		final Set<ObjectId> allIds = gitHubHistory.getGraph().nodes();
		Verify.verify(allIds.containsAll(keptIds));
		final Set<ObjectId> excludedIds = Sets.difference(allIds, keptIds);
		final String comment;
		if (excludedIds.isEmpty()) {
			comment = "";
		} else {
			comment = "Excluded the following commits (pushed too late): "
					+ excludedIds.stream()
							.map(o -> o.getName().substring(0, 7) + " (" + gitHubHistory
									.getCorrectedAndCompletedPushedDates().get(o).atZone(ZoneId.systemDefault()) + ")")
							.collect(Collectors.joining("; "));
		}

		LOGGER.debug("Graph filtered history: {}.", filtered.getGraph().edges());
		fs.getHistory();
		final GitLocalHistory manual = filtered
				.filter(o -> !JavaMarkHelper.committerIsGitHub(fs.getCachedHistory().getCommit(o)));
		LOGGER.debug("Graph manual: {}.", manual.getGraph().edges());
		final ImmutableGraph<ObjectId> graph = Utils.asImmutableGraph(manual.getGraph(), o -> o);
		LOGGER.debug("Graph copied from manual: {}.", graph.edges());

		if (graph.nodes().isEmpty()) {
			return Mark.zero("Found no manual commit (not counting commits from GitHub).");
		}

		final Set<RevCommit> unfiltered = fs.getHistory().getGraph().nodes();
		@SuppressWarnings("unlikely-arg-type")
		final boolean containsAll = unfiltered.containsAll(graph.nodes());
		Verify.verify(containsAll);

		final ImmutableMap.Builder<Criterion, IGrade> gradeBuilder = ImmutableMap.builder();

		final GitLocalHistory ownHistory = filtered
				.filter(o -> JavaMarkHelper.committerAndAuthorIs(fs.getCachedHistory().getCommit(o), owner));
		final ImmutableGraph<RevCommit> ownGraph = ownHistory.getGraph();
		gradeBuilder.put(JavaCriterion.ID, Mark.binary(ownGraph.nodes().size() >= 1));

		if (!graph.nodes().containsAll(ownGraph.nodes())) {
			LOGGER.warn("Risk of accessing a commit beyond deadline (e.g. master!).");
		}

		@SuppressWarnings("unlikely-arg-type")
		final Optional<ObjectId> br1 = getObjectId(fs.getPathRoot(branchPrefix + "/br1"))
				.filter(o -> ownGraph.nodes().contains(o));
		@SuppressWarnings("unlikely-arg-type")
		final Optional<ObjectId> br2 = getObjectId(fs.getPathRoot(branchPrefix + "/br2"))
				.filter(o -> ownGraph.nodes().contains(o));
		@SuppressWarnings("unlikely-arg-type")
		final Optional<ObjectId> br3 = getObjectId(fs.getPathRoot(branchPrefix + "/br3"))
				.filter(o -> ownGraph.nodes().contains(o));

		final Set<ObjectId> startCandidates = new LinkedHashSet<>();
		final ImmutableSet<RevCommit> roots = ownHistory.getRoots();
		LOGGER.info("Roots: {}.", roots);
		ImmutableSet<ObjectId> parentsOfOwnRoots = roots.stream()
				.flatMap(o -> filtered.getGraph().successors(o).stream()).collect(ImmutableSet.toImmutableSet());
		LOGGER.info("Parents of roots: {}.", parentsOfOwnRoots);
		if (parentsOfOwnRoots.size() == 1) {
			startCandidates.add(parentsOfOwnRoots.iterator().next());
		}
		final Set<ObjectId> parentsOfBr1 = br1.map(graph::successors).orElse(ImmutableSet.of());
		if (parentsOfBr1.size() == 1) {
			startCandidates.add(parentsOfBr1.iterator().next());
		}
		checkArgument(startCandidates.size() < 2);
		startCandidates.add(START);
		LOGGER.info("Start candidates: {}.", startCandidates);
		final ObjectId effectiveStart = startCandidates.iterator().next();
		final boolean startedAtStart = effectiveStart.equals(START);

		final RevCommit effectiveStartRev = filtered.getCommit(effectiveStart);
		final Set<RevCommit> predecessorsOfEffectiveStart = filtered.getGraph().predecessors(effectiveStartRev);
		final Set<ObjectId> childrenOfStart = ImmutableSet
				.copyOf(Sets.intersection(predecessorsOfEffectiveStart, ownGraph.nodes()));
		final Optional<ObjectId> commitA;
		if (br1.isPresent()) {
			commitA = br1;
			@SuppressWarnings("unlikely-arg-type")
			final boolean contained = ownGraph.nodes().contains(commitA.get());
			Verify.verify(contained);
			@SuppressWarnings("unlikely-arg-type")
			final boolean containsA = predecessorsOfEffectiveStart.contains(commitA.get());
			checkArgument(containsA);
			Verify.verify(childrenOfStart.contains(commitA.get()));
		} else {
			final ImmutableSet<ObjectId> matchingForA = childrenOfStart.stream()
					.filter(o -> getAContentGrade(fs, Optional.of(o)).getPoints() >= 0.9d)
					.collect(ImmutableSet.toImmutableSet());
			final ImmutableSet<ObjectId> matchingForB = childrenOfStart.stream()
					.filter(o -> getBContentGrade(fs, Optional.of(o)).getPoints() >= 0.9d)
					.collect(ImmutableSet.toImmutableSet());
			Verify.verify(Sets.intersection(matchingForA, matchingForB).isEmpty());

			if (matchingForA.size() != 1) {
				final ImmutableSet<ObjectId> mightBeA;
				if (matchingForA.size() > 1) {
					mightBeA = matchingForA;
					LOGGER.warn("Multiple candidates for A (all matching): {}.", mightBeA);
				} else {
					mightBeA = ImmutableSet.copyOf(Sets.difference(childrenOfStart, matchingForB));
					if (mightBeA.size() > 1) {
						LOGGER.warn("Multiple candidates for A (none matching): {}.", mightBeA);
					}
				}
				commitA = mightBeA.isEmpty() ? Optional.empty() : Optional.of(mightBeA.iterator().next());
			} else {
				commitA = Optional.of(matchingForA.iterator().next());
			}
		}
		Verify.verify(Utils.implies(commitA.isEmpty(), childrenOfStart.isEmpty() || childrenOfStart.size() == 1),
				String.format("Commit A: %s; childrenOfStart: %s, effective start: %s.", commitA, childrenOfStart,
						effectiveStart));

		final WeightingGrade gradeA = WeightingGrade.from(ImmutableList.of(
				CriterionGradeWeight.from(GitBrCriterion.COMMIT_A_START,
						Mark.binary(startedAtStart && commitA.isPresent()), 0.2d),
				CriterionGradeWeight.from(GitBrCriterion.COMMIT_A_EXISTS, Mark.binary(commitA.isPresent()), 0.4d),
				CriterionGradeWeight.from(GitBrCriterion.COMMIT_A_CONTENTS, getAContentGrade(fs, commitA), 0.4d)));
		gradeBuilder.put(GitBrCriterion.COMMIT_A, gradeA);

		final Set<ObjectId> childrenOfStartNotA = commitA.isEmpty() ? childrenOfStart
				: Sets.difference(childrenOfStart, ImmutableSet.of(commitA.get()));
		final Optional<ObjectId> commitB = childrenOfStartNotA.isEmpty() ? Optional.empty()
				: Optional.of(childrenOfStartNotA.iterator().next());
		Verify.verify(Utils.implies(commitB.isEmpty(), childrenOfStart.isEmpty() || childrenOfStart.size() == 1));

		final WeightingGrade gradeB = WeightingGrade.from(ImmutableList.of(
				CriterionGradeWeight.from(GitBrCriterion.COMMIT_B_START,
						Mark.binary(startedAtStart && commitB.isPresent()), 0.2d),
				CriterionGradeWeight.from(GitBrCriterion.COMMIT_B_EXISTS, Mark.binary(commitB.isPresent()), 0.4d),
				CriterionGradeWeight.from(GitBrCriterion.COMMIT_B_CONTENTS, getBContentGrade(fs, commitB), 0.4d)));
		gradeBuilder.put(GitBrCriterion.COMMIT_B, gradeB);

		final Optional<RevCommit> revCommitB = commitB.map(filtered::getCommit);
		final Set<RevCommit> childrenOfB = revCommitB.map(c -> filtered.getGraph().predecessors(c))
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

		final WeightingGrade gradeC = WeightingGrade.from(ImmutableList.of(
				CriterionGradeWeight.from(GitBrCriterion.COMMIT_C_START,
						Mark.binary(startedAtStart && commitC.isPresent()), 0.2d),
				CriterionGradeWeight.from(GitBrCriterion.COMMIT_C_EXISTS, Mark.binary(commitC.isPresent()), 0.4d),
				CriterionGradeWeight.from(GitBrCriterion.COMMIT_C_CONTENTS, getCContentGrade(fs, commitC), 0.4d)));
		gradeBuilder.put(GitBrCriterion.COMMIT_C, gradeC);

		final Optional<RevCommit> revCommitC = commitC.map(filtered::getCommit);
		final Set<RevCommit> childrenOfC = revCommitC.map(c -> filtered.getGraph().predecessors(c))
				.orElse(ImmutableSet.of());
		final Optional<RevCommit> revCommitA = commitA.map(filtered::getCommit);
		final Set<RevCommit> childrenOfA = revCommitA.map(c -> filtered.getGraph().predecessors(c))
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

		final WeightingGrade gradeD = WeightingGrade.from(ImmutableList.of(
				CriterionGradeWeight.from(GitBrCriterion.COMMIT_D_START,
						Mark.binary(startedAtStart && commitD.isPresent()), 0.2d),
				CriterionGradeWeight.from(GitBrCriterion.COMMIT_D_EXISTS, Mark.binary(commitD.isPresent()), 0.4d),
				CriterionGradeWeight.from(GitBrCriterion.COMMIT_D_CONTENTS, getDContentGrade(fs, commitD), 0.4d)));
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
		builder.put(GitBrCriterion.COMMIT_C, 1.5d);
		builder.put(GitBrCriterion.COMMIT_D, 1.5d);
		builder.put(GitBrCriterion.BR, 2d);
		return WeightingGrade.from(subGrades, builder.build(), comment);
	}

	private Optional<ObjectId> getObjectId(GitPathRoot root) throws IOException {
		if (!root.exists()) {
			return Optional.empty();
		}
		return Optional.of(root.getCommit().getId());
	}

	private IGrade getAContentGrade(GitFileSystem fs, Optional<ObjectId> commit) {
		final IGrade grade;
		if (commit.isPresent()) {
			final GitPath file = fs.getAbsolutePath(commit.get().getName(), "hello.txt");
			final String exactTarget = "first try";
			grade = Marks.fileMatchesGrade(file, exactTarget, Marks.extend(exactTarget));
		} else {
			grade = Mark.zero();
		}

		return grade;
	}

	private IGrade getBContentGrade(GitFileSystem fs, Optional<ObjectId> commit) {
		final IGrade grade;
		if (commit.isPresent()) {
			final GitPath file = fs.getAbsolutePath(commit.get().getName(), "hello.txt");
			final String exactTarget = "second try";
			grade = Marks.fileMatchesGrade(file, exactTarget, Marks.extend(exactTarget));
		} else {
			grade = Mark.zero();
		}

		return grade;
	}

	private IGrade getCContentGrade(GitFileSystem fs, Optional<ObjectId> commit) {
		final IGrade grade;
		if (commit.isPresent()) {
			final GitPath file = fs.getAbsolutePath(commit.get().getName(), "supplements.txt");
			final String exactTarget = "Hello, world";
			final Pattern approximateTarget = Marks.extend("Hello,?\\h*world");
			grade = Marks.fileMatchesGrade(file, exactTarget, approximateTarget);
		} else {
			grade = Mark.zero();
		}

		return grade;
	}

	private IGrade getDContentGrade(GitFileSystem fs, Optional<ObjectId> commit) {
		final IGrade grade;
		if (commit.isPresent()) {
			final GitPath file = fs.getAbsolutePath(commit.get().getName(), "hello.txt");
			final String exactTarget = "first try\nsecond try";
			final Pattern approximateTarget = Marks.extend("first try[\\h\\v]*second try");
			grade = Marks.fileMatchesGrade(file, exactTarget, approximateTarget);
		} else {
			grade = Mark.zero();
		}

		return grade;
	}

}
