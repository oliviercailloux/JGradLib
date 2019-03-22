package io.github.oliviercailloux.git;

import java.nio.file.Path;
import java.util.function.Supplier;

/**
 * This class does not cache, the supplier supposedly does if deemed useful.
 *
 * @author Olivier Cailloux
 *
 */
public class FileContentImpl implements FileContent {
	private final Supplier<String> contentSupplier;
	private final Path path;

	public FileContentImpl(Path path, Supplier<String> contentSupplier) {
		this.contentSupplier = contentSupplier;
		this.path = path;
	}

	@Override
	public Path getPath() {
		return path;
	}

	@Override
	public String getContent() {
		return contentSupplier.get();
	}
}