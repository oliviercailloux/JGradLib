package io.github.oliviercailloux.java_grade;

import static com.google.common.base.Preconditions.checkArgument;

import java.nio.file.FileSystems;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableList;

import io.github.oliviercailloux.git.FileContent;
import io.github.oliviercailloux.grade.GradingException;

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
}