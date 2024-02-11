package io.github.oliviercailloux.bytecode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.base.VerifyException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.io.MoreFiles;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import io.github.classgraph.ClassGraph;
import io.github.oliviercailloux.javagrade.bytecode.Compiler;
import io.github.oliviercailloux.javagrade.bytecode.Compiler.CompilationResult;
import io.github.oliviercailloux.javagrade.bytecode.NewCompiler;
import java.io.StringWriter;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class CompilerTests {
  @SuppressWarnings("unused")
  private static final Logger LOGGER = LoggerFactory.getLogger(CompilerTests.class);

  @Test
  void testEclipseDestination() throws Exception {
    final String className = "SourceWithNoWarnings";
    final Path source = Path.of(getClass().getResource(className + ".java").toURI());
    {
      final Path destDir = Files.createTempDirectory("compiled");
      final CompilationResult result = Compiler.eclipseCompile(ImmutableList.of(Path.of(".")),
          ImmutableSet.of(source), true, Optional.of(destDir));
      assertTrue(result.compiled);
      assertEquals(0, result.countWarnings());
      final String packagePath = getClass().getPackageName().replace(".", "/");
      final Path compiledPath = destDir.resolve(packagePath).resolve(className + ".class");
      assertTrue(Files.exists(compiledPath), compiledPath.toString());
      MoreFiles.deleteRecursively(destDir);
    }
  }

  @Test
  void testEclipseMissingDep() throws Exception {
    final Path source = Path.of(getClass().getResource("UsingGuava.java").toURI());
    {
      final CompilationResult result =
          Compiler.eclipseCompile(ImmutableList.of(Path.of(".")), ImmutableSet.of(source));
      assertFalse(result.compiled);
      assertEquals(0, result.countWarnings());
      assertTrue(result.err.contains("The import com.google cannot be resolved"));
      assertTrue(result.err.contains("ImmutableList cannot be resolved"));
      assertEquals(2, result.countErrors());
    }
  }

  @Test
  void testEclipseWithDep() throws Exception {
    final Path source = Path.of(getClass().getResource("UsingGuava.java").toURI());
    final List<URI> classpath = new ClassGraph().getClasspathURIs();
    final ImmutableSet<URI> guavas = classpath.stream()
        .filter(u -> u.toString().contains("/guava-")).collect(ImmutableSet.toImmutableSet());
    final ImmutableSet<Path> guavaPaths =
        guavas.stream().map(Path::of).collect(ImmutableSet.toImmutableSet());
    {
      final CompilationResult result =
          Compiler.eclipseCompile(guavaPaths.asList(), ImmutableSet.of(source));
      assertEquals(0, result.countWarnings());
      assertEquals(0, result.countErrors());
      assertTrue(result.compiled);
    }
  }

  @Test
  void testEclipseWarn() throws Exception {
    final Path source = Path.of(getClass().getResource("SourceWithWarnings.java").toURI());
    assertTrue(Files.exists(source), source.toString());
    {
      final CompilationResult resultDefault =
          Compiler.eclipseCompile(ImmutableList.of(Path.of(".")), ImmutableSet.of(source), false);
      assertTrue(resultDefault.compiled);
      /**
       * Does not count the unnecessary type cast, the declared and unused throwing, …
       */
      assertEquals(2, resultDefault.countWarnings());
    }
    {
      final CompilationResult result =
          Compiler.eclipseCompile(ImmutableList.of(Path.of(".")), ImmutableSet.of(source));
      assertTrue(result.compiled);
      assertEquals(5, result.countWarnings());
    }
  }

  @Test
  void testEclipseNoWarn() throws Exception {
    final Path source = Path.of(getClass().getResource("SourceWithNoWarnings.java").toURI());
    final CompilationResult result =
        Compiler.eclipseCompile(ImmutableList.of(Path.of(".")), ImmutableSet.of(source));
    assertTrue(result.compiled);
    assertEquals(0, result.countWarnings());
  }

  @Test
  @Disabled("To be investigated.")
  void testNewCompilerWarn() throws Exception {
    final Path source = Path.of(getClass().getResource("SourceWithWarnings.java").toURI());
    final ImmutableList<Diagnostic<? extends JavaFileObject>> result =
        NewCompiler.create().setSourcePaths(ImmutableSet.of(source)).compile();
    assertEquals(0, result.size());
  }

  @Test
  void testNewCompilerNoWarn() throws Exception {
    final Path source = Path.of(getClass().getResource("SourceWithNoWarnings.java").toURI());
    final ImmutableList<Diagnostic<? extends JavaFileObject>> result =
        NewCompiler.create().setSourcePaths(ImmutableSet.of(source)).compile();
    assertEquals(0, result.size());
  }

  @Test
  void testJdkWarningsDontWarn() throws Exception {
    final Path sourcePath = Path.of(getClass().getResource("SourceWithWarnings.java").toURI());
    try (FileSystem jimFs = Jimfs.newFileSystem(Configuration.unix())) {
      final Path work = jimFs.getPath("");

      Compiler.intolerant(ImmutableList.of(), work).compile(ImmutableList.of(sourcePath));

      final ImmutableSet<Path> files;
      try (Stream<Path> stream = Files.walk(work.toAbsolutePath())) {
        files = stream.filter(Files::isRegularFile).collect(ImmutableSet.toImmutableSet());
      }
      assertEquals(ImmutableSet.of(work.resolve(getClass().getPackageName().replace('.', '/'))
          .resolve("SourceWithWarnings.class").toAbsolutePath()), files);
    }
  }

  @Test
  void testJdkWithCp() throws Exception {
    final Path sourceRequired =
        Path.of(getClass().getResource("SourceWithNoWarnings.java").toURI());
    final Path sourceRequiring =
        Path.of(getClass().getResource("MyFunctionRequiring.java").toURI());
    try (FileSystem jimFs = Jimfs.newFileSystem(Configuration.unix())) {
      final Path cp = jimFs.getPath("cp");
      Files.createDirectories(cp);
      final Path main = jimFs.getPath("main");
      Files.createDirectories(main);

      Compiler.intolerant(ImmutableList.of(), cp).compile(ImmutableList.of(sourceRequired));
      assertThrows(VerifyException.class, () -> Compiler.intolerant(ImmutableList.of(), main)
          .compile(ImmutableList.of(sourceRequiring)));
      Compiler.intolerant(ImmutableList.of(cp), main).compile(ImmutableList.of(sourceRequiring));

      final ImmutableSet<Path> files;
      try (Stream<Path> stream = Files.walk(main.toAbsolutePath())) {
        files = stream.filter(Files::isRegularFile).collect(ImmutableSet.toImmutableSet());
      }
      assertEquals(ImmutableSet.of(main.resolve(getClass().getPackageName().replace('.', '/'))
          .resolve("MyFunctionRequiring.class").toAbsolutePath()), files);
    }
  }

  @Test
  void testCompileIncorrectClassName() throws Exception {
    final Path work = Jimfs.newFileSystem(Configuration.unix()).getPath("");
    final Path sourceDir = work.resolve(getClass().getPackage().getName().replace('.', '/'));
    final Path sourcePath = sourceDir.resolve("AnotherName.java");
    {
      final String idFct =
          Files.readString(Path.of(getClass().getResource("SourceWithWarnings.java").toURI()));
      Files.createDirectories(sourceDir);
      Files.writeString(sourcePath, idFct);
    }

    final List<Diagnostic<? extends JavaFileObject>> diagnostics =
        Compiler.compile(ImmutableList.of(), Path.of("."), ImmutableSet.of(sourcePath));
    assertEquals(1, diagnostics.size());
    assertEquals(
        "io/github/oliviercailloux/bytecode/AnotherName.java:5: error: class SourceWithWarnings is public, should be declared in a file named SourceWithWarnings.java\n"
            + "public class SourceWithWarnings {\n" + "       ^",
        Iterables.getOnlyElement(diagnostics).toString());
  }

  /**
   * See comment in source of {@link Compiler#compile(…)}
   */
  @Test
  void testBugJdk() throws Exception {
    final Path sourcePath = Path.of(getClass().getResource("SourceWithNoWarnings.java").toURI());
    try (FileSystem jimFs = Jimfs.newFileSystem(Configuration.unix())) {
      final Path work = jimFs.getPath("");

      final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
      final DiagnosticCollector<JavaFileObject> diagnosticCollector = new DiagnosticCollector<>();
      try (StandardJavaFileManager fileManager =
          compiler.getStandardFileManager(diagnosticCollector, null, null)) {
        fileManager.setLocationFromPaths(StandardLocation.CLASS_PATH, ImmutableList.of(work));
        final Iterable<? extends JavaFileObject> srcToCompileObjs =
            fileManager.getJavaFileObjectsFromPaths(ImmutableList.of(sourcePath));
        final CompilationTask task = compiler.getTask(new StringWriter(), fileManager,
            diagnosticCollector, ImmutableList.of(), null, srcToCompileObjs);
        assertThrows(IllegalStateException.class, () -> task.call());
      }
    }
  }
}
