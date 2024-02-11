package io.github.oliviercailloux.java_grade.ex_extractor;

import java.io.IOException;
import java.io.StringWriter;

public class MyStringWriter extends StringWriter {
  private boolean hasBeenClosed;

  public boolean hasBeenClosed() {
    return hasBeenClosed;
  }

  public MyStringWriter() {
    hasBeenClosed = false;
  }

  @Override
  public void close() throws IOException {
    super.close();
    hasBeenClosed = true;
  }
}
