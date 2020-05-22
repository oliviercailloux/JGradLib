package io.github.oliviercailloux.grade.markers;

import static com.google.common.base.Preconditions.checkArgument;
import static io.github.oliviercailloux.exceptions.Unchecker.IO_UNCHECKER;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.Booleans;

import io.github.oliviercailloux.git.ComplexClient;
import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.CriterionGradeWeight;
import io.github.oliviercailloux.grade.IGrade;
import io.github.oliviercailloux.grade.Mark;
import io.github.oliviercailloux.grade.WeightingGrade;
import io.github.oliviercailloux.grade.context.FilesSource;
import io.github.oliviercailloux.grade.context.GitContext;
import io.github.oliviercailloux.grade.context.GitFullContext;
import io.github.oliviercailloux.grade.context.PomContext;
import io.github.oliviercailloux.grade.contexters.MavenManager;
import io.github.oliviercailloux.grade.contexters.PomSupplier;

/**
 *
 * NB to cope for combinatorics in possible combinations of marks, keep single
 * ones as simple as possible, and possibly when asking for (e.g.
 * POM_DEP_GUAVA), specify with a boolean if the comment "pom not found" should
 * be specified. Or, give it the grades so far, so that it will see whether the
 * comment has already been given.
 *
 * @author Olivier Cailloux
 *
 */
public class Marks {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(Marks.class);

	public static enum MarksCriterion implements Criterion {
		FILE_EXISTS, FILE_CONTENTS_MATCH_EXACTLY, FILE_CONTENTS_MATCH_APPROXIMATELY;

		@Override
		public String getName() {
			return toString();
		}
	}

	public static IGrade packageGroupIdGrade(FilesSource wholeSource, PomSupplier pomSupplier, PomContext pomContext) {
		final List<String> groupIdElements = pomContext.getGroupIdElements();
		if (groupIdElements.isEmpty()) {
			return Mark.zero("No group id.");
		}
		final ImmutableList<Path> pathsRelativeToMain = PackageGroupIdMarker.relativeTo(wholeSource,
				pomSupplier.getSrcMainJavaFolder());
		final Optional<Path> firstWronglyPrefixedInMain = pathsRelativeToMain.stream()
				.filter((p) -> !PackageGroupIdMarker.hasPrefix(p, groupIdElements)).findFirst();
		final ImmutableList<Path> pathsRelativeToTest = PackageGroupIdMarker.relativeTo(wholeSource,
				pomSupplier.getSrcTestJavaFolder());
		final Optional<Path> firstWronglyPrefixedInTest = pathsRelativeToTest.stream()
				.filter((p) -> !PackageGroupIdMarker.hasPrefix(p, groupIdElements)).findFirst();
		final boolean pass;
		final String comment;
		if (pathsRelativeToMain.isEmpty()) {
			pass = false;
			comment = "No source.";
		} else if (!firstWronglyPrefixedInMain.isEmpty()) {
			pass = false;
			comment = String.format("%s not prefixed by group id (%s) as expected.", firstWronglyPrefixedInMain.get(),
					groupIdElements);
		} else if (!firstWronglyPrefixedInTest.isEmpty()) {
			pass = false;
			comment = String.format("%s not prefixed by group id (%s) as expected.", firstWronglyPrefixedInTest.get(),
					groupIdElements);
		} else {
			pass = true;
			comment = "";
		}
		return Mark.given(pass ? 1d : 0d, comment);
	}

	public static IGrade noDerivedFilesGrade(FilesSource wholeSource) {
		if (wholeSource.asFileContents().isEmpty()) {
			return Mark.zero();
		}

		final List<String> comments = new ArrayList<>();
		final boolean noClasspath = wholeSource.filterOnPath(Predicates.equalTo(Paths.get(".classpath")))
				.asFileContents().isEmpty();
		final boolean noProject = wholeSource.filterOnPath(Predicates.equalTo(Paths.get(".project"))).asFileContents()
				.isEmpty();
		final boolean noSettings = wholeSource.filterOnPath((p) -> p.startsWith(".settings")).asFileContents()
				.isEmpty();
		final boolean noTarget = wholeSource.filterOnPath((p) -> p.startsWith("target")).asFileContents().isEmpty();
		if (!noClasspath) {
			comments.add("Found derived: .classpath.");
		}
		if (!noProject) {
			comments.add("Found derived: .project.");
		}
		if (!noSettings) {
			comments.add("Found derived: .settings/.");
		}
		if (!noTarget) {
			comments.add("Found derived: target/.");
		}
		return Mark.given(Booleans.countTrue(noClasspath, noProject, noSettings, noTarget) / 4d,
				comments.stream().collect(Collectors.joining(" ")));
	}

