package io.github.oliviercailloux.grade.markers;

import static com.google.common.base.Preconditions.checkArgument;

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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Booleans;

import io.github.oliviercailloux.git.Client;
import io.github.oliviercailloux.grade.IGrade;
import io.github.oliviercailloux.grade.Mark;
import io.github.oliviercailloux.grade.context.FilesSource;
import io.github.oliviercailloux.grade.context.GitContext;
import io.github.oliviercailloux.grade.context.GitFullContext;
import io.github.oliviercailloux.grade.context.PomContext;
import io.github.oliviercailloux.grade.contexters.MavenManager;
import io.github.oliviercailloux.grade.contexters.PomSupplier;

public class Marks {
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

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(Marks.class);

	/**
	 * The project must be checked out at the version to be tested, at the path
	 * indicated by the project directory of the client.
	 */
	public static IGrade mavenCompileGrade(GitContext context, PomSupplier pomSupplier) {
		final MavenManager mavenManager = new MavenManager();
		final Optional<Path> projectRelativeRootOpt = pomSupplier.getMavenRelativeRoot();
		return Mark.ifPasses(projectRelativeRootOpt.isPresent() && mavenManager.compile(
				context.getClient().getProjectDirectory().resolve(projectRelativeRootOpt.get().resolve("pom.xml"))));
	}

	public static IGrade travisConfGrade(String travisContent) {
		/** TODO refine using multiple sub-grades. */
		if (travisContent.isEmpty()) {
			return Mark.zero("Configuration not found or incorrectly named.");
		}

		final Predicate<CharSequence> lang = Predicates.contains(Pattern.compile("language: java"));
		final Predicate<CharSequence> dist = Predicates.contains(Pattern.compile("dist: xenial"));
		/**
		 * I still accept this as I suspect some of my examples in the course use it.
		 */
		final Predicate<CharSequence> distTrusty = Predicates.contains(Pattern.compile("dist: trusty"));
		final Predicate<CharSequence> script = Predicates.contains(Pattern.compile("script: "));
		final boolean hasLang = lang.test(travisContent);
		final boolean hasDist = dist.test(travisContent) || distTrusty.test(travisContent);
		final boolean hasScript = script.test(travisContent);
		final double points;
		final String comment;
		if (!hasLang && !hasScript) {
			points = 0d;
			comment = "Missing language.";
		} else if (!hasLang && hasScript && !hasDist) {
			points = 1d / 3d;
			comment = "Missing language (script should be defaulted).";
		} else if (!hasLang && hasScript && hasDist) {
			points = 0d;
			comment = "Missing language (script should be defaulted). Missing dist.";
		} else {
			assert hasLang;
			if (!hasDist && !hasScript) {
				points = 1d * 2d / 3d;
				comment = "Missing ‘dist: xenial’.";
			} else if (!hasDist && hasScript) {
				points = 1d / 3d;
				comment = "Missing ‘dist: xenial’. Inappropriate script, why not default?";
			} else if (hasDist && !hasScript) {
				points = 1d;
				comment = "";
			} else {
				assert hasDist && hasScript;
				points = 1d / 2d;
				comment = "Inappropriate script, why not default?";
			}
		}
		return Mark.given(points, comment);
	}

	public static IGrade gitRepoGrade(GitFullContext context) {
		final Client client = context.getClient();

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
		return mark(contextSupplier, deadline, timeScorer);
	}

	private static IGrade mark(GitFullContext context, Instant deadline, Function<Duration, Double> timeScorer) {
		final Client client = context.getClient();

		if (!client.hasContentCached() || !context.getMainCommit().isPresent()) {
			return Mark.one();
		}

		final Instant submitted = context.getSubmittedTime();

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

}
