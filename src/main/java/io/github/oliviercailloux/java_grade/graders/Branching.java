package io.github.oliviercailloux.java_grade.graders;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Verify.verify;

import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.MoreCollectors;
import com.google.common.graph.Graphs;
import io.github.oliviercailloux.git.fs.GitPath;
import io.github.oliviercailloux.git.fs.GitPathRoot;
import io.github.oliviercailloux.grade.BatchGitHistoryGrader;
import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.GitFileSystemHistory;
import io.github.oliviercailloux.grade.GitFileSystemWithHistoryFetcherByPrefix;
import io.github.oliviercailloux.grade.GitGrader.Predicates;
import io.github.oliviercailloux.grade.Grade;
import io.github.oliviercailloux.grade.GradeAggregator;
import io.github.oliviercailloux.grade.Grader;
import io.github.oliviercailloux.grade.IGrade.CriteriaPath;
import io.github.oliviercailloux.grade.Mark;
import io.github.oliviercailloux.grade.MarksTree;
import io.github.oliviercailloux.grade.SubMark;
import io.github.oliviercailloux.grade.SubMarksTree;
import io.github.oliviercailloux.jaris.exceptions.Throwing.Function;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
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

	public static final double USER_WEIGHT = 0.05d;

	private static final boolean EXPAND = true;

	public static void main(String[] args) throws Exception {
		final BatchGitHistoryGrader<RuntimeException> grader = BatchGitHistoryGrader
				.given(() -> GitFileSystemWithHistoryFetcherByPrefix.getRetrievingByPrefixAndUsingCommitDates(PREFIX));
		grader.getAndWriteGrades(DEADLINE, Duration.ofMinutes(5), new Branching(), USER_WEIGHT,
				Path.of("grades " + PREFIX), PREFIX + " " + Instant.now().atZone(DEADLINE.getZone()));
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

	private GitPathRoot startPath;

	Branching() {
		currentHistory = null;
		startPath = null;
	}

	@Override
	public MarksTree grade(GitFileSystemHistory data) throws IOException {
		currentHistory = data;
		verify(!currentHistory.getGraph().nodes().isEmpty());

		final ImmutableSet<GitPathRoot> commitsOrdered = currentHistory.getRoots().stream()
				.flatMap(r -> Graphs.reachableNodes(currentHistory.getGraph(), r).stream())
				.collect(ImmutableSet.toImmutableSet());
		final int nbCommits = commitsOrdered.size();

		final MarksTree anyCommitMark = Mark.binary(!commitsOrdered.isEmpty(),
				String.format("Found %s commit%s, including the root ones", nbCommits, nbCommits == 1 ? "" : "s"), "");
		final SubMarksTree subAny = SubMarksTree.given(C_ANY, anyCommitMark);

		final SubMarksTree subStart = gradeStartFromCommits(commitsOrdered, EXPAND);
		if (EXPAND) {
			// return move(tree, CriteriaPath.from(ImmutableList.of(C_START, C_A)),
			// CriteriaPath.from(ImmutableList.of(C_A)));
			return MarksTree.composite(ImmutableSet.of(subAny, subStart));
		}

		final SubMarksTree subA = gradeAFromStart(startPath, false);
		return MarksTree.composite(ImmutableSet.of(subAny, subStart, subA));
	}

	private SubMarksTree gradeStartFromCommits(ImmutableSet<GitPathRoot> commitsOrdered, boolean expandLocally)
			throws IOException {
		final ImmutableBiMap<GitPathRoot, SubMarksTree> subs = ThrowingStream
				.of(commitsOrdered.stream(), IOException.class)
				.filter(r -> currentHistory.getGraph().predecessors(r).size() <= 1)
				.collect(ImmutableBiMap.toImmutableBiMap(r -> r, r -> {
					try {
						return subGrade(r, w -> gradeStart(w, true));
					} catch (NoSuchFileException e) {
						throw new IllegalStateException(e);
					} catch (IOException e) {
						throw new IllegalStateException(e);
					}
				}));
		final MarksTree tree = compositeOrZero(subs.values(), "No commit");
		if (expandLocally) {
			return SubMarksTree.given(C_START, tree);
		}
		final ImmutableMap<SubMark, Double> weightedSubMarks = Grade.given(startAggregatorExpanded(), tree)
				.getWeightedSubMarks();
		final Criterion bestPath = Maps.filterValues(weightedSubMarks, w -> w == 1d).keySet().stream()
				.map(SubMark::getCriterion).collect(MoreCollectors.onlyElement());
		startPath = Maps.filterValues(subs, s -> s.getCriterion().equals(bestPath)).keySet().stream()
				.collect(MoreCollectors.onlyElement());
		return SubMarksTree.given(C_START, gradeStart(startPath, false));
	}

	private MarksTree compositeOrZero(Set<SubMarksTree> subs, String commentIfEmpty) {
		return subs.isEmpty() ? Mark.zero(commentIfEmpty) : MarksTree.composite(subs);
	}

	private SubMarksTree subGrade(GitPathRoot gitPathRoot, Function<GitPathRoot, MarksTree, IOException> grader)
			throws IOException, NoSuchFileException {
		final String comment = "Using " + gitPathRoot.getCommit().getId().getName();
		final MarksTree grade = grader.apply(gitPathRoot);
		final MarksTree newTree = addComment(grade, comment);

		final Criterion criterion = Criterion.given(comment);
		return SubMarksTree.given(criterion, newTree);
	}

	private MarksTree addComment(MarksTree grade, String comment) {
		final Criterion sub1 = grade.getCriteria().stream().findFirst().orElseThrow();
		final MarksTree tree1 = grade.getTree(sub1);
		verify(tree1.isMark());
		final Mark mark1 = tree1.getMark(CriteriaPath.ROOT);
		verify(mark1.getComment().isEmpty());
		final ImmutableMap<Criterion, MarksTree> subs = Maps.toMap(grade.getCriteria(), grade::getTree);
		final LinkedHashMap<Criterion, MarksTree> subsNew = new LinkedHashMap<>(subs);
		subsNew.put(sub1, Mark.given(mark1.getPoints(), comment));
		final MarksTree newTree = MarksTree.composite(subsNew);
		return newTree;
	}

	@Override
	public GradeAggregator getAggregator() {
		if (EXPAND) {
			return getAggregatorExpanded();
		}
		return getAggregatorFlattened();
	}

	private GradeAggregator getAggregatorExpanded() {
		final GradeAggregator startAg = startAggregatorExpanded();
		final GradeAggregator mainAg;
		{
			final ImmutableMap.Builder<Criterion, Double> builder = ImmutableMap.builder();
			builder.put(C_ANY, 1d);
			builder.put(C_START, 18d);
			mainAg = GradeAggregator.staticAggregator(builder.build(), ImmutableMap.of(C_START, startAg));
		}
		return mainAg;
	}

	private GradeAggregator startAggregatorExpanded() {
		final GradeAggregator aAg = aAggregatorExpanded();
		final GradeAggregator startAg;
		{
			final ImmutableMap.Builder<Criterion, Double> builder = ImmutableMap.builder();
			builder.put(C_ONE, 1d);
			builder.put(C_EXISTS_START, 0.5d);
			builder.put(C_CONTENTS_START, 0.5d);
			builder.put(C_A, 16d);
			final GradeAggregator startAggregator = GradeAggregator.staticAggregator(builder.build(),
					ImmutableMap.of(C_A, aAg));
			startAg = GradeAggregator.max(startAggregator);
		}
		return startAg;
	}

	private GradeAggregator aAggregatorExpanded() {
		final GradeAggregator bAg = bAggregatorExpanded();
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
			final GradeAggregator aAggregator = GradeAggregator.staticAggregator(builder.build(),
					ImmutableMap.of(C_B, bAg));
			aAg = GradeAggregator.max(aAggregator);
		}
		return aAg;
	}

	private GradeAggregator bAggregatorExpanded() {
		final GradeAggregator cAg = cAggregatorExpanded();
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
			final GradeAggregator bAggregator = GradeAggregator.staticAggregator(builder.build(),
					ImmutableMap.of(C_C, cAg));
			bAg = GradeAggregator.max(bAggregator);
		}
		return bAg;
	}

	private GradeAggregator cAggregatorExpanded() {
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
			final GradeAggregator dAggregator = GradeAggregator.staticAggregator(builder.build(), ImmutableMap.of());
			dAg = GradeAggregator.max(dAggregator);
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
			final GradeAggregator cAggregator = GradeAggregator.staticAggregator(builder.build(),
					ImmutableMap.of(C_D, dAg));
			cAg = GradeAggregator.max(cAggregator);
		}
		return cAg;
	}

	private GradeAggregator getAggregatorFlattened() {
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
			dAg = GradeAggregator.staticAggregator(builder.build(), ImmutableMap.of());
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
			cAg = GradeAggregator.staticAggregator(builder.build(), ImmutableMap.of());
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
			bAg = GradeAggregator.staticAggregator(builder.build(), ImmutableMap.of());
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
			aAg = GradeAggregator.staticAggregator(builder.build(), ImmutableMap.of());
		}
		final GradeAggregator startAg;
		{
			final ImmutableMap.Builder<Criterion, Double> builder = ImmutableMap.builder();
			builder.put(C_ONE, 1d);
			builder.put(C_EXISTS_START, 0.5d);
			builder.put(C_CONTENTS_START, 0.5d);
			startAg = GradeAggregator.staticAggregator(builder.build(), ImmutableMap.of());
		}
		final GradeAggregator mainAg;
		{
			final ImmutableMap.Builder<Criterion, Double> builder = ImmutableMap.builder();
			builder.put(C_ANY, 1d);
			builder.put(C_START, 2d);
			builder.put(C_A, 4d);
			builder.put(C_B, 4d);
			builder.put(C_C, 4d);
			builder.put(C_D, 4d);
			mainAg = GradeAggregator.staticAggregator(builder.build(),
					ImmutableMap.of(C_START, startAg, C_A, aAg, C_B, bAg, C_C, cAg, C_D, dAg));
		}
		return mainAg;
	}

	private MarksTree gradeStart(GitPathRoot commitStart, boolean expandLocally) throws IOException {
		final long nbFiles = Files.find(commitStart, Integer.MAX_VALUE, (p, a) -> Files.isRegularFile(p)).count();
		final boolean rightNb = nbFiles == 1;
		final SubMarksTree subNb = SubMarksTree.given(C_ONE, Mark.binary(rightNb, "", "Found " + nbFiles + " files"));

		final GitPath startPath = commitStart.resolve("start.txt");
		final boolean startExists = Files.exists(startPath);
		final String startContent = startExists ? Files.readString(startPath) : "";
		final Pattern pattern = Pattern.compile("A starting point\\v*");
		final boolean startMatches = pattern.matcher(startContent).matches();
		final SubMarksTree subExists = SubMarksTree.given(C_EXISTS_START, Mark.binary(startExists));
		final SubMarksTree subMatches = SubMarksTree.given(C_CONTENTS_START, Mark.binary(startMatches));

		final ImmutableSet.Builder<SubMarksTree> builder = ImmutableSet.builder();
		builder.add(subNb, subExists, subMatches);
		if (expandLocally) {
			final SubMarksTree subA = gradeAFromStart(commitStart, true);
			builder.add(subA);
		}
		return MarksTree.composite(builder.build());
	}

	private SubMarksTree gradeAFromStart(GitPathRoot commitStart, boolean expandLocally) throws IOException {
		final Set<GitPathRoot> successors = currentHistory.getGraph().successors(commitStart);
		final ImmutableBiMap<GitPathRoot, SubMarksTree> subs = ThrowingStream.of(successors.stream(), IOException.class)
				.filter(r -> currentHistory.getGraph().predecessors(r).size() == 1)
				.collect(ImmutableBiMap.toImmutableBiMap(r -> r, r -> {
					try {
						return subGrade(r, w -> gradeA(w, true));
					} catch (NoSuchFileException e) {
						throw new IllegalStateException(e);
					} catch (IOException e) {
						throw new IllegalStateException(e);
					}
				}));
		final MarksTree tree = compositeOrZero(subs.values(), "No successor of Start");
		if (expandLocally) {
			return SubMarksTree.given(C_A, tree);
		}
		final ImmutableMap<SubMark, Double> weightedSubMarks = Grade.given(aAggregatorExpanded(), tree)
				.getWeightedSubMarks();
		final Criterion bestPath = Maps.filterValues(weightedSubMarks, w -> w == 1d).keySet().stream()
				.map(SubMark::getCriterion).collect(MoreCollectors.onlyElement());
		final GitPathRoot bestSubMark = Maps.filterValues(subs, s -> s.getCriterion().equals(bestPath)).keySet()
				.stream().collect(MoreCollectors.onlyElement());
		return SubMarksTree.given(C_A, gradeA(bestSubMark, false));
	}

	private MarksTree gradeA(GitPathRoot commitA, boolean expandLocally) throws IOException {
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
		final Pattern patternStart = Pattern.compile("A starting point\\v*");
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

		final ImmutableSet.Builder<SubMarksTree> builder = ImmutableSet.builder();
		builder.add(subNb, subBranch, subStartExists, subStartMatches, subExists, subMatches);
		if (expandLocally) {
			final SubMarksTree subB = gradeBFromA(commitA);
			builder.add(subB);
		}
		return MarksTree.composite(builder.build());
	}

	private SubMarksTree gradeBFromA(GitPathRoot commitA) throws IOException {
		final Set<GitPathRoot> successors = currentHistory.getGraph().successors(commitA);
		final Set<SubMarksTree> subs = ThrowingStream.of(successors.stream(), IOException.class)
				.filter(r -> currentHistory.getGraph().predecessors(r).size() == 1)
				.map(r -> subGrade(r, w -> gradeB(w, true))).collect(ImmutableSet.toImmutableSet());
		return SubMarksTree.given(C_B, compositeOrZero(subs, "No successor of A"));
	}

	private MarksTree gradeB(GitPathRoot commitB, boolean expandLocally) throws IOException {
		checkArgument(currentHistory.getGraph().predecessors(commitB).size() == 1);
		{
			final GitPathRoot commitA = currentHistory.getGraph().predecessors(commitB).stream()
					.collect(MoreCollectors.onlyElement());
			checkArgument(currentHistory.getGraph().predecessors(commitA).size() == 1);
		}

		final long nbFiles = Files.find(commitB, Integer.MAX_VALUE, (p, a) -> Files.isRegularFile(p)).count();
		final boolean rightNb = nbFiles == 3;
		final SubMarksTree subNb = SubMarksTree.given(C_THREE, Mark.binary(rightNb, "", "Found " + nbFiles + " files"));

		final GitPath startPath = commitB.resolve("start.txt");
		final boolean startExists = Files.exists(startPath);
		final String startContent = startExists ? Files.readString(startPath) : "";
		final Pattern patternStart = Pattern.compile("A starting point\\v*");
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

		final ImmutableSet.Builder<SubMarksTree> builder = ImmutableSet.builder();
		builder.add(subNb, subStartExists, subStartMatches, subExists, subMatches, subSomeExists, subSomeMatches);
		if (expandLocally) {
			final SubMarksTree subC = gradeCFromB(commitB);
			builder.add(subC);
		}
		return MarksTree.composite(builder.build());
	}

	private SubMarksTree gradeCFromB(GitPathRoot commitB) throws IOException {
		final GitPathRoot commitA = currentHistory.getGraph().predecessors(commitB).stream()
				.collect(MoreCollectors.onlyElement());
		final GitPathRoot start = currentHistory.getGraph().predecessors(commitA).stream()
				.collect(MoreCollectors.onlyElement());
		final Set<GitPathRoot> siblingsOfA = currentHistory.getGraph().successors(start).stream()
				.filter(r -> !r.equals(commitA)).collect(ImmutableSet.toImmutableSet());
		final Set<SubMarksTree> subs = ThrowingStream.of(siblingsOfA.stream(), IOException.class)
				.map(r -> subGrade(r, w -> gradeC(commitB, w, true))).collect(ImmutableSet.toImmutableSet());
		return SubMarksTree.given(C_C, compositeOrZero(subs, "No sibling of A"));
	}

	private MarksTree gradeC(GitPathRoot commitB, GitPathRoot commitC, boolean expandLocally) throws IOException {
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
		final Pattern patternStart = Pattern.compile("A starting point\\v*");
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

		final ImmutableSet.Builder<SubMarksTree> builder = ImmutableSet.builder();
		builder.add(subNb, subBranch, subStartExists, subStartMatches, subExists, subMatches);
		if (expandLocally) {
			final SubMarksTree subD = gradeDFromBAndC(commitB, commitC);
			builder.add(subD);
		}
		return MarksTree.composite(builder.build());
	}

	private SubMarksTree gradeDFromBAndC(GitPathRoot commitB, GitPathRoot commitC) throws IOException {
		final Set<GitPathRoot> successors = currentHistory.getGraph().successors(commitC);
		final Set<SubMarksTree> subs = ThrowingStream.of(successors.stream(), IOException.class)
				.filter(r -> currentHistory.getGraph().predecessors(r).size() == 2)
				.map(r -> subGrade(r, w -> gradeD(commitB, w))).collect(ImmutableSet.toImmutableSet());
		return SubMarksTree.given(C_D, compositeOrZero(subs, "No successor of C merging two commits"));
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
		final Pattern patternStart = Pattern.compile("A starting point\\v*");
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
