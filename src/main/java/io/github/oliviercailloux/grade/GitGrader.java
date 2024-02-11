package io.github.oliviercailloux.grade;

import static com.google.common.base.Preconditions.checkArgument;
import static io.github.oliviercailloux.jaris.exceptions.Unchecker.IO_UNCHECKER;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.primitives.Ints;
import io.github.oliviercailloux.gitjfs.GitPath;
import io.github.oliviercailloux.gitjfs.GitPathRoot;
import io.github.oliviercailloux.gitjfs.GitPathRootRef;
import io.github.oliviercailloux.jaris.throwing.TFunction;
import io.github.oliviercailloux.jaris.throwing.TPredicate;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface GitGrader {
	public static class Predicates {
		public static TPredicate<Path, IOException> isFileNamed(String fileName) {
			return p -> p.getFileName() != null && p.getFileName().toString().equals(fileName);
		}

		public static TPredicate<Path, IOException> contentMatches(Pattern pattern) {
			return p -> Files.exists(p) && pattern.matcher(Files.readString(p)).matches();
		}

		public static TPredicate<GitPathRoot, IOException>
				containsFileMatching(TPredicate<? super GitPath, IOException> predicate) {
			final Predicate<? super GitPath> wrappedPredicate = IO_UNCHECKER.wrapPredicate(predicate);
			return r -> {
				try (
						Stream<Path> found = Files.find(r, 100, (p, a) -> wrappedPredicate.test((GitPath) p))) {
					return found.anyMatch(p -> true);
				} catch (UncheckedIOException e) {
					throw e.getCause();
				}
			};
		}

		public static TPredicate<GitPathRoot, IOException> isBranch(String remoteBranch) {
			checkArgument(!remoteBranch.contains("/"));
			checkArgument(!remoteBranch.isEmpty());

			final Pattern patternBranch = Pattern.compile("refs/remotes/[^/]+/" + remoteBranch);
			return r -> patternBranch.matcher(r.getGitRef()).matches();
		}

		public static TPredicate<GitPathRootRef, IOException> isRefBranch(String remoteBranch) {
			checkArgument(!remoteBranch.contains("/"));
			checkArgument(!remoteBranch.isEmpty());

			final Pattern patternBranch = Pattern.compile("refs/remotes/[^/]+/" + remoteBranch);
			return r -> patternBranch.matcher(r.getGitRef()).matches();
		}

		public static boolean isBranch(String gitRef, String remoteBranch) {
			checkArgument(!remoteBranch.contains("/"));
			checkArgument(!remoteBranch.isEmpty());

			final Pattern patternBranch = Pattern.compile("refs/remotes/[^/]+/" + remoteBranch);
			return patternBranch.matcher(gitRef).matches();
		}

		public static <PI, QI, FO extends QI> TPredicate<PI, IOException>
				compose(TFunction<PI, FO, IOException> f, TPredicate<QI, IOException> p) {
			return r -> p.test(f.apply(r));
		}

		public static <PI> TPredicate<ImmutableSet<PI>, IOException>
				anyMatch(TPredicate<? super PI, IOException> p) {
			return r -> {
				try {
					return r.stream().anyMatch(IO_UNCHECKER.wrapPredicate(p));
				} catch (UncheckedIOException e) {
					throw e.getCause();
				}
			};
		}

		public static <PI> TPredicate<ImmutableSet<PI>, IOException>
				allAndSomeMatch(TPredicate<? super PI, IOException> p) {
			return r -> {
				try {
					return !r.isEmpty() && r.stream().allMatch(IO_UNCHECKER.wrapPredicate(p));
				} catch (UncheckedIOException e) {
					throw e.getCause();
				}
			};
		}

		public static <PI> TPredicate<ImmutableSet<PI>, IOException>
				singletonAndMatch(TPredicate<? super PI, IOException> p) {
			return r -> r.size() == 1 && p.test(Iterables.getOnlyElement(r));
		}
	}

	public static class Functions {
		@SuppressWarnings("unused")
		private static final Logger LOGGER = LoggerFactory.getLogger(GitGrader.class);

		public static <FI extends GitPath> TFunction<FI, GitPath, IOException> resolve(String file) {
			return r -> r.resolve(file);
		}

		public static TFunction<GitPathRoot, Integer, IOException>
				countTrue(Set<TPredicate<GitPathRoot, IOException>> predicates) throws IOException {
			try {
				return r -> Ints.checkedCast(predicates.stream()
						.map(p -> IO_UNCHECKER.wrapPredicate(p).test(r)).filter(b -> b).count());
			} catch (UncheckedIOException e) {
				throw e.getCause();
			}
		}

		public static TFunction<GitPathRoot, ImmutableSet<GitPath>, IOException>
				filesMatching(TPredicate<? super GitPath, IOException> predicate) {
			final Predicate<? super GitPath> wrappedPredicate = IO_UNCHECKER.wrapPredicate(predicate);
			return r -> {
				try (
						Stream<Path> found = Files.find(r, 100, (p, a) -> wrappedPredicate.test((GitPath) p))) {
					final ImmutableSet<GitPath> foundSet =
							found.map(p -> (GitPath) p).collect(ImmutableSet.toImmutableSet());
					LOGGER.debug("Found: {}.", foundSet);
					return foundSet;
				} catch (UncheckedIOException e) {
					throw e.getCause();
				}
			};
		}
	}

	public IGrade grade(GitWork work) throws IOException;
}
