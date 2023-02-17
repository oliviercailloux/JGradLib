package io.github.oliviercailloux.java_grade.graders;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Verify.verify;

import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.MoreCollectors;
import com.google.common.graph.Graphs;
import io.github.oliviercailloux.git.fs.GitHistorySimple;
import io.github.oliviercailloux.gitjfs.GitPath;
import io.github.oliviercailloux.gitjfs.GitPathRoot;
import io.github.oliviercailloux.gitjfs.GitPathRootRef;
import io.github.oliviercailloux.gitjfs.GitPathRootSha;
import io.github.oliviercailloux.gitjfs.GitPathRootShaCached;
import io.github.oliviercailloux.grade.BatchGitHistoryGrader;
import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.GitFileSystemWithHistoryFetcherByPrefix;
import io.github.oliviercailloux.grade.GitFsGrader;
import io.github.oliviercailloux.grade.GitGrader.Predicates;
import io.github.oliviercailloux.grade.Grade;
import io.github.oliviercailloux.grade.GradeAggregator;
import io.github.oliviercailloux.grade.GradeUtils;
import io.github.oliviercailloux.grade.IGrade.CriteriaPath;
import io.github.oliviercailloux.grade.Mark;
import io.github.oliviercailloux.grade.MarksTree;
import io.github.oliviercailloux.grade.SubMark;
import io.github.oliviercailloux.grade.SubMarksTree;
import io.github.oliviercailloux.jaris.throwing.TFunction;
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

public class BranchingBis implements GitFsGrader<IOException> {
	@Deprecated
	public static SubMarksTree subGrade(GitPathRoot gitPathRoot, TFunction<GitPathRoot, MarksTree, IOException> grader)
			throws IOException, NoSuchFileException {
		final MarksTree grade = grader.apply(gitPathRoot);
		return subGrade(gitPathRoot, grade);
	}

	@Deprecated
	public static SubMarksTree subGradeC(GitPathRootShaCached gitPathRoot,
			TFunction<GitPathRootShaCached, MarksTree, IOException> grader) throws IOException, NoSuchFileException {
		final MarksTree grade = grader.apply(gitPathRoot);
		return subGrade(gitPathRoot, grade);
	}

	public static SubMarksTree subGrade(GitPathRoot gitPathRoot, MarksTree grade)
			throws IOException, NoSuchFileException {
		final String comment = "Using " + gitPathRoot.getCommit().id().getName();
		final MarksTree newTree = addComment(grade, comment);

		final Criterion criterion = Criterion.given(comment);
		return SubMarksTree.given(criterion, newTree);
	}

	public static SubMarksTree subGrade(GitPathRootShaCached gitPathRoot, MarksTree grade) {
		final String comment = "Using " + gitPathRoot.getCommit().id().getName();
		final MarksTree newTree = addComment(grade, comment);

		final Criterion criterion = Criterion.given(comment);
		return SubMarksTree.given(criterion, newTree);
	}

	public static MarksTree addComment(MarksTree grade, String comment) {
		final Criterion sub1 = grade.getCriteria().stream().findFirst().orElseThrow();
		final MarksTree tree1 = grade.getTree(sub1);
		verify(tree1.isMark());
		final Mark mark1 = tree1.getMark(CriteriaPath.ROOT);
		final String commentOriginal = mark1.getComment();
		final ImmutableMap<Criterion, MarksTree> subs = Maps.toMap(grade.getCriteria(), grade::getTree);
		final LinkedHashMap<Criterion, MarksTree> subsNew = new LinkedHashMap<>(subs);
		final String newComment = commentOriginal.isEmpty() ? comment : commentOriginal + " (" + comment + ")";
		subsNew.put(sub1, Mark.given(mark1.getPoints(), newComment));
		return MarksTree.composite(subsNew);
	}

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(BranchingBis.class);

	public static final String PREFIX = "branching-bis";

	public static final ZonedDateTime DEADLINE = ZonedDateTime.parse("2022-03-21T14:05:00+01:00[Europe/Paris]");

	public static final double USER_WEIGHT = 0.01d;

	private static final boolean EXPAND = false;

