package io.github.oliviercailloux.grade.contexters;

import static java.util.Objects.requireNonNull;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import io.github.oliviercailloux.git.FileContent;
import io.github.oliviercailloux.git.FileContentImpl;
import io.github.oliviercailloux.grade.context.FilesSource;

/**
 * This object does not cache. The source supposedly does if deemed useful.
 *
 * @author Olivier Cailloux
 *
 */
public class FilesSourceImpl implements FilesSource {
	private final Function<Path, String> contentSupplier;
	private final ImmutableSet<Path> paths;

	public FilesSourceImpl(Set<Path> paths, Function<Path, String> contentSupplier) {
		this.contentSupplier = requireNonNull(contentSupplier);
		this.paths = ImmutableSet.copyOf(paths);
	}

	private ImmutableSet<Path> getPaths() {
		return paths;
	}

	@Override
	public boolean equals(Object o2) {
		if (!(o2 instanceof FilesSource)) {
			return false;
		}
		final FilesSource f2 = (FilesSource) o2;
		return getContents().equals(f2.getContents());
	}

	@Override
	public int hashCode() {
		return Objects.hash(getContents());
	}

	public ImmutableMap<Path, String> asMapToRename() {
		return getPaths().stream().collect(ImmutableMap.toImmutableMap((p) -> p, (p) -> contentSupplier.apply(p)));
	}

	@Override
	public ImmutableMap<Path, String> getContents() {
		return asMapToRename();
	}

	@Override
	public FilesSource filterOnContent(Predicate<? super String> predicate) {
		final ImmutableSet<Path> matching = getPaths().stream().filter((p) -> predicate.test(contentSupplier.apply(p)))
				.collect(ImmutableSet.toImmutableSet());
		return new FilesSourceImpl(matching, contentSupplier);
	}

	@Override
	public String getContent(Path path) {
		return contentSupplier.apply(path);
	}

	@Override
	public ImmutableSet<FileContent> asFileContents() {
		return getPaths().stream().map((p) -> new FileContentImpl(p, () -> contentSupplier.apply(p)))
				.collect(ImmutableSet.toImmutableSet());
	}

	@Override
	public boolean anyMatch(Predicate<? super String> predicate) {
		return asMapToRename().values().stream().anyMatch(predicate);
	}

	@Override
	public boolean existsAndAllMatch(Predicate<? super String> predicate) {
		return !asMapToRename().isEmpty() && asMapToRename().values().stream().allMatch(predicate);
	}

	@Override
	public boolean existsAndNoneMatch(Predicate<? super String> predicate) {
		return !asMapToRename().isEmpty() && asMapToRename().values().stream().allMatch(predicate.negate());
	}

	@Override
	public boolean noneMatch(Predicate<? super String> predicate) {
		return !asMapToRename().values().stream().anyMatch(predicate);
	}

	@Override
	public FilesSource filter(Predicate<FileContent> predicate) {
		final ImmutableSet<Path> matching = getPaths().stream()
				.filter((p) -> predicate.test(new FileContentImpl(p, () -> contentSupplier.apply(p))))
				.collect(ImmutableSet.toImmutableSet());
		return new FilesSourceImpl(matching, contentSupplier);
	}

	@Override
	public FilesSource filterOnPath(Predicate<Path> predicate) {
		final ImmutableSet<Path> matching = getPaths().stream().filter(predicate)
				.collect(ImmutableSet.toImmutableSet());
		return new FilesSourceImpl(matching, contentSupplier);
	}
}
