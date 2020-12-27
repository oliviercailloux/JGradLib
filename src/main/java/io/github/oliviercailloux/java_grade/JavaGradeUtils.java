package io.github.oliviercailloux.java_grade;

import static com.google.common.base.Preconditions.checkArgument;
import static io.github.oliviercailloux.jaris.exceptions.Unchecker.IO_UNCHECKER;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URLClassLoader;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableList;

import io.github.oliviercailloux.grade.GradingException;
import io.github.oliviercailloux.grade.IGrade;
import io.github.oliviercailloux.grade.Mark;
import io.github.oliviercailloux.java_grade.bytecode.Instanciator;
import io.github.oliviercailloux.java_grade.bytecode.RestrictingClassLoader;

public class JavaGradeUtils {
	/**
	 * @return an empty path if the given source declares no package.
	 */
	public static Path getPackage(String code) {
		final Matcher matcher = Pattern.compile("^package (?<PKG>[^;]+);").matcher(code);
		final String pkg = matcher.find() ? matcher.group("PKG") : "";
		final ImmutableList<String> pkgElements = pkg.equals("") ? ImmutableList.of()
				: ImmutableList.copyOf(pkg.split("."));
		assert !pkgElements.contains("")
				: String.format("Pkg: %s, split: %s (%d).", pkg, pkgElements, pkgElements.size());
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
			content = IO_UNCHECKER.getUsing(() -> Files.readString(sourcePath, StandardCharsets.ISO_8859_1));
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

	public static IGrade gradeSecurely(Path classPathRoot, Function<Instanciator, IGrade> gradeFunction) {
		final IGrade implGrade;
		try (URLClassLoader loader = RestrictingClassLoader.noPermissions(classPathRoot.toUri().toURL(),
				gradeFunction.getClass().getClassLoader())) {
			final Instanciator instanciator = Instanciator.given(loader);
			implGrade = gradeFunction.apply(instanciator);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return implGrade;
	}

	public static <T> IGrade gradeSecurely(Path classPathRoot, Class<T> clazz,
			Function<Supplier<T>, IGrade> gradeFunction) {
		return gradeSecurely(classPathRoot, i -> fromInst(i, clazz, gradeFunction));
	}

	private static <T> IGrade fromInst(Instanciator instanciator, Class<T> clazz,
			Function<Supplier<T>, IGrade> gradeFunction) {
		final Optional<T> instanceOpt = instanciator.getInstance(clazz, "newInstance");
		final IGrade implGrade;
		if (instanceOpt.isPresent()) {
			final Supplier<T> factory = () -> instanciator.getInstance(clazz, "newInstance").get();
			implGrade = gradeFunction.apply(factory);
		} else {
			implGrade = Mark.zero("Could not initialize implementation: " + instanciator.getLastException());
		}
		return implGrade;
	}

}
