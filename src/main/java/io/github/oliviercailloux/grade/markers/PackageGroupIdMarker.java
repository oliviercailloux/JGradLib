package io.github.oliviercailloux.grade.markers;

import static java.util.Objects.requireNonNull;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.diffplug.common.base.MoreCollectors;

import io.github.oliviercailloux.git.FileContent;
import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.GradingException;
import io.github.oliviercailloux.grade.Mark;
import io.github.oliviercailloux.grade.context.FilesSource;
import io.github.oliviercailloux.grade.context.PomContext;
import io.github.oliviercailloux.grade.contexters.PomSupplier;

class PackageGroupIdMarker {
	private Criterion criterion;
	private FilesSource source;
	private PomContext pomContext;
	private PomSupplier pomSupplier;

	public PackageGroupIdMarker(Criterion criterion, FilesSource source, PomSupplier pomSupplier,
			PomContext pomContext) {
		this.criterion = requireNonNull(criterion);
		this.source = requireNonNull(source);
		this.pomSupplier = requireNonNull(pomSupplier);
		this.pomContext = requireNonNull(pomContext);
	}

	public Mark mark() throws GradingException {
		final List<String> groupIdElements = pomContext.getGroupIdElements();

		if (groupIdElements.isEmpty()) {
			return Mark.min(criterion, "Unknown group id");
		}
		final Optional<Path> relRootOpt = pomSupplier.getMavenRelativeRoot();
		if (!relRootOpt.isPresent()) {
			return Mark.min(criterion, "No unique pom found.");
		}

		final Path relativeRoot = relRootOpt.get();

		final boolean mainOk = allMatch(relativeRoot.resolve(Paths.get("src/main/java")));
		final boolean testOk = allMatch(relativeRoot.resolve(Paths.get("src/test/java")));
		return Mark.proportional(criterion, mainOk, testOk);
	}

	private boolean allMatch(Path start) {
		final List<String> groupIdElements = pomContext.getGroupIdElements();
		assert !groupIdElements.isEmpty();

		boolean allMatch = true;
		Path currentSegment = start;
		for (String element : groupIdElements) {
			LOGGER.debug("Checking for group id element {}.", element);
			final String packagePart = element.replaceAll("-", "_");
			final Path nextSegment = currentSegment.resolve(packagePart);
			boolean onlyRightName;
			final Path currentSegment2 = currentSegment;
			final Optional<FileContent> sub = source.filterOnPath((p) -> p.startsWith(currentSegment2)).asFileContents()
					.stream().collect(MoreCollectors.singleOrEmpty());
			onlyRightName = sub.isPresent() && sub.get().getPath().equals(nextSegment);
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
