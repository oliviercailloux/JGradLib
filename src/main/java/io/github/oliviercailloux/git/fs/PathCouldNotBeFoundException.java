package io.github.oliviercailloux.git.fs;

import java.io.IOException;

/**
 * We could not determine which file this path locates, or even whether it
 * exists, because we may not follow links but the path does contain links.
 */
@SuppressWarnings("serial")
public class PathCouldNotBeFoundException extends IOException {

	public PathCouldNotBeFoundException() {
		super();
	}

	public PathCouldNotBeFoundException(String message) {
		super(message);
	}

}