package io.github.oliviercailloux.grade.contexters;

import static java.util.Objects.requireNonNull;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSet;

import io.github.oliviercailloux.grade.context.MultiContent;

public class PomSupplier {

	public static PomSupplier basedOn(MultiContent supplier) {
		return new PomSupplier(supplier);
	}

	private final String delegate;
	private MultiContent underlyingMultiSupplier;

	private PomSupplier(MultiContent supplier) {
		this.underlyingMultiSupplier = requireNonNull(supplier);
		this.delegate = MultiToSingleSupplier.getContent(supplier);
	}

	public String getContent() {
		LOGGER.debug("Found poms: {}.", underlyingMultiSupplier.getContents().keySet());
		return delegate;
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
