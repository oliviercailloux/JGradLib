package io.github.oliviercailloux.grade.markers;

import static com.google.common.base.Preconditions.checkArgument;
import static io.github.oliviercailloux.jaris.exceptions.Unchecker.IO_UNCHECKER;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.CriterionGradeWeight;
import io.github.oliviercailloux.grade.IGrade;
import io.github.oliviercailloux.grade.Mark;
import io.github.oliviercailloux.grade.WeightingGrade;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

	public static enum MarksCriterion {
		FILE_EXISTS, FILE_CONTENTS_MATCH_EXACTLY, FILE_CONTENTS_MATCH_APPROXIMATELY;

		public Criterion asCriterion() {
			return Criterion.given(toString());
		}
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

	public static IGrade fileMatchesGrade(Path file, String exactTarget, Pattern approximateTarget) {
		final boolean exists = Files.exists(file);
		final String content = exists ? IO_UNCHECKER.getUsing(() -> Files.readString(file)) : "";
		final boolean matchesExactly = exists && content.stripTrailing().equals(exactTarget);
		final boolean matchesApproximately = exists && (matchesExactly || approximateTarget.matcher(content).matches());
		final Mark matchesExactlyMark = exists
				? Mark.binary(matchesExactly, "", String.format("Expected \"%s\", found \"%s\".", exactTarget, content))
				: Mark.zero();
		return WeightingGrade.from(ImmutableList.of(
				CriterionGradeWeight.from(MarksCriterion.FILE_EXISTS.asCriterion(), Mark.binary(exists), 0.5d),
				CriterionGradeWeight.from(MarksCriterion.FILE_CONTENTS_MATCH_APPROXIMATELY.asCriterion(),
						Mark.binary(matchesApproximately), 0.4d),
				CriterionGradeWeight.from(MarksCriterion.FILE_CONTENTS_MATCH_EXACTLY.asCriterion(), matchesExactlyMark,
						0.1d)));
	}

	public static Pattern extendWhite(String basis) {
		return Pattern.compile("[\\h\\v]*\"?(?<basis>" + basis + ")\"?[\\h\\v]*",
				Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS);
	}

	public static Pattern extendAll(String basis) {
		return Pattern.compile(".*(?<basis>" + basis + ").*",
				Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS | Pattern.DOTALL);
	}

}