	public static void main(String[] args) throws Exception {
		final BatchGitHistoryGrader<RuntimeException> grader = BatchGitHistoryGrader
				.given(() -> GitFileSystemWithHistoryFetcherByPrefix.getRetrievingByPrefix(PREFIX));
		grader.getAndWriteGrades(DEADLINE, Duration.ofMinutes(5), new BranchingBis(), USER_WEIGHT,
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

	private GitHistorySimple currentHistory;

	private GitPathRootShaCached startPath;
	private GitPathRootShaCached aPath;
	private GitPathRootShaCached bPath;
	private GitPathRootShaCached cPath;

	BranchingBis() {
		currentHistory = null;
		startPath = null;
		aPath = null;
		bPath = null;
		cPath = null;
	}

	@Override
	public MarksTree grade(GitHistorySimple data) throws IOException {
		startPath = null;
		aPath = null;
		bPath = null;
		cPath = null;

		currentHistory = data;
		verify(!currentHistory.graph().nodes().isEmpty());

		final ImmutableSet<GitPathRootShaCached> commitsOrdered = currentHistory.roots().stream()
				.flatMap(r -> Graphs.reachableNodes(currentHistory.graph(), r).stream())
				.collect(ImmutableSet.toImmutableSet());
		final int nbCommits = commitsOrdered.size();

		final MarksTree anyCommitMark = Mark.binary(!commitsOrdered.isEmpty(),
				String.format("Found %s commit%s, including the root ones", nbCommits, nbCommits == 1 ? "" : "s"), "");
		final SubMarksTree subAny = SubMarksTree.given(C_ANY, anyCommitMark);

		final SubMarksTree subStart = gradeStartFromCommits(commitsOrdered, EXPAND);
		if (EXPAND) {
			return MarksTree.composite(ImmutableSet.of(subAny, subStart));
		}

		final SubMarksTree subA = gradeAFromStart(startPath, false);
		final SubMarksTree subB = gradeBFromA(aPath, false);
		final SubMarksTree subC = gradeCFromB(bPath, false);
		final SubMarksTree subD = gradeDFromBAndC(bPath, cPath, false);
		return MarksTree.composite(ImmutableSet.of(subAny, subStart, subA, subB, subC, subD));
	}

	private MarksTree compositeOrZero(Set<SubMarksTree> subs, String commentIfEmpty) {
		return subs.isEmpty() ? Mark.zero(commentIfEmpty) : MarksTree.composite(subs);
	}

	private ImmutableMap.Builder<Criterion, Double> startWeightsBuilder() {
		final ImmutableMap.Builder<Criterion, Double> builder = ImmutableMap.builder();
		builder.put(C_ONE, 1d);
		builder.put(C_EXISTS_START, 0.5d);
		builder.put(C_CONTENTS_START, 0.5d);
		return builder;
	}

	private ImmutableMap.Builder<Criterion, Double> aWeightsBuilder() {
		final ImmutableMap.Builder<Criterion, Double> builder = ImmutableMap.builder();
		builder.put(C_TWO, 0.2d);
		builder.put(C_BR1, 3.0d);
		builder.put(C_EXISTS_START, 0.15d);
		builder.put(C_CONTENTS_START, 0.35d);
		builder.put(C_EXISTS_FIRST, 0.15d);
		builder.put(C_CONTENTS_FIRST, 0.35d);
		return builder;
	}

	private ImmutableMap.Builder<Criterion, Double> bWeightsBuilder() {
		final ImmutableMap.Builder<Criterion, Double> builder = ImmutableMap.builder();
		builder.put(C_THREE, 1d);
		builder.put(C_EXISTS_START, 0.5d);
		builder.put(C_CONTENTS_START, 0.5d);
		builder.put(C_EXISTS_FIRST, 0.5d);
		builder.put(C_CONTENTS_FIRST, 0.5d);
		builder.put(C_EXISTS_SOME, 0.5d);
		builder.put(C_CONTENTS_SOME, 0.5d);
		return builder;
	}

	private ImmutableMap.Builder<Criterion, Double> cWeightsBuilder() {
		final ImmutableMap.Builder<Criterion, Double> builder = ImmutableMap.builder();
		builder.put(C_TWO, 0.2d);
		builder.put(C_BR2, 3.0d);
		builder.put(C_EXISTS_START, 0.15d);
		builder.put(C_CONTENTS_START, 0.35d);
		builder.put(C_EXISTS_FIRST, 0.15d);
		builder.put(C_CONTENTS_FIRST, 0.35d);
		return builder;
	}

	private GradeAggregator dAggregatorFlat() {
		final GradeAggregator dAg;
		{
			final ImmutableMap.Builder<Criterion, Double> builder = ImmutableMap.builder();
			builder.put(C_THREE, 0.1d);
			builder.put(C_BR3, 3.0d);
			builder.put(C_EXISTS_START, 0.05d);
			builder.put(C_CONTENTS_START, 0.05d);
			builder.put(C_EXISTS_FIRST, 0.1d);
			builder.put(C_CONTENTS_FIRST, 0.9d);
			builder.put(C_EXISTS_SOME, 0.05d);
			builder.put(C_CONTENTS_SOME, 0.05d);
			builder.put(C_PARENTS, 0.1d);
			dAg = GradeAggregator.staticAggregator(builder.build(), ImmutableMap.of());
		}
		return dAg;
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
			builder.put(C_START, 18.8d);
			mainAg = GradeAggregator.staticAggregator(builder.build(), ImmutableMap.of(C_START, startAg));
		}
		return mainAg;
	}

	private GradeAggregator startAggregatorExpanded() {
		final GradeAggregator aAg = aAggregatorExpanded();
		final GradeAggregator startAg;
		{
			final ImmutableMap.Builder<Criterion, Double> builder = startWeightsBuilder();
			builder.put(C_A, 16.8d);
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
			final ImmutableMap.Builder<Criterion, Double> builder = aWeightsBuilder();
			builder.put(C_B, 12.6d);
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
			final ImmutableMap.Builder<Criterion, Double> builder = bWeightsBuilder();
			builder.put(C_C, 8.6d);
			final GradeAggregator bAggregator = GradeAggregator.staticAggregator(builder.build(),
					ImmutableMap.of(C_C, cAg));
			bAg = GradeAggregator.max(bAggregator);
		}
		return bAg;
	}

	private GradeAggregator cAggregatorExpanded() {
		final GradeAggregator dAg = dAggregatorExpanded();
		final GradeAggregator cAg;
		{
			final ImmutableMap.Builder<Criterion, Double> builder = cWeightsBuilder();
			builder.put(C_D, 4.4d);
			final GradeAggregator cAggregator = GradeAggregator.staticAggregator(builder.build(),
					ImmutableMap.of(C_D, dAg));
			cAg = GradeAggregator.max(cAggregator);
		}
		return cAg;
	}

	private GradeAggregator dAggregatorExpanded() {
		return GradeAggregator.max(dAggregatorFlat());
	}

	private GradeAggregator getAggregatorFlattened() {
		final GradeAggregator dAg = dAggregatorFlat();
		final GradeAggregator cAg;
		{
			final ImmutableMap.Builder<Criterion, Double> builder = cWeightsBuilder();
			cAg = GradeAggregator.staticAggregator(builder.build(), ImmutableMap.of());
		}
		final GradeAggregator bAg;
		{
			final ImmutableMap.Builder<Criterion, Double> builder = bWeightsBuilder();
			bAg = GradeAggregator.staticAggregator(builder.build(), ImmutableMap.of());
		}
		final GradeAggregator aAg;
		{
			final ImmutableMap.Builder<Criterion, Double> builder = aWeightsBuilder();
			aAg = GradeAggregator.staticAggregator(builder.build(), ImmutableMap.of());
		}
		final GradeAggregator startAg;
		{
			final ImmutableMap.Builder<Criterion, Double> builder = startWeightsBuilder();
			startAg = GradeAggregator.staticAggregator(builder.build(), ImmutableMap.of());
		}
		final GradeAggregator mainAg;
		{
			final ImmutableMap.Builder<Criterion, Double> builder = ImmutableMap.builder();
			builder.put(C_ANY, 1d);
			builder.put(C_START, 2d);
			builder.put(C_A, 4.2d);
			builder.put(C_B, 4.0d);
			builder.put(C_C, 4.2d);
			builder.put(C_D, 4.4d);
			mainAg = GradeAggregator.staticAggregator(builder.build(),
					ImmutableMap.of(C_START, startAg, C_A, aAg, C_B, bAg, C_C, cAg, C_D, dAg));
		}
		return mainAg;
	}

	private SubMarksTree gradeStartFromCommits(ImmutableSet<GitPathRootShaCached> commitsOrdered, boolean expandLocally)
			throws IOException {
		final ImmutableBiMap<GitPathRootShaCached, SubMarksTree> subs = ThrowingStream
				.of(commitsOrdered.stream(), IOException.class)
				.filter(r -> currentHistory.graph().predecessors(r).size() <= 1)
				.collect(ImmutableBiMap.toImmutableBiMap(r -> r, r -> {
					try {
						return subGradeC(r, w -> gradeStart(w, true));
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

	private MarksTree gradeStart(GitPathRootShaCached commitStart, boolean expandLocally) throws IOException {
		final long nbFiles = Files.find(commitStart, Integer.MAX_VALUE, (p, a) -> Files.isRegularFile(p)).count();
		final boolean rightNb = nbFiles == 1;
		final SubMarksTree subNb = SubMarksTree.given(C_ONE, Mark.binary(rightNb, "", "Found " + nbFiles + " files"));

		final GitPath startFilePath = commitStart.resolve("starting point.txt");
		final boolean startExists = Files.exists(startFilePath);
		final String startContent = startExists ? Files.readString(startFilePath) : "";
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

	private SubMarksTree gradeAFromStart(GitPathRootShaCached commitStart, boolean expandLocally) throws IOException {
		if (commitStart == null) {
			return SubMark.given(C_A, Mark.zero());
		}

		final Set<GitPathRootShaCached> successors = currentHistory.graph().successors(commitStart);
		final ImmutableBiMap<GitPathRootShaCached, SubMarksTree> subs = ThrowingStream
				.of(successors.stream(), IOException.class)
				.filter(r -> currentHistory.graph().predecessors(r).size() == 1)
				.collect(ImmutableBiMap.toImmutableBiMap(r -> r, r -> {
					try {
						return subGradeC(r, w -> gradeA(w, true));
					} catch (NoSuchFileException e) {
						throw new IllegalStateException(e);
					} catch (IOException e) {
						throw new IllegalStateException(e);
					}
				}));
		final MarksTree tree = compositeOrZero(subs.values(), "No successor of Start");
		if (expandLocally || tree.isMark()) {
			return SubMarksTree.given(C_A, tree);
		}
		final ImmutableMap<SubMark, Double> weightedSubMarks = Grade.given(aAggregatorExpanded(), tree)
				.getWeightedSubMarks();
		final Criterion bestPath = Maps.filterValues(weightedSubMarks, w -> w == 1d).keySet().stream()
				.map(SubMark::getCriterion).collect(MoreCollectors.onlyElement());
		aPath = Maps.filterValues(subs, s -> s.getCriterion().equals(bestPath)).keySet().stream()
				.collect(MoreCollectors.onlyElement());
		return SubMarksTree.given(C_A, gradeA(aPath, false));
	}

	private MarksTree gradeA(GitPathRootShaCached commitA, boolean expandLocally) throws IOException {
		checkArgument(currentHistory.graph().predecessors(commitA).size() == 1);

		final long nbFiles = Files.find(commitA, Integer.MAX_VALUE, (p, a) -> Files.isRegularFile(p)).count();
		final boolean rightNb = nbFiles == 2;
		final SubMarksTree subNb = SubMarksTree.given(C_TWO, Mark.binary(rightNb, "", "Found " + nbFiles + " files"));

		final boolean isBranch = ThrowingStream.of(refsTo(commitA.toSha()).stream(), IOException.class)
				.filter(GitPathRoot::isRef).map(GitPathRoot::getGitRef).anyMatch(r -> Predicates.isBranch(r, "br1"));
		final SubMarksTree subBranch = SubMarksTree.given(C_BR1, Mark.binary(isBranch));

		final GitPath startFilePath = commitA.resolve("starting point.txt");
		final boolean startExists = Files.exists(startFilePath);
		final String startContent = startExists ? Files.readString(startFilePath) : "";
		final Pattern patternStart = Pattern.compile("A starting point\\v*");
		final boolean startMatches = patternStart.matcher(startContent).matches();
		final SubMarksTree subStartExists = SubMarksTree.given(C_EXISTS_START, Mark.binary(startExists));
		final SubMarksTree subStartMatches = SubMarksTree.given(C_CONTENTS_START, Mark.binary(startMatches));

		final GitPath firstPath = commitA.resolve("first file.txt");
		final boolean firstExists = Files.exists(firstPath);
		final String firstContent = firstExists ? Files.readString(firstPath) : "";
		final Pattern patternFirst = Pattern.compile("Hello\\h?!\\v*");
		verify(patternFirst.matcher("Hello!").matches());
		verify(patternFirst.matcher("Hello !\n").matches());
		verify(!patternFirst.matcher("Hello").matches());
		final boolean firstMatches = patternFirst.matcher(firstContent).matches();
		final SubMarksTree subExists = SubMarksTree.given(C_EXISTS_FIRST, Mark.binary(firstExists));
		final SubMarksTree subMatches = SubMarksTree.given(C_CONTENTS_FIRST, Mark.binary(firstMatches));

		final ImmutableSet.Builder<SubMarksTree> builder = ImmutableSet.builder();
		builder.add(subNb, subBranch, subStartExists, subStartMatches, subExists, subMatches);
		if (expandLocally) {
			final SubMarksTree subB = gradeBFromA(commitA, true);
			builder.add(subB);
		}
		return MarksTree.composite(builder.build());
	}

	private SubMarksTree gradeBFromA(GitPathRootShaCached commitA, boolean expandLocally) throws IOException {
		if (commitA == null) {
			return SubMark.given(C_B, Mark.zero());
		}

		final Set<GitPathRootShaCached> successors = currentHistory.graph().successors(commitA);
		final ImmutableBiMap<GitPathRootShaCached, SubMarksTree> subs = ThrowingStream
				.of(successors.stream(), IOException.class)
				.filter(r -> currentHistory.graph().predecessors(r).size() == 1)
				.collect(ImmutableBiMap.toImmutableBiMap(r -> r, r -> {
					try {
						return subGradeC(r, w -> gradeB(w, true));
					} catch (NoSuchFileException e) {
						throw new IllegalStateException(e);
					} catch (IOException e) {
						throw new IllegalStateException(e);
					}
				}));
		final MarksTree tree = compositeOrZero(subs.values(), "No successor of A");
		if (expandLocally || tree.isMark()) {
			return SubMarksTree.given(C_B, tree);
		}
		final ImmutableMap<SubMark, Double> weightedSubMarks = Grade.given(bAggregatorExpanded(), tree)
				.getWeightedSubMarks();
		final Criterion bestPath = Maps.filterValues(weightedSubMarks, w -> w == 1d).keySet().stream()
				.map(SubMark::getCriterion).collect(MoreCollectors.onlyElement());
		bPath = Maps.filterValues(subs, s -> s.getCriterion().equals(bestPath)).keySet().stream()
				.collect(MoreCollectors.onlyElement());
		return SubMarksTree.given(C_B, gradeB(bPath, false));
	}

	private MarksTree gradeB(GitPathRootShaCached commitB, boolean expandLocally) throws IOException {
		checkArgument(currentHistory.graph().predecessors(commitB).size() == 1);
		{
			final GitPathRootShaCached commitA = currentHistory.graph().predecessors(commitB).stream()
					.collect(MoreCollectors.onlyElement());
			checkArgument(currentHistory.graph().predecessors(commitA).size() == 1);
		}

		final long nbFiles = Files.find(commitB, Integer.MAX_VALUE, (p, a) -> Files.isRegularFile(p)).count();
		final boolean rightNb = nbFiles == 3;
		final SubMarksTree subNb = SubMarksTree.given(C_THREE, Mark.binary(rightNb, "", "Found " + nbFiles + " files"));

		final GitPath startFilePath = commitB.resolve("starting point.txt");
		final boolean startExists = Files.exists(startFilePath);
		final String startContent = startExists ? Files.readString(startFilePath) : "";
		final Pattern patternStart = Pattern.compile("A starting point\\v*");
		final boolean startMatches = patternStart.matcher(startContent).matches();
		final SubMarksTree subStartExists = SubMarksTree.given(C_EXISTS_START, Mark.binary(startExists));
		final SubMarksTree subStartMatches = SubMarksTree.given(C_CONTENTS_START, Mark.binary(startMatches));

		final GitPath firstPath = commitB.resolve("first file.txt");
		final boolean firstExists = Files.exists(firstPath);
		final String firstContent = firstExists ? Files.readString(firstPath) : "";
		final Pattern patternFirst = Pattern.compile("Hello\\h?!\\v*");
		final boolean firstMatches = patternFirst.matcher(firstContent).matches();
		final SubMarksTree subExists = SubMarksTree.given(C_EXISTS_FIRST, Mark.binary(firstExists));
		final SubMarksTree subMatches = SubMarksTree.given(C_CONTENTS_FIRST, Mark.binary(firstMatches));

		final GitPath somePath = commitB.resolve("My folder/second file.txt");
		final boolean someExists = Files.exists(somePath);
		final String someContent = someExists ? Files.readString(somePath) : "";
		final Pattern somePattern = Pattern.compile("Second\\v*");
		final boolean someMatches = somePattern.matcher(someContent).matches();
		final SubMarksTree subSomeExists = SubMarksTree.given(C_EXISTS_SOME, Mark.binary(someExists));
		final SubMarksTree subSomeMatches = SubMarksTree.given(C_CONTENTS_SOME, Mark.binary(someMatches));

		final ImmutableSet.Builder<SubMarksTree> builder = ImmutableSet.builder();
		builder.add(subNb, subStartExists, subStartMatches, subExists, subMatches, subSomeExists, subSomeMatches);
		if (expandLocally) {
			final SubMarksTree subC = gradeCFromB(commitB, true);
			builder.add(subC);
		}
		return MarksTree.composite(builder.build());
	}

	private SubMarksTree gradeCFromB(GitPathRootShaCached commitB, boolean expandLocally) throws IOException {
		if (commitB == null) {
			return SubMark.given(C_C, Mark.zero());
		}

		final GitPathRootShaCached commitA = currentHistory.graph().predecessors(commitB).stream()
				.collect(MoreCollectors.onlyElement());
		final GitPathRootShaCached start = currentHistory.graph().predecessors(commitA).stream()
				.collect(MoreCollectors.onlyElement());
		final Set<GitPathRootShaCached> siblingsOfA = currentHistory.graph().successors(start).stream()
				.filter(r -> !r.equals(commitA)).collect(ImmutableSet.toImmutableSet());
		final ImmutableBiMap<GitPathRootShaCached, SubMarksTree> subs = ThrowingStream
				.of(siblingsOfA.stream(), IOException.class).collect(ImmutableBiMap.toImmutableBiMap(r -> r, r -> {
					try {
						return subGradeC(r, w -> gradeC(commitB, w, true));
					} catch (NoSuchFileException e) {
						throw new IllegalStateException(e);
					} catch (IOException e) {
						throw new IllegalStateException(e);
					}
				}));
		final MarksTree tree = compositeOrZero(subs.values(), "No sibling of A");
		if (expandLocally || tree.isMark()) {
			return SubMarksTree.given(C_C, tree);
		}
		final ImmutableMap<SubMark, Double> weightedSubMarks = Grade.given(cAggregatorExpanded(), tree)
				.getWeightedSubMarks();
		final Criterion bestPath = Maps.filterValues(weightedSubMarks, w -> w == 1d).keySet().stream()
				.map(SubMark::getCriterion).collect(MoreCollectors.onlyElement());
		cPath = Maps.filterValues(subs, s -> s.getCriterion().equals(bestPath)).keySet().stream()
				.collect(MoreCollectors.onlyElement());
		return SubMarksTree.given(C_C, gradeC(commitB, cPath, false));
	}

	private MarksTree gradeC(GitPathRootShaCached commitB, GitPathRootShaCached commitC, boolean expandLocally)
			throws IOException {
		final long nbFiles = Files.find(commitC, Integer.MAX_VALUE, (p, a) -> Files.isRegularFile(p)).count();
		final boolean rightNb = nbFiles == 2;
		final SubMarksTree subNb = SubMarksTree.given(C_TWO, Mark.binary(rightNb, "", "Found " + nbFiles + " files"));

		final boolean isBranch = ThrowingStream.of(refsTo(commitC.toSha()).stream(), IOException.class)
				.filter(GitPathRoot::isRef).map(GitPathRoot::getGitRef).anyMatch(r -> Predicates.isBranch(r, "br2"));
		final SubMarksTree subBranch = SubMarksTree.given(C_BR2, Mark.binary(isBranch));

		final GitPath startFilePath = commitC.resolve("starting point.txt");
		final boolean startExists = Files.exists(startFilePath);
		final String startContent = startExists ? Files.readString(startFilePath) : "";
		final Pattern patternStart = Pattern.compile("A starting point\\v*");
		final boolean startMatches = patternStart.matcher(startContent).matches();
		final SubMarksTree subStartExists = SubMarksTree.given(C_EXISTS_START, Mark.binary(startExists));
		final SubMarksTree subStartMatches = SubMarksTree.given(C_CONTENTS_START, Mark.binary(startMatches));

		final GitPath firstPath = commitC.resolve("first file.txt");
		final boolean firstExists = Files.exists(firstPath);
		final String firstContent = firstExists ? Files.readString(firstPath) : "";
		final Pattern pattern = Pattern.compile("Coucou\\h?!\\v*");
		final boolean firstMatches = pattern.matcher(firstContent).matches();
		final SubMarksTree subExists = SubMarksTree.given(C_EXISTS_FIRST, Mark.binary(firstExists));
		final SubMarksTree subMatches = SubMarksTree.given(C_CONTENTS_FIRST, Mark.binary(firstMatches));

		final ImmutableSet.Builder<SubMarksTree> builder = ImmutableSet.builder();
		builder.add(subNb, subBranch, subStartExists, subStartMatches, subExists, subMatches);
		if (expandLocally) {
			final SubMarksTree subD = gradeDFromBAndC(commitB, commitC, true);
			builder.add(subD);
		}
		return MarksTree.composite(builder.build());
	}

	private SubMarksTree gradeDFromBAndC(GitPathRootShaCached commitB, GitPathRootShaCached commitC,
			boolean expandLocally) throws IOException {
		if (commitB == null || commitC == null) {
			return SubMark.given(C_D, Mark.zero());
		}

		final Set<GitPathRootShaCached> successors = currentHistory.graph().successors(commitC);
		final ImmutableBiMap<GitPathRootShaCached, SubMarksTree> subs = ThrowingStream
				.of(successors.stream(), IOException.class)
				.filter(r -> currentHistory.graph().predecessors(r).size() == 2)
				.map(r -> new RootedMarksTree(r.toShaCached(), gradeD(commitB, r)))
				.collect(ImmutableBiMap.toImmutableBiMap(RootedMarksTree::root, RootedMarksTree::commented));
//		final ImmutableBiMap<GitPathRootShaCached, SubMarksTree> subs = ThrowingStream.of(successors.stream(), IOException.class)
//				.filter(r -> currentHistory.graph().predecessors(r).size() == 2)
//				.collect(ImmutableBiMap.toImmutableBiMap(r -> r, r -> {
//					try {
//						return subGrade(r, w -> gradeD(commitB, w));
//					} catch (NoSuchFileException e) {
//						throw new IllegalStateException(e);
//					} catch (IOException e) {
//						throw new IllegalStateException(e);
//					}
//				}));
		final MarksTree tree = compositeOrZero(subs.values(), "No successor of C merging two commits");
		if (expandLocally || tree.isMark()) {
			return SubMarksTree.given(C_D, tree);
		}
		final ImmutableMap<SubMark, Double> weightedSubMarks = Grade.given(dAggregatorExpanded(), tree)
				.getWeightedSubMarks();
		final Criterion bestPath = Maps.filterValues(weightedSubMarks, w -> w == 1d).keySet().stream()
				.map(SubMark::getCriterion).collect(MoreCollectors.onlyElement());
		final GitPathRootShaCached bestSubMark = Maps.filterValues(subs, s -> s.getCriterion().equals(bestPath))
				.keySet().stream().collect(MoreCollectors.onlyElement());
		return SubMarksTree.given(C_D, gradeD(commitB, bestSubMark));
	}

	private MarksTree gradeD(GitPathRootShaCached commitC, GitPathRootShaCached commitD) throws IOException {
		final long nbFiles = Files.find(commitD, Integer.MAX_VALUE, (p, a) -> Files.isRegularFile(p)).count();
		final boolean rightNb = nbFiles == 3;
		final SubMarksTree subNb = SubMarksTree.given(C_THREE, Mark.binary(rightNb, "", "Found " + nbFiles + " files"));

		final boolean isBranch = ThrowingStream.of(refsTo(commitD.toSha()).stream(), IOException.class)
				.filter(GitPathRoot::isRef).map(GitPathRoot::getGitRef).anyMatch(r -> Predicates.isBranch(r, "br3"));
		final SubMarksTree subBranch = SubMarksTree.given(C_BR3, Mark.binary(isBranch));

		final GitPath startFilePath = commitD.resolve("starting point.txt");
		final boolean startExists = Files.exists(startFilePath);
		final String startContent = startExists ? Files.readString(startFilePath) : "";
		final Pattern patternStart = Pattern.compile("A starting point\\v*");
		final boolean startMatches = patternStart.matcher(startContent).matches();
		final SubMarksTree subStartExists = SubMarksTree.given(C_EXISTS_START, Mark.binary(startExists));
		final SubMarksTree subStartMatches = SubMarksTree.given(C_CONTENTS_START, Mark.binary(startMatches));

		final GitPath firstPath = commitD.resolve("first file.txt");
		final boolean firstExists = Files.exists(firstPath);
		final String firstContent = firstExists ? Files.readString(firstPath) : "";
		final Pattern patternFirst = Pattern.compile("Hello\\h?!\\v+Coucou\\h?!\\v*");
		verify(patternFirst.matcher("Hello!\nCoucou !").matches());
		verify(patternFirst.matcher("Hello !\n\nCoucou!\n").matches());
		verify(!patternFirst.matcher("Hello!").matches());
		verify(!patternFirst.matcher("Hello!\nCoucou").matches());
		final boolean firstMatches = patternFirst.matcher(firstContent).matches();
		final SubMarksTree subFirstExists = SubMarksTree.given(C_EXISTS_FIRST, Mark.binary(firstExists));
		final SubMarksTree subFirstMatches = SubMarksTree.given(C_CONTENTS_FIRST, Mark.binary(firstMatches));

		final GitPath somePath = commitD.resolve("My folder/second file.txt");
		final boolean someExists = Files.exists(somePath);
		final String someContent = someExists ? Files.readString(somePath) : "";
		final Pattern somePattern = Pattern.compile("Second\\v*");
		final boolean someMatches = somePattern.matcher(someContent).matches();
		final SubMarksTree subSomeExists = SubMarksTree.given(C_EXISTS_SOME, Mark.binary(someExists));
		final SubMarksTree subSomeMatches = SubMarksTree.given(C_CONTENTS_SOME, Mark.binary(someMatches));

		final Set<GitPathRootShaCached> parents = currentHistory.graph().predecessors(commitD);
		final boolean rightParents = parents.contains(commitC) && parents.size() == 2;
		final SubMarksTree subParents = SubMarksTree.given(C_PARENTS, Mark.binary(rightParents));

		return MarksTree.composite(ImmutableSet.of(subNb, subBranch, subStartExists, subStartMatches, subFirstExists,
				subFirstMatches, subSomeExists, subSomeMatches, subParents));
	}

	private ImmutableSet<GitPathRootRef> refsTo(GitPathRootSha target) throws IOException {
		return GradeUtils.getRefsTo(currentHistory.fs().refs(), target);
	}
}
