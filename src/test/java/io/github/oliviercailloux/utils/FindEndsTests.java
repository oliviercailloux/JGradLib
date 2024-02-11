package io.github.oliviercailloux.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.common.collect.ImmutableSet;
import io.github.oliviercailloux.java_grade.utils.FindEnds;
import org.junit.jupiter.api.Test;

public class FindEndsTests {

  @Test
  void testFindEnds() throws Exception {
    assertEquals(ImmutableSet.of(), FindEnds.withPrefix("commit").getEnded());
  }

}
