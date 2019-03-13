package io.github.oliviercailloux.grade.context;

import static java.util.Objects.requireNonNull;

import java.nio.file.Path;
import java.util.Map;

import com.google.common.collect.ImmutableMap;

class SimpleMultiContent implements MultiContent {
	private final ImmutableMap<Path, String> delegate;

	public SimpleMultiContent(Map<Path, String> delegate) {
		this.delegate = ImmutableMap.copyOf(requireNonNull(delegate));
	}

	@Override
	public ImmutableMap<Path, String> getContents() {
		return delegate;
	}
}