	public static IGrade noDerivedFilesGrade(Path projectRoot) {
		final ImmutableSet<Path> forbidden = ImmutableSet.of(projectRoot.resolve(".classpath"),
				projectRoot.resolve(".project"), projectRoot.resolve(".settings/"), projectRoot.resolve("target/"),
				projectRoot.resolve("bin/"), projectRoot.resolve(".DS_Store"));

		final boolean contains;
		try (Stream<Path> entries = IO_UNCHECKER.getUsing(() -> Files.list(projectRoot))) {
			contains = entries.findAny().isPresent();
		}

		final boolean noForbidden;
		try (Stream<Path> entries = IO_UNCHECKER.getUsing(() -> Files.list(projectRoot))) {
			noForbidden = entries.noneMatch(forbidden::contains);
		}

		return Mark.binary(contains && noForbidden);
	}

	/**
	 * The project must be checked out at the version to be tested, at the path
	 * indicated by the project directory of the client.
	 */
	public static IGrade mavenCompileGrade(GitContext context, PomSupplier pomSupplier) {
		final MavenManager mavenManager = new MavenManager();
		final Optional<Path> projectRelativeRootOpt = pomSupplier.getMavenRelativeRoot();
		return Mark.given(Booleans.countTrue(projectRelativeRootOpt.isPresent() && mavenManager.compile(
				context.getClient().getProjectDirectory().resolve(projectRelativeRootOpt.get().resolve("pom.xml")))),
				"");
	}

	public static IGrade travisConfGrade(String travisContent) {
		if (travisContent.isEmpty()) {
			return Mark.zero("Configuration missing or named incorrectly.");
		}

		final Predicate<CharSequence> lang = Predicates.contains(Pattern.compile("language: java"));
		final Predicate<CharSequence> dist = Predicates.contains(Pattern.compile("dist: xenial"));
		/**
		 * I still accept this as I suspect some of my examples in the course use it.
		 */
		final Predicate<CharSequence> distTrusty = Predicates.contains(Pattern.compile("dist: trusty"));
		final Predicate<CharSequence> script = Predicates.contains(Pattern.compile("script: "));
		final boolean hasLang = lang.test(travisContent);
		final boolean hasDistXenial = dist.test(travisContent);
		final boolean hasDistTrusty = distTrusty.test(travisContent);
		final boolean hasDist = hasDistXenial || hasDistTrusty;
		final boolean hasScript = script.test(travisContent);
		final boolean deducibleLang = hasLang || hasScript;
		final Mark langAndNoScript;
		if (hasLang && !hasScript) {
			langAndNoScript = Mark.one();
		} else if (hasLang && hasScript) {
			langAndNoScript = Mark.given(1d / 4d, "Redundant script.");
		} else if (!hasLang && hasScript) {
			langAndNoScript = Mark.given(1d / 4d, "Script instead of lang.");
		} else {
			assert !hasLang && !hasScript;
			langAndNoScript = Mark.zero("No language given, directly or indirectly.");
		}
		final Mark distAndDeducibleLanguage;
		if (!hasDist) {
			distAndDeducibleLanguage = Mark.zero("Missing ‘dist: xenial’.");
		} else if (!deducibleLang) {
			distAndDeducibleLanguage = Mark.zero("No deducible language");
		} else if (hasDistTrusty) {
			distAndDeducibleLanguage = Mark.one("Tolerating dist trusty, but dist xenial is to be preferred");
		} else {
			assert hasDist && deducibleLang && !hasDistTrusty && hasDistXenial;
			distAndDeducibleLanguage = Mark.one();
		}
		return WeightingGrade.from(ImmutableSet.of(
				CriterionGradeWeight.from(Criterion.given("Language indication"), langAndNoScript, 2d / 3d),
				CriterionGradeWeight.from(Criterion.given("Dist indication (and language indicated)"),
						distAndDeducibleLanguage, 1d / 3d)));
	}

	public static IGrade gitRepoGrade(GitFullContext context) {
		final ComplexClient client = context.getClient();

		final IGrade grade;
		if (!client.existsCached()) {
			grade = Mark.zero("Repository not found");
		} else if (!client.hasContentCached()) {
			grade = Mark.zero("Repository found but is empty");
		} else if (!context.getMainCommit().isPresent()) {
			grade = Mark.zero("Repository found with content but no suitable commit found");
		} else {
			grade = Mark.one();
		}

		return grade;
	}

