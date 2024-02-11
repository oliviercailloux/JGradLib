package io.github.oliviercailloux.uml_graders;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.common.jimfs.Jimfs;
import io.github.oliviercailloux.grade.IGrade;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class AdminTests {
  @SuppressWarnings("unused")
  private static final Logger LOGGER = LoggerFactory.getLogger(AdminTests.class);

  @Test
  void testAlmostEmpty() throws Exception {
    final Path umlSource = Path.of(getClass().getResource("Admin/Almost empty.uml").toURI());
    try (FileSystem fs = Jimfs.newFileSystem()) {
      final Path di = fs.getPath("stuff.di");
      Files.createFile(di);
      Files.copy(umlSource, fs.getPath("uml.uml"));
      final IGrade grade = AdminManagesUsers.grade(fs.getPath("").toAbsolutePath());
      assertEquals(1d / 19d, grade.getPoints(), 1e-6);
    }
  }

  @Test
  void testBad() throws Exception {
    final Path uml = Path.of(getClass().getResource("Admin/Bad.uml").toURI());
    final IGrade grade = AdminManagesUsers.grade(uml);
    // Files.writeString(Path.of("Grade.html"), XmlUtils.toString(HtmlGrades.asHtml(grade, "Test
    // grade", 19d)));
    assertEquals((1d + 1.333333d + 1d + 1.5d) / 19d, grade.getPoints(), 1e-6);
  }

  @Test
  void testDuplicate() throws Exception {
    final Path uml = Path.of(getClass().getResource("Admin/Duplicate.uml").toURI());
    final IGrade grade = AdminManagesUsers.grade(uml);
    assertEquals(1d - 1d / 19d, grade.getPoints(), 1e-6d);
  }

  @Test
  void testDuplicateTwo() throws Exception {
    final Path uml = Path.of(getClass().getResource("Admin/Duplicate two.uml").toURI());
    final IGrade grade = AdminManagesUsers.grade(uml);
    /*
     * Manage UC is duplicated and one subject missing; Create is duplicated: not quite on .75, .75,
     * and not at all on .5.
     */
    assertEquals(1d - 0.666666d / 19d - 1d / 19d - 1d / 19d - 1.5d / 19d, grade.getPoints(), 1e-6d);
  }

  @Test
  void testPerfect() throws Exception {
    final Path uml = Path.of(getClass().getResource("Admin/Perfect.uml").toURI());
    final IGrade grade = AdminManagesUsers.grade(uml);
    assertEquals(1d, grade.getPoints());
  }
}
