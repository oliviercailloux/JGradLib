package io.github.oliviercailloux.git.fs;

import java.util.function.Predicate;

import org.eclipse.jgit.lib.ObjectId;

class GitFilteredFileSystem {
	public static GitFilteredFileSystem given(GitFileSystem delegate, Predicate<ObjectId> kept) {
		return new GitFilteredFileSystem(delegate, kept);
	}

	private final GitFileSystem delegate;
	private final Predicate<ObjectId> kept;

	private GitFilteredFileSystem(GitFileSystem delegate, Predicate<ObjectId> kept) {
		this.delegate = delegate;
		this.kept = kept;
	}
}
