package io.github.oliviercailloux.grade.contexters;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class GradingTests {

  @SuppressWarnings("unused")
  private static final Logger LOGGER = LoggerFactory.getLogger(GradingTests.class);

  @Test
  void testGroupIdDiscovery() throws Exception {
    {
      final PomContexter c = getPomContexter("<project blah>HEY<groupId>one.two</groupId>HUH");
      assertEquals("one.two", c.getGroupId());
      assertEquals(ImmutableList.of("one", "two"), c.getGroupIdElements());
    }
    {
      final PomContexter c = getPomContexter("<project>HEY\n<groupId>one.two</groupId>");
      assertEquals("one.two", c.getGroupId());
      assertEquals(ImmutableList.of("one", "two"), c.getGroupIdElements());
    }
    {
      final PomContexter c = getPomContexter(
          "HEY<project>\n\t <groupId>one.two</groupId>\n<dependencies><groupId>ploum.again</groupId>");
      assertEquals("one.two", c.getGroupId());
      assertEquals(ImmutableList.of("one", "two"), c.getGroupIdElements());
    }
  }

  private PomContexter getPomContexter(String pom) {
    final PomContexter supp = new PomContexter(pom);
    supp.init();
    return supp;
  }
}
