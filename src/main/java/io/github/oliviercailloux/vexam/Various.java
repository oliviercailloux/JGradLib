package io.github.oliviercailloux.vexam;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

public interface Various {
  /**
   * Logs something (anything you like, for example, “Hello, world”) to the SLF4J logging system (at
   * severity level “INFO”).
   */
  public void log();

  /**
   * See the related unit test.
   */
  public String bePolite();

  /**
   * Returns a string containing one {@code 1} the first time it is called on this instance, a
   * string containing two {@code 1}s the second time it is called on this instance, and so on. For
   * example, the third time it is called on this instance, this method returns the string
   * {@code 111}.
   *
   * @return a string containing as many {@code 1}s as the number of time this method has been
   *         called on this instance.
   */
  public String ones();

  /**
   * Returns the content of the given source, read and decoded using {@link StandardCharsets#UTF_8}.
   */
  public String read(Path source) throws IOException;

  /**
   * Adds a path to the list of sources to read from when {@link #readAll()} is called.
   */
  public void addPath(Path source);

  /**
   * Returns the contatenated content of all sources read and decoded using
   * {@link StandardCharsets#UTF_8}.
   */
  public String readAll() throws IOException;
}
