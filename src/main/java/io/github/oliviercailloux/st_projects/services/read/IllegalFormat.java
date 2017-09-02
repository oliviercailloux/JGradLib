package io.github.oliviercailloux.st_projects.services.read;

public class IllegalFormat extends Exception {

	public IllegalFormat() {
		super();
	}

	public IllegalFormat(String message) {
		super(message);
	}

	public IllegalFormat(String message, Throwable cause) {
		super(message, cause);
	}

	public IllegalFormat(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	public IllegalFormat(Throwable cause) {
		super(cause);
	}

}
