package io.github.oliviercailloux.java_grade.bytecode;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Verify.verify;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.google.common.collect.ImmutableSet;

import io.github.oliviercailloux.java_grade.JavaGradeUtils;
import io.github.oliviercailloux.utils.Utils;

public class SourceScanner {
	private static final Pattern PACKAGE_PATTERN = Pattern.compile("^\\h*package\\h+(?<packageName>[^\\h]*)\\h*;");

	public static ImmutableSet<SourceClass> scan(Path start) {
		checkNotNull(start);
		final ImmutableSet<Path> javaPaths;
		try (Stream<Path> found = Utils.getOrThrow(() -> Files.find(start, 30,
				(p, a) -> p.getFileName() == null ? false : p.getFileName().toString().endsWith(".java")))) {
			verify(found != null);
			assert found != null;
			javaPaths = found.collect(ImmutableSet.toImmutableSet());
		}
		return javaPaths.stream().map(SourceScanner::asSourceClass).collect(ImmutableSet.toImmutableSet());
	}

	public static SourceClass asSourceClass(Path sourcePath) {
		final String shortClassNameFromFileName = sourcePath.getFileName().toString().replace(".java", "");
		String content = JavaGradeUtils.read(sourcePath);
		final Matcher matcher = PACKAGE_PATTERN.matcher(content);
		final boolean found = matcher.find();
		final String packageName = found ? matcher.group("packageName") : "";
		checkArgument(!matcher.find());
		return new SourceClass(sourcePath, packageName, shortClassNameFromFileName);
	}

	public static class SourceClass {
		private Path path;
		private String packageName;
		private String shortClassName;

		SourceClass(Path path, String packageName, String shortClassName) {
			this.path = checkNotNull(path);
			this.packageName = checkNotNull(packageName);
			this.shortClassName = checkNotNull(shortClassName);
		}

		public Path getPath() {
			return path;
		}

		public String getPackageName() {
			return packageName;
		}

		public String getShortClassName() {
			return shortClassName;
		}

		public String getFullClassName() {
			final String separator = packageName.equals("") ? "" : ".";
			return packageName + separator + shortClassName;
		}
	}
}
