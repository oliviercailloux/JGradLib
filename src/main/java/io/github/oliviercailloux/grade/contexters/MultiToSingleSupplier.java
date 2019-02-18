package io.github.oliviercailloux.grade.contexters;

import static java.util.Objects.requireNonNull;

import java.nio.file.Path;

import com.google.common.collect.ImmutableMap;

import io.github.oliviercailloux.grade.context.MultiContent;

public class MultiToSingleSupplier {
	public static String getContent(MultiContent wrapped) {
		final ImmutableMap<Path, String> contents = wrapped.getContents();
		return contents.size() == 1 ? contents.values().iterator().next() : "";
	}

	private final MultiContent wrapped;

	public MultiToSingleSupplier(MultiContent wrapped) {
		this.wrapped = requireNonNull(wrapped);
	}

	public String getContent() {
		final ImmutableMap<Path, String> contents = wrapped.getContents();
		return contents.size() == 1 ? contents.values().iterator().next() : "";
	}

}
