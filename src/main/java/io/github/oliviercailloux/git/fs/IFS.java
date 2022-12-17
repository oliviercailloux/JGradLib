package io.github.oliviercailloux.git.fs;

import java.nio.file.spi.FileSystemProvider;

public interface IFS {
	public abstract FileSystemProvider provider();
}
