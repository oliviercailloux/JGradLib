package io.github.oliviercailloux.grade.markers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MarkHelperTests {
  @SuppressWarnings("unused")
  private static final Logger LOGGER = LoggerFactory.getLogger(MarkHelperTests.class);

  @Test
  void testFindBestMatch() throws Exception {
    assertEquals(ImmutableSet.of("plim"),
        MarkHelper.findBestMatches(ImmutableSet.of("ploum", "plim", "plum"),
            ImmutableList.of(s -> s.equals("Plim"), s -> s.equalsIgnoreCase("Plim"), s -> true)));
    assertEquals(ImmutableSet.of("plim"),
        MarkHelper.findBestMatches(ImmutableSet.of("ploum", "plim", "plum"),
            ImmutableList.of(s -> s.equals("plim"), s -> s.equals("ploum"), s -> true)));
    assertEquals(ImmutableSet.of(),
        MarkHelper.findBestMatches(ImmutableSet.of("ploum", "plim", "plum"), ImmutableList
            .of(s -> s.equals("dqfsd"), s -> s.equals("dfsfd"), s -> s.equals("dfsfddqsf"))));
    final ImmutableSet<String> best =
        MarkHelper.findBestMatches(ImmutableSet.of("ploum", "pl", "plum"), ImmutableList
            .of(s -> s.equals("dfqd"), s -> s.equalsIgnoreCase("Plim"), s -> s.equals("gddfd")));
    LOGGER.debug("Best matches: {}.", best);
    assertEquals(ImmutableSet.of(), best);
  }
}
