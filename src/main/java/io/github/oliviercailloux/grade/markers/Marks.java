package io.github.oliviercailloux.grade.markers;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
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
import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.Mark;
import io.github.oliviercailloux.grade.context.FilesSource;
import io.github.oliviercailloux.grade.context.GitContext;
import io.github.oliviercailloux.grade.context.GitFullContext;
import io.github.oliviercailloux.grade.context.PomContext;
import io.github.oliviercailloux.grade.contexters.MavenManager;
import io.github.oliviercailloux.grade.contexters.PomSupplier;

public class Marks {
	public static Mark packageGroupId(Criterion criterion, FilesSource wholeSource, PomSupplier pomSupplier,
			PomContext pomContext) {
		final List<String> groupIdElements = pomContext.getGroupIdElements();
		if (groupIdElements.isEmpty()) {
			return Mark.min(criterion, "No group id.");
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
		return Mark.of(criterion, pass ? criterion.getMaxPoints() : criterion.getMinPoints(), comment);
	}

	public static Mark noDerivedFiles(Criterion criterion, FilesSource wholeSource) {
		if (wholeSource.asFileContents().isEmpty()) {
			return Mark.min(criterion);
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
		return Mark.proportional(criterion, Booleans.countTrue(noClasspath, noProject, noSettings, noTarget), 4,
				comments.stream().collect(Collectors.joining(" ")));
	}

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(Marks.class);

	public static Mark notEmpty(Criterion criterion, FilesSource multiSupplier) {
		return !multiSupplier.getContents().isEmpty()
				? Mark.of(criterion, criterion.getMaxPoints(), "Found: " + multiSupplier.getContents().keySet() + ".")
				: Mark.min(criterion);
	}

	/**
	 * The project must be checked out at the version to be tested, at the path
	 * indicated by the project directory of the client.
	 */
	public static Mark mavenCompile(Criterion criterion, GitContext context, PomSupplier pomSupplier) {
		final MavenManager mavenManager = new MavenManager();
		final Optional<Path> projectRelativeRootOpt = pomSupplier.getMavenRelativeRoot();
		return Mark.binary(criterion, projectRelativeRootOpt.isPresent() && mavenManager.compile(
				context.getClient().getProjectDirectory().resolve(projectRelativeRootOpt.get().resolve("pom.xml"))));
	}

	public static Mark travisConfMark(Criterion criterion, String travisContent) {
		if (travisContent.isEmpty()) {
			return Mark.min(criterion, "Configuration not found or incorrectly named.");
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
		assert criterion.getMinPoints() == 0d;
		if (!hasLang && !hasScript) {
			points = criterion.getMinPoints();
			comment = "Missing language.";
		} else if (!hasLang && hasScript && !hasDist) {
			points = criterion.getMaxPoints() / 3d;
			comment = "Missing language (script should be defaulted).";
		} else if (!hasLang && hasScript && hasDist) {
			points = criterion.getMinPoints();
			comment = "Missing language (script should be defaulted). Missing dist.";
		} else {
			assert hasLang;
			if (!hasDist && !hasScript) {
				points = criterion.getMaxPoints() * 2d / 3d;
				comment = "Missing ‘dist: xenial’.";
			} else if (!hasDist && hasScript) {
				points = criterion.getMaxPoints() / 3d;
				comment = "Missing ‘dist: xenial’. Inappropriate script, why not default?";
			} else if (hasDist && !hasScript) {
				points = criterion.getMaxPoints();
				comment = "";
			} else {
				assert hasDist && hasScript;
				points = criterion.getMaxPoints() / 2d;
				comment = "Inappropriate script, why not default?";
			}
		}
		return Mark.of(criterion, points, comment);
	}

	public static Mark gitRepo(Criterion criterion, GitFullContext context) {
		final Client client = context.getClient();

		final Mark grade;
		if (!client.existsCached()) {
			grade = Mark.min(criterion, "Repository not found");
		} else if (!client.hasContentCached()) {
			grade = Mark.min(criterion, "Repository found but is empty");
		} else if (!context.getMainCommit().isPresent()) {
			grade = Mark.min(criterion, "Repository found with content but no suitable commit found");
		} else {
			grade = Mark.max(criterion);
		}

		return grade;
	}

	public static Mark timeMark(Criterion criterion, GitFullContext contextSupplier, Instant deadline,
			Function<Duration, Double> penalizer) {
		return new TimeMarker(criterion, contextSupplier, deadline, penalizer).mark();
	}

}
