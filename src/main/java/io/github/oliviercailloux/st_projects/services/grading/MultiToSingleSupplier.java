package io.github.oliviercailloux.st_projects.services.grading;

import static java.util.Objects.requireNonNull;

import java.nio.file.Path;
import java.util.function.Supplier;

import com.google.common.collect.ImmutableMap;

import io.github.oliviercailloux.st_projects.model.GradingContexter;
import io.github.oliviercailloux.st_projects.model.MultiContent;

/**
 * Has no internal state, thus, does not implement {@link GradingContexter}.
 *
 * @author Olivier Cailloux
 *
 */
public class MultiToSingleSupplier implements Supplier<String> {
	private final Supplier<MultiContent> supplier;

	public MultiToSingleSupplier(Supplier<MultiContent> supplier) {
		this.supplier = requireNonNull(supplier);
	}

	@Override
	public String get() {
		final ImmutableMap<Path, String> contents = supplier.get().getContents();
		return contents.size() == 1 ? contents.values().iterator().next() : "";
	}

}
