package io.github.oliviercailloux.utils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import io.github.oliviercailloux.bytecode.SandboxTests;
import io.github.oliviercailloux.javagrade.bytecode.Compiler;
import io.github.oliviercailloux.javagrade.bytecode.Instanciator;
import io.github.oliviercailloux.javagrade.bytecode.RestrictingClassLoader;
import io.github.oliviercailloux.javagrade.bytecode.SandboxSecurityPolicy;
import java.io.FilePermission;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.AccessControlException;
import java.security.AccessController;
import java.security.AllPermission;
import java.security.Permissions;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings({"removal"})
class UtilsTests {
  @SuppressWarnings("unused")
  private static final Logger LOGGER = LoggerFactory.getLogger(UtilsTests.class);

  @Test
  void testQuery() throws Exception {
    final String queryStr = "first=first value&second=(already)?&first=";
    final URI uri = new URI("ssh", "", "host", -1, "/path", queryStr, null);
    assertEquals(queryStr, uri.getQuery());
    assertEquals("first=first%20value&second=(already)?&first=", uri.getRawQuery());
    final Map<String, ImmutableList<String>> query = Utils.getQuery(uri);
    assertEquals(ImmutableSet.of("first", "second"), query.keySet());
    assertEquals(ImmutableList.of("first value", ""), query.get("first"));
    assertEquals(ImmutableList.of("(already)?"), query.get("second"));
  }

  @Test
  void testCopyResursivelyAuthorized() throws Exception {
    try (FileSystem jimFs = Jimfs.newFileSystem(Configuration.unix())) {
      final Path source = jimFs.getPath("/source/");
      Files.createDirectory(source);
      final Path target = jimFs.getPath("/target/");
      Files.createDirectory(target);
      Files.writeString(source.resolve("file.txt"), "Hello.");
      Utils.copyRecursively(source, target);
      assertEquals("Hello.", Files.readString(target.resolve("file.txt")));
    }
  }

  @Test
  void testCopyResursivelyNotReadableFile() throws Exception {
    try (FileSystem jimFs = Jimfs.newFileSystem(Configuration.unix())) {
      final Path source = Path.of("/home/guest/");
      final Path target = jimFs.getPath("/target/");
      Files.createDirectory(target);
      assertThrows(IOException.class, () -> Utils.copyRecursively(source, target));
    }
  }

  // @Test
  void testCopyResursivelyReadableFile() throws Exception {
    try (FileSystem jimFs = Jimfs.newFileSystem(Configuration.unix())) {
      final Path source = Path.of("src/main/resources/");
      final Path target = jimFs.getPath("/target/");
      Files.createDirectory(target);
      Utils.copyRecursively(source, target);
      assertEquals("Hello.", Files.readString(target.resolve("file.txt")));
    }
  }

  @Test
  @SuppressWarnings("deprecation")
  void testCopyUnderPolicyFails() throws Exception {
    SandboxSecurityPolicy.setSecurity();
    assertDoesNotThrow(() -> AccessController.checkPermission(new FilePermission("/-", "read")));

    final Path sourcePath =
        Path.of(SandboxTests.class.getResource("MyFunctionCopying.java").toURI());
    try (FileSystem jimFs = Jimfs.newFileSystem(Configuration.unix())) {
      final Path work = jimFs.getPath("");

      Compiler.intolerant(ImmutableList.of(Path.of("target/classes/")), work)
          .compile(ImmutableList.of(sourcePath));

      final URL url = work.toUri().toURL();
      try (URLClassLoader loader =
          RestrictingClassLoader.noPermissions(url, getClass().getClassLoader())) {
        final Instanciator instanciator = Instanciator.given(loader);
        final Function<String, String> myFct =
            instanciator.getInstance(Function.class, "newInstance").get();
        /**
         * Note that this actually fails while attempting to execute the very first argument check
         * in the copying function, which requires some path access.
         */
        assertThrows(AccessControlException.class, () -> myFct.apply(null));
      }
      final Permissions permissions = new Permissions();
      permissions.add(new AllPermission());
      try (URLClassLoader loader =
          RestrictingClassLoader.granting(url, getClass().getClassLoader(), permissions)) {
        final Instanciator instanciator = Instanciator.given(loader);
        final Function<String, String> myFct =
            instanciator.getInstance(Function.class, "newInstance").get();
        /**
         * This succeeds from the security point of view, which makes it fail because of the
         * argument check.
         */
        assertThrows(IllegalArgumentException.class, () -> myFct.apply(null));
      }
    }

  }
}
