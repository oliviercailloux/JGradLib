package io.github.oliviercailloux.st_projects.services.grading;

import static java.util.Objects.requireNonNull;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSet;

import io.github.oliviercailloux.st_projects.model.ContentSupplier;
import io.github.oliviercailloux.st_projects.model.MultiContent;

public class PomSupplier implements ContentSupplier {

	public static PomSupplier basedOn(MultiContent supplier) {
		return new PomSupplier(supplier);
	}

	private final ContentSupplier delegate;
	private MultiContent underlyingMultiSupplier;

	private PomSupplier(MultiContent supplier) {
		this.underlyingMultiSupplier = requireNonNull(supplier);
		this.delegate = new MultiToSingleSupplier(supplier);
	}

	@Override
	public String getContent() {
		LOGGER.debug("Found poms: {}.", underlyingMultiSupplier.getContents().keySet());
		return delegate.getContent();
	}

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(PomSupplier.class);

	public Optional<Path> getProjectRelativeRoot() {
		final ImmutableSet<Path> possiblePoms = underlyingMultiSupplier.getContents().keySet();
		if (possiblePoms.size() != 1) {
			return Optional.empty();
		}
		final Path pomPath = possiblePoms.iterator().next();
		assert pomPath.getNameCount() >= 1;
		if (pomPath.getNameCount() == 1) {
			return Optional.of(Paths.get(""));
		}
		return Optional.of(pomPath.getParent());
	}

	/**
	 * @return true iff only one pom has been found and it is at the project root,
	 *         equivalently, true iff {@link #getProjectRelativeRoot()} is the empty
	 *         path.
	 */
	public boolean isProjectAtRoot() {
		return getProjectRelativeRoot().filter((p) -> p.toString().equals("")).isPresent();
	}

}
