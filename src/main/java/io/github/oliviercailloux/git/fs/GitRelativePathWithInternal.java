package io.github.oliviercailloux.git.fs;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Verify.verify;

import java.nio.file.Path;

import com.google.common.collect.Streams;

class GitRelativePathWithInternal extends GitRelativePath {
	private final GitAbsolutePathWithInternal absoluteEquivalent;

	GitRelativePathWithInternal(GitAbsolutePathWithInternal absoluteEquivalent) {
		this.absoluteEquivalent = checkNotNull(absoluteEquivalent);
		checkArgument(absoluteEquivalent.getRoot().toStaticRev().equals(GitRev.DEFAULT));
		final Path relative = absoluteEquivalent.getInternalPath().relativize(GitFileSystem.JIM_FS_SLASH);
		checkArgument(relative.getRoot() == null);
		checkArgument(relative.getNameCount() >= 1);
		final boolean hasEmptyName = Streams.stream(relative).anyMatch(p -> p.toString().isEmpty());
		if (hasEmptyName) {
			verify(relative.getNameCount() == 1);
		}
	}

	@Override
	public GitAbsolutePathWithInternal toAbsolutePath() {
		return absoluteEquivalent;
	}

	@Override
	Path getInternalPath() {
		return absoluteEquivalent.getInternalPath().relativize(GitFileSystem.JIM_FS_SLASH);
	}
}
