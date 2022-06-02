package io.github.oliviercailloux.java_grade.graders;

import static com.google.common.base.Verify.verify;
import static io.github.oliviercailloux.grade.GitGrader.Predicates.compose;
import static io.github.oliviercailloux.grade.GitGrader.Predicates.contentMatches;
import static io.github.oliviercailloux.grade.GitGrader.Predicates.isFileNamed;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.MoreCollectors;
import com.google.common.collect.Sets;
import com.google.common.graph.Graphs;
import io.github.oliviercailloux.git.fs.GitPath;
import io.github.oliviercailloux.git.fs.GitPathRoot;
import io.github.oliviercailloux.grade.BatchGitHistoryGrader;
import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.GitFileSystemHistory;
import io.github.oliviercailloux.grade.GitFileSystemWithHistoryFetcherByPrefix;
import io.github.oliviercailloux.grade.GitGrader.Functions;
import io.github.oliviercailloux.grade.GitGrader.Predicates;
import io.github.oliviercailloux.grade.GradeAggregator;
import io.github.oliviercailloux.grade.GitFsGrader;
import io.github.oliviercailloux.grade.Mark;
import io.github.oliviercailloux.grade.MarksTree;
import io.github.oliviercailloux.grade.SubMark;
import io.github.oliviercailloux.grade.SubMarksTree;
import io.github.oliviercailloux.grade.markers.Marks;
import io.github.oliviercailloux.jaris.exceptions.CheckedStream;
import io.github.oliviercailloux.jaris.exceptions.Throwing.Function;
import io.github.oliviercailloux.jaris.exceptions.Throwing.Predicate;
import io.github.oliviercailloux.jaris.throwing.TOptional;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.eclipse.jgit.diff.DiffEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EclipseBis implements GitFsGrader<IOException> {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(EclipseBis.class);

	public static final String PREFIX = "eclipse";

	public static final ZonedDateTime DEADLINE = ZonedDateTime.parse("2022-03-23T14:10:00+01:00[Europe/Paris]");

	public static final double USER_WEIGHT = 0.01d;

	public static void main(String[] args) throws Exception {
		final BatchGitHistoryGrader<RuntimeException> grader = BatchGitHistoryGrader
				.given(() -> GitFileSystemWithHistoryFetcherByPrefix.getRetrievingByPrefix(PREFIX));
		grader.getAndWriteGrades(DEADLINE, Duration.ofMinutes(5), new EclipseBis(), USER_WEIGHT,
				Path.of("grades " + PREFIX), PREFIX + " " + Instant.now().atZone(DEADLINE.getZone()));
	}

	private static final Criterion C_ANY = Criterion.given("Anything committed");
	private static final Criterion C_COMPILE = Criterion.given("Error corrected");
	private static final Criterion C_MAIN = Criterion.given("Main");

	private static final Criterion C_SINGLE_CHANGE = Criterion.given("Single change");

	private static final Criterion C_WARNING = Criterion.given("Warning corrected");
	private static final Criterion C_RENAME = Criterion.given("FactoryAdapter → MyFactoryAdapter");
	private static final Criterion C_MAIN_RENAME = Criterion.given("Main rename");

	private static final Criterion C_USER_ADAPT = Criterion.given("Renamed in FactoryAdapterUser classes");

	private static final Criterion C_SOC = Criterion.given("courses.soc");
	private static final Criterion C_NUMBER = Criterion.given("Number");
	private static final Criterion C_FORMATTING = Criterion.given("Formatting");

	private static final Criterion C_FORMATTED = Criterion.given("Formatted");

	private static final Criterion C_STYLE = Criterion.given("Using Google Style");

	private static final Criterion C_LIMITED_CHANGES = Criterion.given("Limited changes");

	private GitFileSystemHistory history;

	EclipseBis() {
		history = null;
		/* Nothing */
	}

	@Override
	public MarksTree grade(GitFileSystemHistory data) throws IOException {
		history = data;
		final ImmutableSet.Builder<SubMarksTree> gradeBuilder = ImmutableSet.builder();

		final ImmutableSet<GitPathRoot> commitsOrdered = data.getRoots().stream()
				.flatMap(r -> Graphs.reachableNodes(data.getGraph(), r).stream())
				.collect(ImmutableSet.toImmutableSet());
		verify(!commitsOrdered.isEmpty());
		final ImmutableSet<GitPathRoot> commits = Sets.difference(commitsOrdered, data.getRoots()).immutableCopy();

		{
			final int nbCommits = commits.size();
			gradeBuilder.add(SubMarksTree.given(C_ANY,
					Mark.binary(!commits.isEmpty(), String.format("Found %s commit%s, not counting the root ones",
							nbCommits, nbCommits == 1 ? "" : "s"), "")));
		}
		{
			LOGGER.info("Grading compile.");
			final ImmutableSet<SubMarksTree> subs = CheckedStream.<GitPathRoot, IOException>from(commits).map(
					p -> new RootedMarksTree(p.toShaCached(), toTree(compiles(p), singleChangeAbout(p, "Oracle.java"))))
					.map(RootedMarksTree::commented).collect(ImmutableSet.toImmutableSet());
			gradeBuilder.add(SubMarksTree.given(C_COMPILE, MarksTree.composite(subs)));
		}

		final String testContent = """
				package io.github.oliviercailloux.minimax.experiment.json.user;

				import io.github.oliviercailloux.minimax.experiment.json.MyFactoryAdapter;

				public class FactoryAdapterUser {
				  public static void main(String[] args) {
				    final MyFactoryAdapter instance = MyFactoryAdapter.INSTANCE;
				    System.out.println(instance.getClass());
				  }
				}""";
		verify(renamed(testContent));
		{
			LOGGER.info("Grading warning.");
			final ImmutableSet<SubMarksTree> subs = CheckedStream.<GitPathRoot, IOException>from(commits)
					.map(p -> new RootedMarksTree(p.toShaCached(),
							toTree(warning(p), singleChangeAbout(p, "RegretComputer.java"))))
					.map(RootedMarksTree::commented).collect(ImmutableSet.toImmutableSet());
			gradeBuilder.add(SubMarksTree.given(C_WARNING, MarksTree.composite(subs)));
		}
		{
			LOGGER.info("Grading rename.");
			final ImmutableSet<SubMarksTree> subs = CheckedStream.<GitPathRoot, IOException>from(commits)
					.map(p -> new RootedMarksTree(p.toShaCached(), toRenameTree(p))).map(RootedMarksTree::commented)
					.collect(ImmutableSet.toImmutableSet());
			gradeBuilder.add(SubMarksTree.given(C_RENAME, MarksTree.composite(subs)));
		}
		{
			LOGGER.info("Grading courses.");
			final ImmutableSet<SubMarksTree> subs = CheckedStream.<GitPathRoot, IOException>from(commits)
					.map(p -> new RootedMarksTree(p.toShaCached(),
							toTree(coursesChange(p), singleChangeAbout(p, "courses.soc"))))
					.map(RootedMarksTree::commented).collect(ImmutableSet.toImmutableSet());
			gradeBuilder.add(SubMarksTree.given(C_SOC, MarksTree.composite(subs)));
		}
		{
			LOGGER.info("Grading number.");
			final ImmutableSet<SubMarksTree> subs = CheckedStream.<GitPathRoot, IOException>from(commits)
					.map(p -> new RootedMarksTree(p.toShaCached(),
							toTree(numberChange(p), singleChangeAbout(p, "Oracles m = 5, n = 9, 100.json"))))
					.map(RootedMarksTree::commented).collect(ImmutableSet.toImmutableSet());
			gradeBuilder.add(SubMarksTree.given(C_NUMBER, MarksTree.composite(subs)));
		}
		{
			LOGGER.info("Grading formatting.");
			final ImmutableSet<SubMarksTree> subs = CheckedStream.<GitPathRoot, IOException>from(commits)
					.map(p -> new RootedMarksTree(p.toShaCached(), toFormattingTree(p))).map(RootedMarksTree::commented)
					.collect(ImmutableSet.toImmutableSet());
			gradeBuilder.add(SubMarksTree.given(C_FORMATTING, MarksTree.composite(subs)));
		}

		return MarksTree.composite(gradeBuilder.build());
	}

	@Override
	public GradeAggregator getAggregator() {
		final ImmutableMap.Builder<Criterion, Double> builder = ImmutableMap.builder();
		builder.put(C_ANY, 0.8d);
		builder.put(C_COMPILE, 3.0d);
		builder.put(C_WARNING, 2.5d);
		builder.put(C_RENAME, 3.5d);
		builder.put(C_SOC, 3.5d);
		builder.put(C_NUMBER, 3.0d);
		builder.put(C_FORMATTING, 3.5d);
		final ImmutableMap<Criterion, Double> mainWeights = builder.build();

		final GradeAggregator subAg = GradeAggregator.staticAggregator(ImmutableMap.of(C_MAIN, 7d, C_SINGLE_CHANGE, 3d),
				ImmutableMap.of());
		final GradeAggregator renameAg = GradeAggregator
				.staticAggregator(ImmutableMap.of(C_MAIN_RENAME, 0.2d, C_USER_ADAPT, 0.8d), ImmutableMap.of());
//		final GradeAggregator fmtAg = GradeAggregator.staticAggregator(
//				ImmutableMap.of(C_FORMATTED, 0d, C_STYLE, 7d, C_SINGLE_CHANGE, 3d), ImmutableMap.of());
		final GradeAggregator fmtAg = GradeAggregator.staticAggregator(
				ImmutableMap.of(C_MAIN, 7d, C_LIMITED_CHANGES, 3d), ImmutableMap.of(C_MAIN, GradeAggregator.MIN));

		final ImmutableMap.Builder<Criterion, GradeAggregator> subsBuilder = ImmutableMap.builder();
		subsBuilder.put(C_COMPILE, GradeAggregator.max(subAg));
		subsBuilder.put(C_WARNING, GradeAggregator.max(subAg));
		subsBuilder.put(C_RENAME, GradeAggregator.max(renameAg));
		subsBuilder.put(C_SOC, GradeAggregator.max(subAg));
		subsBuilder.put(C_NUMBER, GradeAggregator.max(subAg));
		subsBuilder.put(C_FORMATTING, GradeAggregator.max(fmtAg));
		return GradeAggregator.staticAggregator(mainWeights, subsBuilder.build());
	}

	private MarksTree toTree(boolean main, boolean singleChange) {
		final SubMarksTree mainMark = SubMarksTree.given(C_MAIN, Mark.binary(main));
		final SubMarksTree singleChangeMark = SubMarksTree.given(C_SINGLE_CHANGE, Mark.binary(singleChange));
		return MarksTree.composite(ImmutableSet.of(mainMark, singleChangeMark));
	}

	private MarksTree toRenameTree(GitPathRoot path) throws IOException {
		final SubMark renamed;
		{
			final Pattern patternFA = Pattern.compile("FactoryAdapter");
			final Pattern patternMyFA = Pattern.compile("MyFactoryAdapter");
			final Optional<Path> fA = Files.find(path, Integer.MAX_VALUE, (p, a) -> Files.isRegularFile(p))
					.filter(p -> p.getFileName().toString().equals("FactoryAdapter.java"))
					.collect(MoreCollectors.toOptional());
			final Optional<Path> myFA = Files.find(path, Integer.MAX_VALUE, (p, a) -> Files.isRegularFile(p))
					.filter(p -> p.getFileName().toString().equals("MyFactoryAdapter.java"))
					.collect(MoreCollectors.toOptional());
			final String myFAContent = myFA.isEmpty() ? "" : Files.readString(myFA.get());
			final long nbFA = patternFA.matcher(myFAContent).results().count();
			final long nbMyFA = patternMyFA.matcher(myFAContent).results().count();
			final boolean right = fA.isEmpty() && nbFA == nbMyFA && nbMyFA == 4;
			renamed = SubMark.given(C_MAIN_RENAME, Mark.binary(right));
		}
		final ImmutableSet<Path> users = Files.find(path, Integer.MAX_VALUE, (p, a) -> Files.isRegularFile(p))
				.filter(p -> p.getFileName().toString().matches("FactoryAdapterUser[0-9]*.java"))
				.collect(ImmutableSet.toImmutableSet());
		final ImmutableSet<Path> failedRenaming = CheckedStream.<Path, IOException>from(users)
				.filter(p -> !renamed(Files.readString(p))).collect(ImmutableSet.toImmutableSet());
		final SubMark renamedUsers = SubMark.given(C_USER_ADAPT, Mark.binary(failedRenaming.isEmpty()));

		return MarksTree.composite(ImmutableSet.of(renamed, renamedUsers));
	}

	private boolean renamed(String userContent) {
		final Pattern patternFA = Pattern.compile("FactoryAdapter");
		final Pattern patternMyFA = Pattern.compile("MyFactoryAdapter");
		final Pattern patternFAU = Pattern.compile("FactoryAdapterUser");
		final Pattern patternMyFAU = Pattern.compile("MyFactoryAdapterUser");
		final long nbFA = patternFA.matcher(userContent).results().count();
		final long nbMyFA = patternMyFA.matcher(userContent).results().count();
		final long nbFAU = patternFAU.matcher(userContent).results().count();
		final long nbMyFAU = patternMyFAU.matcher(userContent).results().count();
		LOGGER.debug("Renamed output, in order: {}, {}, {}, {}.", nbFA, nbMyFA, nbFAU, nbMyFAU);
		return nbFA == (nbMyFA + nbFAU) && nbMyFA == 3 && nbFAU == 1 && nbMyFAU == 0;
	}

	boolean compiles(GitPathRoot p) throws IOException {
		LOGGER.debug("Files matching.");
		final Function<GitPathRoot, ImmutableSet<GitPath>, IOException> filesMatching = Functions
				.filesMatching(isFileNamed("Oracle.java"));
		final ImmutableSet<GitPath> matching = filesMatching.apply(p);
		LOGGER.debug("Files matching found: {}.", matching);
		final Pattern patternCompiles = Pattern.compile(".*^(?<indent>\\h+)return[\\v\\h]+alternatives.[\\v\\h]*size.*",
				Pattern.DOTALL | Pattern.MULTILINE);
		final Predicate<ImmutableSet<GitPath>, IOException> singletonAndMatch = Predicates
				.singletonAndMatch(contentMatches(patternCompiles));
		final boolean tested = singletonAndMatch.test(matching);
		LOGGER.debug("Predicate known: {}.", tested);
		return tested;
		// return compose(filesMatching, singletonAndMatch).test(p);
	}

	boolean singleChangeAbout(GitPathRoot p, String file) throws IOException {
		final Set<GitPathRoot> predecessors = history.getGraph().predecessors(p);
		return (predecessors.size() == 1) && singleDiffAbout(Iterables.getOnlyElement(predecessors), p, file);
	}

	boolean changesAbout(GitPathRoot p, Set<String> files) throws IOException {
		final Set<GitPathRoot> predecessors = history.getGraph().predecessors(p);
		return (predecessors.size() == 1) && diffsAbout(Iterables.getOnlyElement(predecessors), p, files);
	}

	private boolean singleDiffAbout(GitPathRoot predecessor, GitPathRoot p, String file) throws IOException {
		final ImmutableSet<DiffEntry> diff = history.getDiff(predecessor, p);
		return diff.size() == 1 && diffIsAboutFile(Iterables.getOnlyElement(diff), file);
	}

	private boolean diffsAbout(GitPathRoot predecessor, GitPathRoot p, Set<String> files) throws IOException {
		final ImmutableSet<DiffEntry> diff = history.getDiff(predecessor, p);
		return diff.stream().allMatch(d -> diffsAreAbout(d, files));
	}

	private boolean diffIsAboutFile(DiffEntry singleDiff, String file) {
		return singleDiff.getOldPath().contains(file) && singleDiff.getNewPath().contains(file);
	}

	private boolean diffsAreAbout(DiffEntry singleDiff, Set<String> files) {
		LOGGER.debug("Old: {}, new: {}.", singleDiff.getOldPath(), singleDiff.getNewPath());
		return files.stream().anyMatch(f -> singleDiff.getOldPath().contains(f))
				&& files.stream().anyMatch(f -> singleDiff.getNewPath().contains(f));
	}

	boolean warning(GitPathRoot path) throws IOException {
		final Optional<Path> f = Files.find(path, Integer.MAX_VALUE, (p, a) -> Files.isRegularFile(p))
				.filter(p -> p.getFileName().toString().equals("RegretComputer.java"))
				.collect(MoreCollectors.toOptional());
		final Pattern pattern = Pattern.compile("x = y");
		final String content = TOptional.wrapping(f).map(Files::readString).orElse("");
		return !pattern.matcher(content).find();
	}

	private boolean coursesChange(GitPathRoot p) throws IOException {
		return compose(Functions.filesMatching(isFileNamed("courses.soc")),
				Predicates.singletonAndMatch(contentMatches(Pattern.compile("^8[\\r\\n]1.+", Pattern.DOTALL)))).test(p);
	}

	private boolean numberChange(GitPathRoot p) throws IOException {
		return compose(Functions.filesMatching(isFileNamed("Oracles m = 5, n = 9, 100.json")),
				Predicates.singletonAndMatch(contentMatches(Marks.extendAll("0.3298073577203555")))).test(p);
	}

	private MarksTree toFormattingTree(GitPathRoot path) throws IOException {
		final Optional<Path> f = Files.find(path, Integer.MAX_VALUE, (p, a) -> Files.isRegularFile(p))
				.filter(p -> p.getFileName().toString().equals("PSRWeights.java")).collect(MoreCollectors.toOptional());
		final Pattern origSpace = Pattern.compile("     checkArgument");
		final Pattern tooManyForGoogle = Pattern.compile("          ");
		final Pattern tooManyTabsForGoogle = Pattern.compile("\\t");
		final String content = TOptional.wrapping(f).map(Files::readString).orElse("");
		final boolean changedAlignment = !origSpace.matcher(content).find();
		final boolean googleStyle = !tooManyForGoogle.matcher(content).find()
				&& !tooManyTabsForGoogle.matcher(content).find();
		final SubMarksTree fmtMark = SubMarksTree.given(C_FORMATTED, Mark.binary(changedAlignment));
		final SubMarksTree styleMark = SubMarksTree.given(C_STYLE, Mark.binary(googleStyle));
		final SubMarksTree mainMark = SubMarksTree.given(C_MAIN,
				MarksTree.composite(ImmutableSet.of(fmtMark, styleMark)));
		final boolean limitedChanges = changesAbout(path, ImmutableSet.of("PSRWeights.java", "RegretComputer.java"));
		final SubMarksTree singleChangeMark = SubMarksTree.given(C_LIMITED_CHANGES,
				Mark.binary(changedAlignment && limitedChanges));
		return MarksTree.composite(ImmutableSet.of(mainMark, singleChangeMark));
	}

}
