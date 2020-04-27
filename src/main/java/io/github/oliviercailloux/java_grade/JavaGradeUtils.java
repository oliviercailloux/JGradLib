package io.github.oliviercailloux.java_grade;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableList;

import io.github.oliviercailloux.git.FileContent;
import io.github.oliviercailloux.grade.GradingException;
import io.github.oliviercailloux.utils.Utils;
import io.vavr.control.Try;

public class JavaGradeUtils {
	/**
	 * @return an empty path if the given source declares no package.
	 */
	public static Path getPackage(FileContent source) {
		final String code = source.getContent();
		final Matcher matcher = Pattern.compile("^package (?<PKG>[^;]+);").matcher(code);
		final String pkg = matcher.find() ? matcher.group("PKG") : "";
		final ImmutableList<String> pkgElements = pkg.equals("") ? ImmutableList.of()
				: ImmutableList.copyOf(pkg.split("."));
		assert !pkgElements.contains("") : String.format("Pkg: %s, split: %s (%d).", pkg, pkgElements,
				pkgElements.size());
		try {
			final Path pkgPath = Path
					.of(pkgElements.stream().collect(Collectors.joining(FileSystems.getDefault().getSeparator())));
			final int pkgCount = pkgPath.getNameCount();
			final int nbElements = pkgElements.size();
			assert (pkgCount == 1 && nbElements == 0) || pkgCount == nbElements;
			return pkgPath;
		} catch (InvalidPathException e) {
			throw new GradingException(e);
		}
	}

	static public Path substract(Path longer, Path toSubstract) {
		checkArgument(!longer.isAbsolute());
		checkArgument(!toSubstract.isAbsolute());
		assert longer.getNameCount() >= 1;
		final Path substracted;
		if (toSubstract.equals(Path.of(""))) {
			substracted = longer;
		} else if (longer.endsWith(toSubstract)) {
			final int pathCount = longer.getNameCount();
			final int pkgCount = toSubstract.getNameCount();
			if (longer.equals(toSubstract)) {
				substracted = Path.of("");
			} else {
				assert pathCount > pkgCount;
				substracted = longer.subpath(0, pathCount - pkgCount);
			}
		} else {
			substracted = Path.of("");
		}
		return substracted;
	}

	public static String read(Path sourcePath) {
		String content;
		try {
			content = Files.readString(sourcePath);
		} catch (@SuppressWarnings("unused") MalformedInputException e) {
			content = Utils.getOrThrow(() -> Files.readString(sourcePath, StandardCharsets.ISO_8859_1));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return content;
	}

	@SafeVarargs
	public static <T> boolean doesThrow(Callable<T> callable, Predicate<Exception>... andSatisfies) {
		boolean satisfies;
		try {
			callable.call();
			satisfies = false;
		} catch (Exception exc) {
			satisfies = Arrays.stream(andSatisfies).allMatch(p -> p.test(exc));
		}
		return satisfies;
	}
}
