package io.github.oliviercailloux.java_grade.graders;

import io.github.oliviercailloux.jaris.exceptions.TryCatchAll;
import io.github.oliviercailloux.jaris.throwing.TConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GraderVarious {
  @SuppressWarnings("unused")
  private static final Logger LOGGER = LoggerFactory.getLogger(GraderVarious.class);

  public void gradeCode() {
    final TryCatchAll<Integer> tryTarget = TryCatchAll.get(() -> 3);
    TConsumer<? super Integer, ?> consumer = Integer::byteValue;
    final TryCatchAll<Integer> got = tryTarget.andConsume(consumer);
    LOGGER.info("Got: {}.", got);
  }
}
