package io.github.oliviercailloux.git.fs;

import com.google.common.base.MoreObjects;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchEvent.Modifier;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Objects;

/**
 * A path wrapping another path and delegating to it but linked to another fs.
 */
public abstract class PathWrapper implements Path {

	protected PathWrapper() {
	}

	protected abstract Path delegate();

	@Override
	public boolean isAbsolute() {
		return delegate().isAbsolute();
	}

	@Override
	public int getNameCount() {
		return delegate().getNameCount();
	}

	@Override
	public boolean startsWith(Path other) {
		return delegate().startsWith(other);
	}

	@Override
	public boolean endsWith(Path other) {
		return delegate().endsWith(other);
	}

	@Override
	public URI toUri() {
		return delegate().toUri();
	}

	@Override
	public WatchKey register(WatchService watcher, Kind<?>[] events, Modifier... modifiers) throws IOException {
		return delegate().register(watcher, events, modifiers);
	}

	@Override
	public int compareTo(Path other) {
		return delegate().compareTo(other);
	}

	@Override
	public boolean equals(Object o2) {
		if (!(o2 instanceof PathWrapper)) {
			return false;
		}
		final PathWrapper t2 = (PathWrapper) o2;
		return getFileSystem().equals(t2.getFileSystem()) && delegate().equals(t2.delegate());
	}

	@Override
	public int hashCode() {
		return Objects.hash(getFileSystem(), delegate());
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this).add("fs", getFileSystem()).add("delegate", delegate()).toString();
	}

}
