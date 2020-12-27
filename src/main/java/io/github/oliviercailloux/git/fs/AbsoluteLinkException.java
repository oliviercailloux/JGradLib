package io.github.oliviercailloux.git.fs;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;

/**
 * An absolute link target was found.
 */
@SuppressWarnings("serial")
public class AbsoluteLinkException extends IOException {

	private final GitPath linkPath;
	private final Path target;

	AbsoluteLinkException(GitPath linkPath, Path target) {
		super(String.format("Link path: %s has an absolute target: %s.", linkPath, target));
		this.linkPath = checkNotNull(linkPath);
		this.target = checkNotNull(target);
		checkArgument(target.isAbsolute());
		checkArgument(target.getFileSystem().equals(FileSystems.getDefault()));
	}

	public GitPath getLinkPath() {
		return linkPath;
	}

	public Path getTarget() {
		return target;
	}

}