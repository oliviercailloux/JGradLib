package io.github.oliviercailloux.st_projects.services.grading;

import static java.util.Objects.requireNonNull;

import java.nio.file.Path;

import com.google.common.collect.ImmutableMap;

import io.github.oliviercailloux.st_projects.model.ContentSupplier;
import io.github.oliviercailloux.st_projects.model.MultiContent;

public class MultiToSingleSupplier implements ContentSupplier {
	private final MultiContent wrapped;

	public MultiToSingleSupplier(MultiContent wrapped) {
		this.wrapped = requireNonNull(wrapped);
	}

	@Override
	public String getContent() {
		final ImmutableMap<Path, String> contents = wrapped.getContents();
		return contents.size() == 1 ? contents.values().iterator().next() : "";
	}

}
