package io.github.oliviercailloux.st_projects.services.grading;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.diffplug.common.base.Predicates;
import com.google.common.collect.ImmutableList;

import io.github.oliviercailloux.git.Client;
import io.github.oliviercailloux.st_projects.ex2.MavenManager;
import io.github.oliviercailloux.st_projects.model.ContentSupplier;
import io.github.oliviercailloux.st_projects.model.Criterion;
import io.github.oliviercailloux.st_projects.model.CriterionGrade;
import io.github.oliviercailloux.st_projects.model.GitContext;
import io.github.oliviercailloux.st_projects.model.MultiContentSupplier;
import io.github.oliviercailloux.st_projects.model.PomContext;
import io.github.oliviercailloux.st_projects.utils.GradingUtils;

public class Graders {
	public static CriterionGrader groupIdGrader(Criterion criterion, PomContext context) {
		return new GroupIdGrader(criterion, context);
	}

	public static CriterionGrader packageGroupIdGrader(Criterion criterion, GitContext context, PomSupplier pomSupplier,
			PomContext pomContext) {
		return new PackageGroupIdGrader(criterion, context, pomSupplier, pomContext);
	}

	public static CriterionGrader gradeOnlyOrig(Criterion criterion, GitContext context) {
		return () -> gradeOrig(criterion, context);
	}

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(Graders.class);

	public static CriterionGrader predicateGraderWithComment(Criterion criterion,
			GitToMultipleSourcer multipleSourcesSupplier, Predicate<CharSequence> predicate) {
		return () -> gradeFromMultipleSources(criterion, multipleSourcesSupplier, predicate);
	}

	private static CriterionGrade gradeFromMultipleSources(Criterion criterion,
			MultiContentSupplier mutlipleSourcesSupplier, Predicate<CharSequence> predicate) {
		final Set<Path> sources = mutlipleSourcesSupplier.getContents().keySet();
		final boolean okay;
		final String comment;
		if (sources.size() == 1) {
			final String content = mutlipleSourcesSupplier.getContents().values().iterator().next();
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
		return new GraderUsingSupplierAndPredicate<>(criterion, supplier, conditionForPoints);
	}

	public static CriterionGrader predicateGraderAny(Criterion criterion, MultiContentSupplier supplier,
			Predicate<? super String> conditionForPoints) {
		final Predicate<? super Map<Path, String>> p = (m) -> m.values().stream().anyMatch(conditionForPoints);
		return new GraderUsingSupplierAndPredicate<>(criterion, () -> supplier.getContents(), p);
	}

	public static CriterionGrader patternGrader(Criterion criterion, ContentSupplier contentSupplier, Pattern pattern) {
		final Predicate<CharSequence> contains = Predicates.contains(pattern);
		return predicateGrader(criterion, contentSupplier, contains);
		// return () -> CriterionGrade.binary(criterion,
		// pattern.matcher(contentSupplier.get()).find());
	}

	private static CriterionGrade gradeOrig(Criterion criterion, GitContext context) {
		final Optional<RevCommit> mainCommitOpt = context.getMainCommit();
		if (!mainCommitOpt.isPresent()) {
			return CriterionGrade.min(criterion);
		}

		final Client client = context.getClient();
		final RevCommit mainCommit = mainCommitOpt.get();
		try {
			Optional<AnyObjectId> classpathId;
			classpathId = client.getBlobId(mainCommit, Paths.get(".classpath"));
			final Optional<AnyObjectId> settingsId = client.getBlobId(mainCommit, Paths.get(".settings/"));
			final Optional<AnyObjectId> projectId = client.getBlobId(mainCommit, Paths.get(".project"));
			final Optional<AnyObjectId> targetId = client.getBlobId(mainCommit, Paths.get("target/"));
			LOGGER.debug("Found settings? {}.", settingsId);
			final ImmutableList<Boolean> succeeded = ImmutableList.of(classpathId, settingsId, projectId, targetId)
					.stream().map((o) -> !o.isPresent()).collect(ImmutableList.toImmutableList());
			final CriterionGrade grade = GradingUtils.getGradeFromSuccesses(criterion, succeeded);
			return grade;
		} catch (IOException e) {
			throw new GradingException(e);
		}
	}

	public static CriterionGrader predicateGrader(Criterion criterion, ContentSupplier supplier,
			Predicate<? super String> conditionForPoints) {
		return new GraderUsingSupplierAndPredicate<>(criterion, () -> supplier.getContent(), conditionForPoints);
	}

	public static CriterionGrader notEmpty(Criterion criterion, MultiContentSupplier multiSupplier) {
		return () -> !multiSupplier.getContents().isEmpty() ? CriterionGrade.of(criterion, criterion.getMaxPoints(),
				"Found: " + multiSupplier.getContents().keySet() + ".") : CriterionGrade.min(criterion);
	}

	public static CriterionGrader notEmpty(Criterion criterion, ContentSupplier contentSupplier) {
		return () -> CriterionGrade.binary(criterion, !contentSupplier.getContent().isEmpty());
	}

	public static CriterionGrader mavenTestGrader(Criterion criterion, ContextInitializer contextInitializer,
			GitToTestSourcer testSourcer, PomSupplier pomSupplier) {
		final MavenManager mavenManager = new MavenManager();
		return () -> CriterionGrade.binary(criterion,
				testSourcer.getContents().keySet().stream().anyMatch(testSourcer::isSurefireTestFile)
						&& pomSupplier.getProjectRelativeRoot().isPresent()
						&& mavenManager.test(contextInitializer.getClient().getProjectDirectory()
								.resolve(pomSupplier.getProjectRelativeRoot().get().resolve("pom.xml"))));
	}

	public static Predicate<Path> startsWithPredicate(PomSupplier pomSupplier, Path start) {
		final Predicate<Path> p1 = (p) -> pomSupplier.getProjectRelativeRoot().isPresent();
		return p1.and((p) -> p.startsWith(pomSupplier.getProjectRelativeRoot().get().resolve(start)));
	}

	public static CriterionGrader mavenCompileGrader(Criterion criterion, ContextInitializer contextInitializer,
			PomSupplier pomSupplier) {
		final MavenManager mavenManager = new MavenManager();
		return () -> {
			final Optional<Path> projectRelativeRootOpt = pomSupplier.getProjectRelativeRoot();
			return CriterionGrade.binary(criterion,
					projectRelativeRootOpt.isPresent() && mavenManager.compile(contextInitializer.getClient()
							.getProjectDirectory().resolve(projectRelativeRootOpt.get().resolve("pom.xml"))));
		};
	}

	public static CriterionGrader predicateGrader(Criterion criterion, MultiContentSupplier supplier,
			Predicate<? super String> conditionForPoints) {
		final Predicate<? super Map<Path, String>> p = (m) -> m.values().stream().allMatch(conditionForPoints);
		return new GraderUsingSupplierAndPredicate<>(criterion, () -> supplier.getContents(), p);
	}
}
