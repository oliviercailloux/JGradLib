package io.github.oliviercailloux.grade;

@SuppressWarnings("serial")
public class AggregatorException extends RuntimeException {

  public AggregatorException(String message, Throwable cause) {
    super(message, cause);
  }

  public AggregatorException(String message) {
    super(message);
  }

  public AggregatorException(Throwable cause) {
    super(cause);
  }
}
