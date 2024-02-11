package io.github.oliviercailloux.javagrade.graders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableSet;
import io.github.oliviercailloux.grade.IGrade;
import io.github.oliviercailloux.jaris.io.PathUtils;
import io.github.oliviercailloux.javagrade.JavaGradeUtils;
import io.github.oliviercailloux.javagrade.bytecode.Compiler;
import io.github.oliviercailloux.javagrade.bytecode.Compiler.CompilationResult;
import io.github.oliviercailloux.utils.Utils;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CoffeeTests {
  @SuppressWarnings("unused")
  private static final Logger LOGGER = LoggerFactory.getLogger(CoffeeTests.class);

  @Test
  void testBad() throws Exception {
    final Path compiledDir = Utils.getTempUniqueDirectory("compile");
    final String subPath = "Coffee/Bad/";
    compile(subPath, compiledDir);

    final IGrade codeGrade = JavaGradeUtils.gradeSecurely(compiledDir, Coffee::grade);
    // Files.writeString(Path.of("out.html"), XmlUtils.toString(HtmlGrades.asHtml(codeGrade,
    // "bad")));
    assertEquals(0d, codeGrade.getPoints(), 1e-6d);
  }

  @Test
  void testPerfect() throws Exception {
    final Path compiledDir = Utils.getTempUniqueDirectory("compile");
    final String subPath = "Coffee/Perfect/";
    compile(subPath, compiledDir);

    final IGrade codeGrade = JavaGradeUtils.gradeSecurely(compiledDir, Coffee::grade);
    assertEquals(1d, codeGrade.getPoints());
  }

  public void compile(String subPath, Path compiledDir) throws URISyntaxException, IOException {
    final Path src = Path.of(getClass().getResource(".").toURI()).resolve(subPath);
    final ImmutableSet<Path> javas =
        PathUtils.getMatchingChildren(src, p -> p.getFileName().toString().endsWith(".java"));
    // CheckedStream.<Path, IOException>wrapping(javas.stream()).map(p ->
    // Files.readString(p)).forEach(LOGGER::info);
    final CompilationResult eclipseResult =
        Compiler.eclipseCompileUsingOurClasspath(javas, compiledDir);
    assertTrue(eclipseResult.compiled, eclipseResult.err);
    assertEquals(0, eclipseResult.countWarnings(), eclipseResult.err);
  }
}
