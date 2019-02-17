package io.github.oliviercailloux.grade.markers;

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
import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.GradingException;
import io.github.oliviercailloux.grade.Mark;
import io.github.oliviercailloux.grade.context.ContentSupplier;
import io.github.oliviercailloux.grade.context.GitContext;
import io.github.oliviercailloux.grade.context.MultiContent;
import io.github.oliviercailloux.grade.context.PomContext;
import io.github.oliviercailloux.grade.contexters.GitToMultipleSourcer;
import io.github.oliviercailloux.grade.contexters.GitToTestSourcer;
import io.github.oliviercailloux.grade.contexters.MavenManager;
import io.github.oliviercailloux.grade.contexters.PomSupplier;

public class Markers {
	public static CriterionMarker groupIdMarker(Criterion criterion, PomContext context) {
		return new GroupIdMarker(criterion, context);
	}

	public static CriterionMarker packageGroupIdMarker(Criterion criterion, GitContext context, PomSupplier pomSupplier,
			PomContext pomContext) {
		return new PackageGroupIdMarker(criterion, context, pomSupplier, pomContext);
	}

	public static CriterionMarker gradeOnlyOrig(Criterion criterion, GitContext context) {
		return () -> markOrig(criterion, context);
	}

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(Markers.class);

	public static CriterionMarker predicateMarkerWithComment(Criterion criterion,
			GitToMultipleSourcer multipleSourcesSupplier, Predicate<CharSequence> predicate) {
		return () -> markFromMultipleSources(criterion, multipleSourcesSupplier, predicate);
	}

	private static Mark markFromMultipleSources(Criterion criterion, MultiContent mutlipleSourcesSupplier,
			Predicate<CharSequence> predicate) {
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
		return Mark.of(criterion, okay ? criterion.getMaxPoints() : 0d, comment);
	}

	public static Function<Boolean, Mark> fromBool(Criterion criterion, double pointsSucceeds, double pointsFail) {
		requireNonNull(criterion);
		checkArgument(Double.isFinite(pointsSucceeds));
		checkArgument(Double.isFinite(pointsFail));
		checkArgument(pointsSucceeds >= pointsFail);

		final Function<Boolean, Mark> f = (b) -> Mark.of(criterion, b ? pointsSucceeds : pointsFail, "");
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

	public static <T> CriterionMarker predicateMarker(Criterion criterion, Supplier<? extends T> supplier,
			Predicate<? super T> conditionForPoints) {
		return new MarkerUsingSupplierAndPredicate<>(criterion, supplier, conditionForPoints);
	}

	public static CriterionMarker predicateMarkerAny(Criterion criterion, MultiContent supplier,
			Predicate<? super String> conditionForPoints) {
		final Predicate<? super Map<Path, String>> p = (m) -> m.values().stream().anyMatch(conditionForPoints);
		return new MarkerUsingSupplierAndPredicate<>(criterion, () -> supplier.getContents(), p);
	}

	public static CriterionMarker patternMarker(Criterion criterion, ContentSupplier contentSupplier, Pattern pattern) {
		final Predicate<CharSequence> contains = Predicates.contains(pattern);
		return predicateMarker(criterion, contentSupplier, contains);
		// return () -> CriterionGrade.binary(criterion,
		// pattern.matcher(contentSupplier.get()).find());
	}

	private static Mark markOrig(Criterion criterion, GitContext context) {
		final Optional<RevCommit> mainCommitOpt = context.getMainCommit();
		if (!mainCommitOpt.isPresent()) {
			return Mark.min(criterion);
		}

		final Client client = context.getClient();
		final RevCommit mainCommit = mainCommitOpt.get();
		try {
			final Optional<AnyObjectId> classpathId = client.getBlobId(mainCommit, Paths.get(".classpath"));
			final Optional<AnyObjectId> settingsId = client.getBlobId(mainCommit, Paths.get(".settings/"));
			final Optional<AnyObjectId> projectId = client.getBlobId(mainCommit, Paths.get(".project"));
			final Optional<AnyObjectId> targetId = client.getBlobId(mainCommit, Paths.get("target/"));
			LOGGER.debug("Found settings? {}.", settingsId);
			final double weightOk = ImmutableList.of(classpathId, settingsId, projectId, targetId).stream()
					.filter((o) -> !o.isPresent()).count() / 4d;
			final double weightKo = 1d - weightOk;
			return Mark.of(criterion, criterion.getMinPoints() * weightKo + criterion.getMaxPoints() * weightOk, "");
		} catch (IOException e) {
			throw new GradingException(e);
		}
	}

	public static CriterionMarker predicateMarker(Criterion criterion, ContentSupplier supplier,
			Predicate<? super String> conditionForPoints) {
		return new MarkerUsingSupplierAndPredicate<>(criterion, () -> supplier.getContent(), conditionForPoints);
	}

	public static CriterionMarker notEmpty(Criterion criterion, MultiContent multiSupplier) {
		return () -> !multiSupplier.getContents().isEmpty()
				? Mark.of(criterion, criterion.getMaxPoints(), "Found: " + multiSupplier.getContents().keySet() + ".")
				: Mark.min(criterion);
	}

	public static CriterionMarker notEmpty(Criterion criterion, ContentSupplier contentSupplier) {
		return () -> Mark.binary(criterion, !contentSupplier.getContent().isEmpty());
	}

	public static CriterionMarker mavenTestMarker(Criterion criterion, GitContext context, GitToTestSourcer testSourcer,
			PomSupplier pomSupplier) {
		final MavenManager mavenManager = new MavenManager();
		return () -> Mark.binary(criterion,
				testSourcer.getContents().keySet().stream().anyMatch(testSourcer::isSurefireTestFile)
						&& pomSupplier.getProjectRelativeRoot().isPresent()
						&& mavenManager.test(context.getClient().getProjectDirectory()
								.resolve(pomSupplier.getProjectRelativeRoot().get().resolve("pom.xml"))));
	}

	public static Predicate<Path> startsWithPredicate(PomSupplier pomSupplier, Path start) {
		final Predicate<Path> p1 = (p) -> pomSupplier.getProjectRelativeRoot().isPresent();
		return p1.and((p) -> p.startsWith(pomSupplier.getProjectRelativeRoot().get().resolve(start)));
	}

	public static CriterionMarker mavenCompileMarker(Criterion criterion, GitContext context, PomSupplier pomSupplier) {
		final MavenManager mavenManager = new MavenManager();
		return () -> {
			final Optional<Path> projectRelativeRootOpt = pomSupplier.getProjectRelativeRoot();
			return Mark.binary(criterion, projectRelativeRootOpt.isPresent() && mavenManager.compile(context.getClient()
					.getProjectDirectory().resolve(projectRelativeRootOpt.get().resolve("pom.xml"))));
		};
	}

	public static CriterionMarker predicateMarker(Criterion criterion, MultiContent supplier,
			Predicate<? super String> conditionForPoints) {
		final Predicate<? super Map<Path, String>> p = (m) -> m.values().stream().allMatch(conditionForPoints);
		return new MarkerUsingSupplierAndPredicate<>(criterion, () -> supplier.getContents(), p);
	}
}