	public static IGrade timeGrade(GitFullContext contextSupplier, Instant deadline,
			Function<Duration, Double> timeScorer) {
		final ComplexClient client = contextSupplier.getClient();

		if (!client.hasContentCached() || !contextSupplier.getMainCommit().isPresent()) {
			return Mark.one();
		}

		final Instant submitted = contextSupplier.getSubmittedTime();

		final Instant tooLate = deadline.plus(Duration.ofSeconds(1));
		final Duration tardiness = Duration.between(tooLate, submitted);

		LOGGER.debug("Last: {}, deadline: {}, tardiness: {}.", submitted, deadline, tardiness);
		final Mark grade;
		if (tardiness.compareTo(Duration.ZERO) > 0) {
			LOGGER.warn("Last event after deadline: {}.", submitted);
			final double penalty = timeScorer.apply(tardiness);
			checkArgument(0d <= penalty && penalty <= 1d);
			grade = Mark.given(penalty, "Last event after deadline: "
					+ ZonedDateTime.ofInstant(submitted, ZoneId.of("Europe/Paris")) + ", " + tardiness + " late.");
		} else {
			grade = Mark.one();
		}
		return grade;
	}

	public static IGrade timeGrade(Instant submitted, Instant deadline, Function<Duration, Double> timeScorer) {
		final Duration tardiness = Duration.between(deadline, submitted);

		LOGGER.debug("Last: {}, deadline: {}, tardiness: {}.", submitted, deadline, tardiness);
		final Mark grade;
		if (tardiness.compareTo(Duration.ZERO) > 0) {
			LOGGER.warn("Last event after deadline: {}.", submitted);
			final double penalty = timeScorer.apply(tardiness);
			checkArgument(0d <= penalty && penalty <= 1d);
			grade = Mark.given(penalty, "Last event after deadline: "
					+ ZonedDateTime.ofInstant(submitted, ZoneId.of("Europe/Paris")) + ", " + tardiness + " late.");
		} else {
			grade = Mark.one();
		}
		return grade;
	}

	public static IGrade packageGroupId(FilesSource wholeSource, PomSupplier pomSupplier, PomContext pomContext) {
		final List<String> groupIdElements = pomContext.getGroupIdElements();
		if (groupIdElements.isEmpty()) {
			return Mark.zero("No group id.");
		}
		final ImmutableList<Path> pathsRelativeToMain = PackageGroupIdMarker.relativeTo(wholeSource,
				pomSupplier.getSrcMainJavaFolder());
		final Optional<Path> firstWronglyPrefixedInMain = pathsRelativeToMain.stream()
				.filter((p) -> !PackageGroupIdMarker.hasPrefix(p, groupIdElements)).findFirst();
		final ImmutableList<Path> pathsRelativeToTest = PackageGroupIdMarker.relativeTo(wholeSource,
				pomSupplier.getSrcTestJavaFolder());
		final Optional<Path> firstWronglyPrefixedInTest = pathsRelativeToTest.stream()
				.filter((p) -> !PackageGroupIdMarker.hasPrefix(p, groupIdElements)).findFirst();
		final boolean pass;
		final String comment;
		if (pathsRelativeToMain.isEmpty()) {
			pass = false;
			comment = "No source.";
		} else if (!firstWronglyPrefixedInMain.isEmpty()) {
			pass = false;
			comment = String.format("%s not prefixed by group id (%s) as expected.", firstWronglyPrefixedInMain.get(),
					groupIdElements);
		} else if (!firstWronglyPrefixedInTest.isEmpty()) {
			pass = false;
			comment = String.format("%s not prefixed by group id (%s) as expected.", firstWronglyPrefixedInTest.get(),
					groupIdElements);
		} else {
			pass = true;
			comment = "";
		}
		return Mark.given(Booleans.countTrue(pass), comment);
	}

	public static IGrade fileMatchesGrade(Path file, String exactTarget, Pattern approximateTarget) {
		final boolean exists = Files.exists(file);
		final String content = exists ? IO_UNCHECKER.getUsing(() -> Files.readString(file)) : "";
		final boolean matchesExactly = exists && content.stripTrailing().equals(exactTarget);
		final boolean matchesApproximately = exists && (matchesExactly || approximateTarget.matcher(content).matches());
		final Mark matchesExactlyMark = exists
				? Mark.binary(matchesExactly, "", String.format("Expected \"%s\", found \"%s\".", exactTarget, content))
				: Mark.zero();
		return WeightingGrade.from(ImmutableList.of(
				CriterionGradeWeight.from(MarksCriterion.FILE_EXISTS, Mark.binary(exists), 0.5d),
				CriterionGradeWeight.from(MarksCriterion.FILE_CONTENTS_MATCH_APPROXIMATELY,
						Mark.binary(matchesApproximately), 0.4d),
				CriterionGradeWeight.from(MarksCriterion.FILE_CONTENTS_MATCH_EXACTLY, matchesExactlyMark, 0.1d)));
	}

	public static Pattern extend(String basis) {
		return Pattern.compile("[\\h\\v]*\"?" + basis + "\"?[\\h\\v]*", Pattern.CASE_INSENSITIVE);
	}

}
