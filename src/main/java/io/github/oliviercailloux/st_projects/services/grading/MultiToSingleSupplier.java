package io.github.oliviercailloux.st_projects.services.grading;

import static java.util.Objects.requireNonNull;

import java.nio.file.Path;

import com.google.common.collect.ImmutableMap;

import io.github.oliviercailloux.st_projects.model.ContentSupplier;
import io.github.oliviercailloux.st_projects.model.GradingContexter;
import io.github.oliviercailloux.st_projects.model.MultiContentSupplier;

/**
 * Has no internal state, thus, does not implement {@link GradingContexter}.
 *
 * @author Olivier Cailloux
 *
 */
public class MultiToSingleSupplier implements ContentSupplier {
	private final MultiContentSupplier supplier;

	public MultiToSingleSupplier(MultiContentSupplier supplier) {
		this.supplier = requireNonNull(supplier);
	}

	@Override
	public String getContent() {
		final ImmutableMap<Path, String> contents = supplier.getContents();
		return contents.size() == 1 ? contents.values().iterator().next() : "";
	}

}
