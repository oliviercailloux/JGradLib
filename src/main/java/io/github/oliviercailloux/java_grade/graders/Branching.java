package io.github.oliviercailloux.java_grade.graders;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Verify.verify;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.MoreCollectors;
import com.google.common.collect.Sets;
import com.google.common.graph.Graphs;
import io.github.oliviercailloux.git.fs.GitPath;
import io.github.oliviercailloux.git.fs.GitPathRoot;
import io.github.oliviercailloux.git.git_hub.model.GitHubToken;
import io.github.oliviercailloux.git.git_hub.model.RepositoryCoordinatesWithPrefix;
import io.github.oliviercailloux.git.git_hub.model.v3.CreateBranchEvent;
import io.github.oliviercailloux.git.git_hub.services.GitHubFetcherV3;
import io.github.oliviercailloux.grade.BatchGitHistoryGrader;
import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.GitFileSystemHistory;
import io.github.oliviercailloux.grade.GitFileSystemWithHistoryFetcherByPrefix;
import io.github.oliviercailloux.grade.GitGrader.Predicates;
import io.github.oliviercailloux.grade.GradeAggregator;
import io.github.oliviercailloux.grade.Grader;
import io.github.oliviercailloux.grade.Mark;
import io.github.oliviercailloux.grade.MarksTree;
import io.github.oliviercailloux.grade.SubMarksTree;
import io.github.oliviercailloux.jaris.exceptions.Throwing.Function;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Set;
import java.util.regex.Pattern;
import name.falgout.jeffrey.throwing.stream.ThrowingStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Branching implements Grader<IOException> {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(Branching.class);

	public static final String PREFIX = "branching";

	public static final ZonedDateTime DEADLINE = ZonedDateTime.parse("2022-03-09T14:11:00+01:00[Europe/Paris]");

	public static void main(String[] args) throws Exception {
		final GitHubFetcherV3 fetcherV3 = GitHubFetcherV3.using(GitHubToken.getRealInstance());
		final ImmutableList<CreateBranchEvent> events = fetcherV3
				.getCreateBranchEvents(RepositoryCoordinatesWithPrefix.from("oliviercailloux-org", PREFIX, "alinou33"));

		final BatchGitHistoryGrader<RuntimeException> grader = BatchGitHistoryGrader
				.given(() -> GitFileSystemWithHistoryFetcherByPrefix.getFirstRetrievingByPrefix(PREFIX));
		grader.getAndWriteGrades(DEADLINE, Duration.ofMinutes(5), new Branching(), 0.05d, Path.of("grades " + PREFIX),
				PREFIX + " " + Instant.now().atZone(DEADLINE.getZone()));
	}

	private static final Criterion C_ANY = Criterion.given("Anything committed");

	private static final Criterion C_START = Criterion.given("Commit Start");
	private static final Criterion C_ONE = Criterion.given("Exactly one file");
	private static final Criterion C_EXISTS_START = Criterion.given("Exists Start file");
	private static final Criterion C_CONTENTS_START = Criterion.given("Contents Start file");

	private static final Criterion C_A = Criterion.given("Commit A");
	private static final Criterion C_TWO = Criterion.given("Exactly two files");
	private static final Criterion C_BR1 = Criterion.given("Branch br1");
	private static final Criterion C_EXISTS_FIRST = Criterion.given("Exists First file");
	private static final Criterion C_CONTENTS_FIRST = Criterion.given("Contents First file");

	private static final Criterion C_B = Criterion.given("Commit B");
	private static final Criterion C_THREE = Criterion.given("Exactly three files");
	private static final Criterion C_EXISTS_SOME = Criterion.given("Exists Some file");
	private static final Criterion C_CONTENTS_SOME = Criterion.given("Contents Some file");

	private static final Criterion C_C = Criterion.given("Commit C");
	private static final Criterion C_BR2 = Criterion.given("Branch br2");

	private static final Criterion C_D = Criterion.given("Commit D");
	private static final Criterion C_BR3 = Criterion.given("Branch br3");
	private static final Criterion C_PARENTS = Criterion.given("Right parents");

	private GitFileSystemHistory currentHistory;

	private Branching() {
		currentHistory = null;
	}

	@Override
	public MarksTree grade(GitFileSystemHistory data) throws IOException {
		currentHistory = data;
		verify(!currentHistory.getGraph().nodes().isEmpty());

		final ImmutableSet<GitPathRoot> commitsOrdered = currentHistory.getRoots().stream()
				.flatMap(r -> Graphs.reachableNodes(currentHistory.getGraph(), r).stream())
				.collect(ImmutableSet.toImmutableSet());
		final ImmutableSet<GitPathRoot> commitsOrderedExceptRoots = Sets
				.difference(commitsOrdered, currentHistory.getRoots()).immutableCopy();
		final int nbCommits = commitsOrderedExceptRoots.size();

		final MarksTree anyCommitMark = Mark.binary(!commitsOrderedExceptRoots.isEmpty(),
				String.format("Found %s commit%s, not counting the root ones", nbCommits, nbCommits == 1 ? "" : "s"),
				"");
		final SubMarksTree subAny = SubMarksTree.given(C_ANY, anyCommitMark);

		final Set<SubMarksTree> subs = ThrowingStream.of(commitsOrderedExceptRoots.stream(), IOException.class)
				.filter(r -> currentHistory.getGraph().predecessors(r).size() == 1)
				.map(r -> subGrade(r, this::gradeStart)).collect(ImmutableSet.toImmutableSet());
		final SubMarksTree subStart = SubMarksTree.given(C_START, compositeOrZero(subs, "No commit"));

		return MarksTree.composite(ImmutableSet.of(subAny, subStart));
	}

	private SubMarksTree subGrade(GitPathRoot gitPathRoot, Function<GitPathRoot, MarksTree, IOException> grader)
			throws IOException, NoSuchFileException {
		final Criterion criterion = Criterion.given("Using " + gitPathRoot.getCommit().getId().getName());
		return SubMarksTree.given(criterion, grader.apply(gitPathRoot));
	}

	@Override
	public GradeAggregator getAggregator() {
		final GradeAggregator dAg;
		{
			final ImmutableMap.Builder<Criterion, Double> builder = ImmutableMap.builder();
			builder.put(C_THREE, 0.5d);
			builder.put(C_BR3, 0.5d);
			builder.put(C_EXISTS_START, 0.15d);
			builder.put(C_CONTENTS_START, 0.15d);
			builder.put(C_EXISTS_FIRST, 0.15d);
			builder.put(C_CONTENTS_FIRST, 0.15d);
			builder.put(C_EXISTS_SOME, 0.4d);
			builder.put(C_CONTENTS_SOME, 1d);
			builder.put(C_PARENTS, 1d);
			dAg = GradeAggregator.max(GradeAggregator.staticAggregator(builder.build(), ImmutableMap.of()));
		}
		final GradeAggregator cAg;
		{
			final ImmutableMap.Builder<Criterion, Double> builder = ImmutableMap.builder();
			builder.put(C_TWO, 1d);
			builder.put(C_BR2, 1d);
			builder.put(C_EXISTS_START, 0.5d);
			builder.put(C_CONTENTS_START, 0.5d);
			builder.put(C_EXISTS_FIRST, 0.5d);
			builder.put(C_CONTENTS_FIRST, 0.5d);
			builder.put(C_D, 4d);
			cAg = GradeAggregator.max(GradeAggregator.staticAggregator(builder.build(), ImmutableMap.of(C_D, dAg)));
		}
		final GradeAggregator bAg;
		{
			final ImmutableMap.Builder<Criterion, Double> builder = ImmutableMap.builder();
			builder.put(C_THREE, 1d);
			builder.put(C_EXISTS_START, 0.5d);
			builder.put(C_CONTENTS_START, 0.5d);
			builder.put(C_EXISTS_FIRST, 0.5d);
			builder.put(C_CONTENTS_FIRST, 0.5d);
			builder.put(C_EXISTS_SOME, 0.5d);
			builder.put(C_CONTENTS_SOME, 0.5d);
			builder.put(C_C, 8d);
			bAg = GradeAggregator.max(GradeAggregator.staticAggregator(builder.build(), ImmutableMap.of(C_C, cAg)));
		}
		final GradeAggregator aAg;
		{
			final ImmutableMap.Builder<Criterion, Double> builder = ImmutableMap.builder();
			builder.put(C_TWO, 1d);
			builder.put(C_BR1, 1d);
			builder.put(C_EXISTS_START, 0.5d);
			builder.put(C_CONTENTS_START, 0.5d);
			builder.put(C_EXISTS_FIRST, 0.5d);
			builder.put(C_CONTENTS_FIRST, 0.5d);
			builder.put(C_B, 12d);
			aAg = GradeAggregator.max(GradeAggregator.staticAggregator(builder.build(), ImmutableMap.of(C_B, bAg)));
		}
		final GradeAggregator startAg;
		{
			final ImmutableMap.Builder<Criterion, Double> builder = ImmutableMap.builder();
			builder.put(C_ONE, 1d);
			builder.put(C_EXISTS_START, 0.5d);
			builder.put(C_CONTENTS_START, 0.5d);
			builder.put(C_A, 16d);
			startAg = GradeAggregator.max(GradeAggregator.staticAggregator(builder.build(), ImmutableMap.of(C_A, aAg)));
		}
		final GradeAggregator mainAg;
		{
			final ImmutableMap.Builder<Criterion, Double> builder = ImmutableMap.builder();
			builder.put(C_ANY, 1d);
			builder.put(C_START, 18d);
			mainAg = GradeAggregator.staticAggregator(builder.build(), ImmutableMap.of(C_START, startAg));
		}
		return mainAg;
	}

	private MarksTree gradeStart(GitPathRoot commitStart) throws IOException {
		final long nbFiles = Files.find(commitStart, Integer.MAX_VALUE, (p, a) -> Files.isRegularFile(p)).count();
		final boolean rightNb = nbFiles == 1;
		final SubMarksTree subNb = SubMarksTree.given(C_ONE, Mark.binary(rightNb, "", "Found " + nbFiles + " files"));

		final GitPath startPath = commitStart.resolve("start.txt");
		final boolean startExists = Files.exists(startPath);
		final String startContent = startExists ? Files.readString(startPath) : "";
		final Pattern pattern = Pattern.compile("A starting point\\v+");
		final boolean startMatches = pattern.matcher(startContent).matches();
		final SubMarksTree subExists = SubMarksTree.given(C_EXISTS_START, Mark.binary(startExists));
		final SubMarksTree subMatches = SubMarksTree.given(C_CONTENTS_START, Mark.binary(startMatches));

		final Set<GitPathRoot> successors = currentHistory.getGraph().successors(commitStart);
		final Set<SubMarksTree> subs = ThrowingStream.of(successors.stream(), IOException.class)
				.filter(r -> currentHistory.getGraph().predecessors(r).size() == 1).map(r -> subGrade(r, this::gradeA))
				.collect(ImmutableSet.toImmutableSet());
		final SubMarksTree subB = SubMarksTree.given(C_A, compositeOrZero(subs, "No successor of Start"));
		return MarksTree.composite(ImmutableSet.of(subNb, subExists, subMatches, subB));
	}

	private MarksTree compositeOrZero(Set<SubMarksTree> subs, String commentIfEmpty) {
		return subs.isEmpty() ? Mark.zero(commentIfEmpty) : MarksTree.composite(subs);
	}

	private MarksTree gradeA(GitPathRoot commitA) throws IOException {
		checkArgument(currentHistory.getGraph().predecessors(commitA).size() == 1);

		final long nbFiles = Files.find(commitA, Integer.MAX_VALUE, (p, a) -> Files.isRegularFile(p)).count();
		final boolean rightNb = nbFiles == 2;
		final SubMarksTree subNb = SubMarksTree.given(C_TWO, Mark.binary(rightNb, "", "Found " + nbFiles + " files"));

		final boolean isBranch = ThrowingStream
				.of(currentHistory.getRefsTo(commitA.toSha()).stream(), IOException.class).filter(GitPathRoot::isRef)
				.map(GitPathRoot::getGitRef).anyMatch(r -> Predicates.isBranch(r, "br1"));
		final SubMarksTree subBranch = SubMarksTree.given(C_BR1, Mark.binary(isBranch));

		final GitPath startPath = commitA.resolve("start.txt");
		final boolean startExists = Files.exists(startPath);
		final String startContent = startExists ? Files.readString(startPath) : "";
		final Pattern patternStart = Pattern.compile("A starting point\\v+");
		final boolean startMatches = patternStart.matcher(startContent).matches();
		final SubMarksTree subStartExists = SubMarksTree.given(C_EXISTS_START, Mark.binary(startExists));
		final SubMarksTree subStartMatches = SubMarksTree.given(C_CONTENTS_START, Mark.binary(startMatches));

		final GitPath firstPath = commitA.resolve("first.txt");
		final boolean firstExists = Files.exists(firstPath);
		final String firstContent = firstExists ? Files.readString(firstPath) : "";
		final Pattern patternFirst = Pattern.compile("Hello world\\v*");
		verify(patternFirst.matcher("Hello world").matches());
		verify(patternFirst.matcher("Hello world\n").matches());
		verify(!patternFirst.matcher("hello world").matches());
		final boolean firstMatches = patternFirst.matcher(firstContent).matches();
		final SubMarksTree subExists = SubMarksTree.given(C_EXISTS_FIRST, Mark.binary(firstExists));
		final SubMarksTree subMatches = SubMarksTree.given(C_CONTENTS_FIRST, Mark.binary(firstMatches));

		final Set<GitPathRoot> successors = currentHistory.getGraph().successors(commitA);
		final Set<SubMarksTree> subs = ThrowingStream.of(successors.stream(), IOException.class)
				.filter(r -> currentHistory.getGraph().predecessors(r).size() == 1).map(r -> subGrade(r, this::gradeB))
				.collect(ImmutableSet.toImmutableSet());
		final SubMarksTree subB = SubMarksTree.given(C_B, compositeOrZero(subs, "No successor of A"));
		return MarksTree.composite(
				ImmutableSet.of(subNb, subBranch, subStartExists, subStartMatches, subExists, subMatches, subB));
	}

	private MarksTree gradeB(GitPathRoot commitB) throws IOException {
		checkArgument(currentHistory.getGraph().predecessors(commitB).size() == 1);
		final GitPathRoot commitA = currentHistory.getGraph().predecessors(commitB).stream()
				.collect(MoreCollectors.onlyElement());
		checkArgument(currentHistory.getGraph().predecessors(commitA).size() == 1);
		final GitPathRoot start = currentHistory.getGraph().predecessors(commitA).stream()
				.collect(MoreCollectors.onlyElement());

		final long nbFiles = Files.find(commitB, Integer.MAX_VALUE, (p, a) -> Files.isRegularFile(p)).count();
		final boolean rightNb = nbFiles == 2;
		final SubMarksTree subNb = SubMarksTree.given(C_THREE, Mark.binary(rightNb, "", "Found " + nbFiles + " files"));

		final GitPath startPath = commitB.resolve("start.txt");
		final boolean startExists = Files.exists(startPath);
		final String startContent = startExists ? Files.readString(startPath) : "";
		final Pattern patternStart = Pattern.compile("A starting point\\v+");
		final boolean startMatches = patternStart.matcher(startContent).matches();
		final SubMarksTree subStartExists = SubMarksTree.given(C_EXISTS_START, Mark.binary(startExists));
		final SubMarksTree subStartMatches = SubMarksTree.given(C_CONTENTS_START, Mark.binary(startMatches));

		final GitPath firstPath = commitB.resolve("first.txt");
		final boolean firstExists = Files.exists(firstPath);
		final String firstContent = firstExists ? Files.readString(firstPath) : "";
		final Pattern patternFirst = Pattern.compile("Hello world\\v*");
		final boolean firstMatches = patternFirst.matcher(firstContent).matches();
		final SubMarksTree subExists = SubMarksTree.given(C_EXISTS_FIRST, Mark.binary(firstExists));
		final SubMarksTree subMatches = SubMarksTree.given(C_CONTENTS_FIRST, Mark.binary(firstMatches));

		final GitPath somePath = commitB.resolve("a/some file.txt");
		final boolean someExists = Files.exists(somePath);
		final String someContent = someExists ? Files.readString(somePath) : "";
		final Pattern somePattern = Pattern.compile("Hey\\v*");
		final boolean someMatches = somePattern.matcher(someContent).matches();
		final SubMarksTree subSomeExists = SubMarksTree.given(C_EXISTS_SOME, Mark.binary(someExists));
		final SubMarksTree subSomeMatches = SubMarksTree.given(C_CONTENTS_SOME, Mark.binary(someMatches));

		final Set<GitPathRoot> siblingsOfA = currentHistory.getGraph().successors(start).stream()
				.filter(r -> !r.equals(commitA)).collect(ImmutableSet.toImmutableSet());
		final Set<SubMarksTree> subs = ThrowingStream.of(siblingsOfA.stream(), IOException.class)
				.map(r -> subGrade(r, w -> gradeC(commitB, w))).collect(ImmutableSet.toImmutableSet());
		final SubMarksTree subC = SubMarksTree.given(C_C, compositeOrZero(subs, "No sibling of A"));
		return MarksTree.composite(ImmutableSet.of(subNb, subStartExists, subStartMatches, subExists, subMatches,
				subSomeExists, subSomeMatches, subC));
	}

	private MarksTree gradeC(GitPathRoot commitB, GitPathRoot commitC) throws IOException {
		final long nbFiles = Files.find(commitC, Integer.MAX_VALUE, (p, a) -> Files.isRegularFile(p)).count();
		final boolean rightNb = nbFiles == 2;
		final SubMarksTree subNb = SubMarksTree.given(C_TWO, Mark.binary(rightNb, "", "Found " + nbFiles + " files"));

		final boolean isBranch = ThrowingStream
				.of(currentHistory.getRefsTo(commitC.toSha()).stream(), IOException.class).filter(GitPathRoot::isRef)
				.map(GitPathRoot::getGitRef).anyMatch(r -> Predicates.isBranch(r, "br2"));
		final SubMarksTree subBranch = SubMarksTree.given(C_BR2, Mark.binary(isBranch));

		final GitPath startPath = commitC.resolve("start.txt");
		final boolean startExists = Files.exists(startPath);
		final String startContent = startExists ? Files.readString(startPath) : "";
		final Pattern patternStart = Pattern.compile("A starting point\\v+");
		final boolean startMatches = patternStart.matcher(startContent).matches();
		final SubMarksTree subStartExists = SubMarksTree.given(C_EXISTS_START, Mark.binary(startExists));
		final SubMarksTree subStartMatches = SubMarksTree.given(C_CONTENTS_START, Mark.binary(startMatches));

		final GitPath firstPath = commitC.resolve("first.txt");
		final boolean firstExists = Files.exists(firstPath);
		final String firstContent = firstExists ? Files.readString(firstPath) : "";
		final Pattern pattern = Pattern.compile("Coucou monde\\v*");
		final boolean firstMatches = pattern.matcher(firstContent).matches();
		final SubMarksTree subExists = SubMarksTree.given(C_EXISTS_FIRST, Mark.binary(firstExists));
		final SubMarksTree subMatches = SubMarksTree.given(C_CONTENTS_FIRST, Mark.binary(firstMatches));

		final Set<GitPathRoot> successors = currentHistory.getGraph().successors(commitC);
		final Set<SubMarksTree> subs = ThrowingStream.of(successors.stream(), IOException.class)
				.filter(r -> currentHistory.getGraph().predecessors(r).size() == 2)
				.map(r -> subGrade(r, w -> gradeD(commitB, w))).collect(ImmutableSet.toImmutableSet());
		final SubMarksTree subD = SubMarksTree.given(C_D,
				compositeOrZero(subs, "No successor of C merging two commits"));
		return MarksTree.composite(
				ImmutableSet.of(subNb, subBranch, subStartExists, subStartMatches, subExists, subMatches, subD));
	}

	private MarksTree gradeD(GitPathRoot commitC, GitPathRoot commitD) throws IOException {
		final long nbFiles = Files.find(commitD, Integer.MAX_VALUE, (p, a) -> Files.isRegularFile(p)).count();
		final boolean rightNb = nbFiles == 3;
		final SubMarksTree subNb = SubMarksTree.given(C_THREE, Mark.binary(rightNb, "", "Found " + nbFiles + " files"));

		final boolean isBranch = ThrowingStream
				.of(currentHistory.getRefsTo(commitD.toSha()).stream(), IOException.class).filter(GitPathRoot::isRef)
				.map(GitPathRoot::getGitRef).anyMatch(r -> Predicates.isBranch(r, "br3"));
		final SubMarksTree subBranch = SubMarksTree.given(C_BR3, Mark.binary(isBranch));

		final GitPath startPath = commitD.resolve("start.txt");
		final boolean startExists = Files.exists(startPath);
		final String startContent = startExists ? Files.readString(startPath) : "";
		final Pattern patternStart = Pattern.compile("A starting point\\v+");
		final boolean startMatches = patternStart.matcher(startContent).matches();
		final SubMarksTree subStartExists = SubMarksTree.given(C_EXISTS_START, Mark.binary(startExists));
		final SubMarksTree subStartMatches = SubMarksTree.given(C_CONTENTS_START, Mark.binary(startMatches));

		final GitPath firstPath = commitD.resolve("first.txt");
		final boolean firstExists = Files.exists(firstPath);
		final String firstContent = firstExists ? Files.readString(firstPath) : "";
		final Pattern patternFirst = Pattern.compile("Hello world\\v+Coucou monde\\v*");
		verify(patternFirst.matcher("Hello world\nCoucou monde").matches());
		verify(patternFirst.matcher("Hello world\n\nCoucou monde\n").matches());
		verify(!patternFirst.matcher("Hello world").matches());
		verify(!patternFirst.matcher("Hello world\nCoucou ").matches());
		final boolean firstMatches = patternFirst.matcher(firstContent).matches();
		final SubMarksTree subFirstExists = SubMarksTree.given(C_EXISTS_FIRST, Mark.binary(firstExists));
		final SubMarksTree subFirstMatches = SubMarksTree.given(C_CONTENTS_FIRST, Mark.binary(firstMatches));

		final GitPath somePath = commitD.resolve("a/some file.txt");
		final boolean someExists = Files.exists(somePath);
		final String someContent = someExists ? Files.readString(somePath) : "";
		final Pattern somePattern = Pattern.compile("Hey\\v*");
		final boolean someMatches = somePattern.matcher(someContent).matches();
		final SubMarksTree subSomeExists = SubMarksTree.given(C_EXISTS_SOME, Mark.binary(someExists));
		final SubMarksTree subSomeMatches = SubMarksTree.given(C_CONTENTS_SOME, Mark.binary(someMatches));

		final Set<GitPathRoot> parents = currentHistory.getGraph().predecessors(commitD);
		final boolean rightParents = parents.contains(commitC) && parents.size() == 2;
		final SubMarksTree subParents = SubMarksTree.given(C_PARENTS, Mark.binary(rightParents));

		return MarksTree.composite(ImmutableSet.of(subNb, subBranch, subStartExists, subStartMatches, subFirstExists,
				subFirstMatches, subSomeExists, subSomeMatches, subParents));
	}
}
