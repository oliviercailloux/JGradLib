package io.github.oliviercailloux.st_projects.services.grading;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.diffplug.common.base.Predicates;

import io.github.oliviercailloux.git.Client;
import io.github.oliviercailloux.st_projects.ex2.MavenManager;
import io.github.oliviercailloux.st_projects.model.ContentSupplier;
import io.github.oliviercailloux.st_projects.model.Criterion;
import io.github.oliviercailloux.st_projects.model.CriterionGrade;
import io.github.oliviercailloux.st_projects.model.GitContext;

public class Graders {
	public static CriterionGrader groupIdGrader(Criterion criterion, PomContexter context) {
		return () -> CriterionGrade.binary(criterion, context.isGroupIdValid());
	}

	public static CriterionGrader packageGroupIdGrader(Criterion criterion, GitContext context,
			PomContexter pomContext) {
		return () -> gradePackageGroupId(criterion, context, pomContext);
	}

	public static CriterionGrader mavenCompileGrader(Criterion criterion, GitContext context) {
		final MavenManager mavenManager = new MavenManager();
		return () -> CriterionGrade.binary(criterion,
				mavenManager.compile(context.getClient().getProjectDirectory().resolve("pom.xml")));
	}

	private static CriterionGrade gradePackageGroupId(Criterion criterion, GitContext context, PomContexter pomContext)
			throws GradingException {
		final Client client = context.getClient();
		{
			final List<String> groupIdElements = pomContext.getGroupIdElements();

			if (groupIdElements.isEmpty()) {
				return CriterionGrade.zero(criterion, "Unknown group id");
			}

			Path currentSegment = Paths.get("src/main/java");
			boolean allMatch = true;
			for (String element : groupIdElements) {
				LOGGER.debug("Checking for element {}.", element);
				final Path current = client.getProjectDirectory().resolve(currentSegment);
				final Path nextSegment = currentSegment.resolve(element);
				final Path next = client.getProjectDirectory().resolve(nextSegment);
				boolean onlyRightName;
				try {
					onlyRightName = Files.list(current).allMatch(Predicate.isEqual(next));
				} catch (IOException e) {
					throw new GradingException(e);
				}
				if (!onlyRightName) {
					allMatch = false;
					break;
				}
				currentSegment = nextSegment;
			}
			final boolean allMatchFinal = allMatch;
			return CriterionGrade.binary(criterion, allMatchFinal);
		}
	}

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(Graders.class);

	public static CriterionGrader predicateGraderWithComment(Criterion criterion,
			GitToMultipleSourcer multipleSourcesSupplier, Predicate<CharSequence> predicate) {
		return () -> gradeFromMultipleSources(criterion, multipleSourcesSupplier, predicate);
	}

	private static CriterionGrade gradeFromMultipleSources(Criterion criterion,
			GitToMultipleSourcer mutlipleSourcesSupplier, Predicate<CharSequence> predicate) {
		final Set<Path> sources = mutlipleSourcesSupplier.getSources();
		final boolean okay;
		final String comment;
		if (sources.size() == 1) {
			final String content = mutlipleSourcesSupplier.getContent();
//			LOGGER.info("Content: {}.", content);
			okay = predicate.test(content);
			comment = "";
		} else if (sources.isEmpty()) {
			okay = false;
			comment = "Source not found.";
		} else {
			okay = false;
			comment = "Found " + sources.size() + " sources: " + sources + ".";
		}
		LOGGER.debug("Sources: {}; okay: {}; comment: {}.", sources, okay, comment);
		return CriterionGrade.of(criterion, okay ? criterion.getMaxPoints() : 0d, comment);
	}

	public static CriterionGrader predicateGrader(Criterion criterion, ContentSupplier supplier,
			Predicate<? super String> conditionForPoints, double pointsSucceeds, double pointsFail) {
		requireNonNull(supplier);
		final Function<Boolean, CriterionGrade> f = fromBool(criterion, pointsSucceeds, pointsFail);
		return () -> f.apply(conditionForPoints.test(supplier.getContent()));
	}

	public static Function<Boolean, CriterionGrade> fromBool(Criterion criterion, double pointsSucceeds,
			double pointsFail) {
		requireNonNull(criterion);
		checkArgument(Double.isFinite(pointsSucceeds));
		checkArgument(Double.isFinite(pointsFail));
		checkArgument(pointsSucceeds >= pointsFail);

		final Function<Boolean, CriterionGrade> f = (b) -> CriterionGrade.of(criterion, b ? pointsSucceeds : pointsFail,
				"");
		return f;
	}

	public static Predicate<CharSequence> containsOnce(Pattern pattern) {
		return (s) -> {
			final Matcher matcher = pattern.matcher(s);
			final boolean found = matcher.find();
			final boolean foundAgain = matcher.find();
//			LOGGER.debug("Matching source '{}' with pattern {}: {} and {}.", s, pattern, found, foundAgain);
			return found && !foundAgain;
		};
	}

	public static <T> CriterionGrader predicateGrader(Criterion criterion, Supplier<? extends T> supplier,
			Predicate<? super T> conditionForPoints) {
		return () -> CriterionGrade.binary(criterion, conditionForPoints.test(supplier.get()));
	}

	public static CriterionGrader predicateGrader(Criterion criterion, ContentSupplier supplier,
			Predicate<? super String> conditionForPoints) {
		return () -> CriterionGrade.binary(criterion, conditionForPoints.test(supplier.getContent()));
	}

	public static CriterionGrader patternGrader(Criterion criterion, ContentSupplier contentSupplier, Pattern pattern) {
		final Predicate<CharSequence> contains = Predicates.contains(pattern);
		return predicateGrader(criterion, contentSupplier, contains);
		// return () -> CriterionGrade.binary(criterion,
		// pattern.matcher(contentSupplier.get()).find());
	}
}
