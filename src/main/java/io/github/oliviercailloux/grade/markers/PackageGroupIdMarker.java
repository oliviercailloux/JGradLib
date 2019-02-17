package io.github.oliviercailloux.grade.markers;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.oliviercailloux.git.Client;
import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.GradingException;
import io.github.oliviercailloux.grade.Mark;
import io.github.oliviercailloux.grade.context.GitContext;
import io.github.oliviercailloux.grade.context.PomContext;
import io.github.oliviercailloux.grade.contexters.PomSupplier;

public class PackageGroupIdMarker implements CriterionMarker {
	private Criterion criterion;
	private GitContext context;
	private PomContext pomContext;
	private PomSupplier pomSupplier;

	public PackageGroupIdMarker(Criterion criterion, GitContext context, PomSupplier pomSupplier,
			PomContext pomContext) {
		this.criterion = requireNonNull(criterion);
		this.context = requireNonNull(context);
		this.pomSupplier = requireNonNull(pomSupplier);
		this.pomContext = requireNonNull(pomContext);
	}

	@Override
	public Mark mark() throws GradingException {
		final List<String> groupIdElements = pomContext.getGroupIdElements();

		if (groupIdElements.isEmpty()) {
			return Mark.min(criterion, "Unknown group id");
		}
		final Optional<Path> relRootOpt = pomSupplier.getProjectRelativeRoot();
		if (!relRootOpt.isPresent()) {
			return Mark.min(criterion, "No unique pom found.");
		}

		final Path relativeRoot = relRootOpt.get();

		double weightOk = 0d;
		if (allMatch(relativeRoot.resolve(Paths.get("src/main/java")))) {
			weightOk += 0.5d;
		}
		if (allMatch(relativeRoot.resolve(Paths.get("src/test/java")))) {
			weightOk += 0.5d;
		}
		final double weightKo = 1d - weightOk;

		final double points = criterion.getMinPoints() * weightKo + criterion.getMaxPoints() * weightOk;
		final Mark grade = Mark.of(criterion, points, "");
		return grade;
	}

	private boolean allMatch(Path start) {
		final Client client = context.getClient();
		final List<String> groupIdElements = pomContext.getGroupIdElements();
		assert !groupIdElements.isEmpty();

		boolean allMatch = true;
		Path currentSegment = start;
		for (String element : groupIdElements) {
			LOGGER.debug("Checking for group id element {}.", element);
			final String packagePart = element.replaceAll("-", "_");
			final Path current = client.getProjectDirectory().resolve(currentSegment);
			final Path nextSegment = currentSegment.resolve(packagePart);
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
		return allMatch;
	}

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(PackageGroupIdMarker.class);
}
