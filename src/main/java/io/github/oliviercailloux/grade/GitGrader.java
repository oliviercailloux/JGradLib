package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkArgument;
import static io.github.oliviercailloux.jaris.exceptions.Unchecker.IO_UNCHECKER;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import io.github.oliviercailloux.git.fs.GitPath;
import io.github.oliviercailloux.git.fs.GitPathRoot;
import io.github.oliviercailloux.jaris.exceptions.Throwing;

public interface GitGrader {
	public static class Predicates {
		public static Throwing.Predicate<Path, IOException> isFileNamed(String fileName) {
			return p -> p.getFileName() != null && p.getFileName().toString().equals(fileName);
		}

		public static Throwing.Predicate<Path, IOException> contentMatches(Pattern pattern) {
			return p -> Files.exists(p) && pattern.matcher(Files.readString(p)).matches();
		}

		public static Throwing.Predicate<GitPathRoot, IOException> containsFileMatching(
				Throwing.Predicate<? super GitPath, IOException> predicate) {
			final Predicate<? super GitPath> wrappedPredicate = IO_UNCHECKER.wrapPredicate(predicate);
			return r -> {
				try (Stream<Path> found = Files.find(r, 100, (p, a) -> wrappedPredicate.test((GitPath) p))) {
					return found.anyMatch(p -> true);
				} catch (UncheckedIOException e) {
					throw e.getCause();
				}
			};
		}

		public static Throwing.Predicate<GitPathRoot, IOException> isBranch(String remoteBranch) {
			checkArgument(!remoteBranch.contains("/"));
			checkArgument(!remoteBranch.isEmpty());

			final Pattern patternBranch = Pattern.compile("refs/remotes/[^/]+/" + remoteBranch);
			return r -> patternBranch.matcher(r.getGitRef()).matches();
		}
	}

	public static class Functions {
		public static Throwing.Function<GitPathRoot, GitPath, IOException> resolve(String file) {
			return r -> r.resolve(file);
		}

		public static <FO extends PI, PI> Throwing.Predicate<GitPathRoot, IOException> compose(
				Throwing.Function<GitPathRoot, FO, IOException> f, Throwing.Predicate<PI, IOException> p) {
			return r -> p.test(f.apply(r));
		}
	}

	public IGrade grade(GitFileSystemHistory history, String username) throws IOException;
}
