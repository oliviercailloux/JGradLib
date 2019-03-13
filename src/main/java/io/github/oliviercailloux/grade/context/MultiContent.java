package io.github.oliviercailloux.grade.context;

import java.nio.file.Path;
import java.util.Map;
import java.util.function.Predicate;

import com.google.common.collect.ImmutableMap;

public interface MultiContent {
	public static MultiContent empty() {
		return new SimpleMultiContent(ImmutableMap.of());
	}

	/**
	 * @return the paths are relative to the project directory.
	 */
	public ImmutableMap<Path, String> getContents();

	public default boolean noneMatch(Predicate<? super String> predicate) {
		return !getContents().values().stream().anyMatch(predicate);
	}

	public default boolean anyMatch(Predicate<? super String> predicate) {
		return getContents().values().stream().anyMatch(predicate);
	}

	public default boolean existsAndNoneMatch(Predicate<? super String> predicate) {
		return !getContents().isEmpty() && getContents().values().stream().allMatch(predicate.negate());
	}

	public default boolean existsAndAllMatch(Predicate<? super String> predicate) {
		return !getContents().isEmpty() && getContents().values().stream().allMatch(predicate);
	}

	public static MultiContent given(Map<Path, String> delegate) {
		return new SimpleMultiContent(delegate);
	}
}
