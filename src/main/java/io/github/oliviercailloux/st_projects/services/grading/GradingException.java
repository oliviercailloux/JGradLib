package io.github.oliviercailloux.st_projects.services.grading;

@SuppressWarnings("serial")
public class GradingException extends RuntimeException {

	public GradingException() {
		super();
	}

	public GradingException(String message, Throwable cause) {
		super(message, cause);
	}

	public GradingException(String message) {
		super(message);
	}

	public GradingException(Throwable cause) {
		super(cause);
	}

}
