package io.github.oliviercailloux.students_project_following.read;

public class IllegalFormat extends RuntimeException {

	private static final long serialVersionUID = 1L;

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
