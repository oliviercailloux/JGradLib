package io.github.oliviercailloux.bytecode;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import io.github.oliviercailloux.javagrade.bytecode.Compiler;
import io.github.oliviercailloux.javagrade.bytecode.Instanciator;
import io.github.oliviercailloux.javagrade.bytecode.RestrictingClassLoader;
import io.github.oliviercailloux.javagrade.bytecode.SandboxSecurityPolicy;
import java.io.FilePermission;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.security.AccessControlException;
import java.security.AccessController;
import java.security.AllPermission;
import java.security.Permissions;
import java.security.Policy;
import java.security.SecurityPermission;
import java.util.function.Function;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Disabled("Compilation warns about deprecated security API.")
@SuppressWarnings({"removal", "deprecation"})
public class SandboxTests {
  @SuppressWarnings("unused")
  private static final Logger LOGGER = LoggerFactory.getLogger(SandboxTests.class);

  @Test
  void testSetPolicy() throws Exception {
    assertNull(System.getSecurityManager());
    assertThrows(AccessControlException.class,
        () -> AccessController.checkPermission(new FilePermission("/-", "read")));

    assertNotNull(Policy.getPolicy());
    SandboxSecurityPolicy.setSecurity();

    assertNotNull(System.getSecurityManager());
    assertDoesNotThrow(() -> AccessController.checkPermission(new FilePermission("/-", "read")));
    assertNotNull(Policy.getPolicy());

    final Path sourcePath = Path.of(getClass().getResource("MyFunctionReading.java").toURI());
    try (FileSystem jimFs = Jimfs.newFileSystem(Configuration.unix())) {
      final Path work = jimFs.getPath("");

      Compiler.intolerant(ImmutableList.of(), work).compileSrcs(ImmutableList.of(sourcePath));

      final URL url = work.toUri().toURL();
      try (URLClassLoader loader =
          RestrictingClassLoader.noPermissions(url, getClass().getClassLoader())) {
        final Instanciator instanciator = Instanciator.given(loader);
        final Function<String, String> myFct =
            instanciator.getInstance(Function.class, "newInstance").get();
        assertThrows(AccessControlException.class, () -> myFct.apply(null));
      }

      final Permissions securityPermissions = new Permissions();
      securityPermissions.add(new SecurityPermission("*"));
      try (URLClassLoader loader =
          RestrictingClassLoader.granting(url, getClass().getClassLoader(), securityPermissions)) {
        final Instanciator instanciator = Instanciator.given(loader);
        final Function<String, String> myFct =
            instanciator.getInstance(Function.class, "newInstance").get();
        assertDoesNotThrow(() -> myFct.apply(null));
      }

      final Permissions filePermissions = new Permissions();
      filePermissions.add(new FilePermission("/-", "read"));
      try (URLClassLoader loader =
          RestrictingClassLoader.granting(url, getClass().getClassLoader(), filePermissions)) {
        final Instanciator instanciator = Instanciator.given(loader);
        final Function<String, String> myFct =
            instanciator.getInstance(Function.class, "newInstance").get();
        assertThrows(AccessControlException.class, () -> myFct.apply(null));
      }

      final Permissions allPermissions = new Permissions();
      allPermissions.add(new AllPermission());
      try (URLClassLoader loader =
          RestrictingClassLoader.granting(url, getClass().getClassLoader(), allPermissions)) {
        final Instanciator instanciator = Instanciator.given(loader);
        final Function<String, String> myFct =
            instanciator.getInstance(Function.class, "newInstance").get();
        assertDoesNotThrow(() -> myFct.apply(null));
      }
    }

  }

  @Test
  void testCheckingFilePermission() throws Exception {
    SandboxSecurityPolicy.setSecurity();

    final Path sourcePath =
        Path.of(getClass().getResource("MyFunctionCheckingFilePermission.java").toURI());
    try (FileSystem jimFs = Jimfs.newFileSystem(Configuration.unix())) {
      final Path work = jimFs.getPath("");

      Compiler.intolerant(ImmutableList.of(), work).compileSrcs(ImmutableList.of(sourcePath));

      final URL url = work.toUri().toURL();
      try (URLClassLoader loader =
          RestrictingClassLoader.noPermissions(url, getClass().getClassLoader())) {
        final Instanciator instanciator = Instanciator.given(loader);
        final Function<String, String> myFct =
            instanciator.getInstance(Function.class, "newInstance").get();
        assertThrows(AccessControlException.class, () -> myFct.apply(null));
      }

      final Permissions filePermissions = new Permissions();
      filePermissions.add(new FilePermission("/-", "read"));
      try (URLClassLoader loader =
          RestrictingClassLoader.granting(url, getClass().getClassLoader(), filePermissions)) {
        final Instanciator instanciator = Instanciator.given(loader);
        final Function<String, String> myFct =
            instanciator.getInstance(Function.class, "newInstance").get();
        assertDoesNotThrow(() -> myFct.apply(null));
      }

      final Permissions securityPermissions = new Permissions();
      securityPermissions.add(new SecurityPermission("*"));
      try (URLClassLoader loader =
          RestrictingClassLoader.granting(url, getClass().getClassLoader(), securityPermissions)) {
        final Instanciator instanciator = Instanciator.given(loader);
        final Function<String, String> myFct =
            instanciator.getInstance(Function.class, "newInstance").get();
        assertThrows(AccessControlException.class, () -> myFct.apply(null));
      }

      final Permissions allPermissions = new Permissions();
      allPermissions.add(new AllPermission());
      try (URLClassLoader loader =
          RestrictingClassLoader.granting(url, getClass().getClassLoader(), allPermissions)) {
        final Instanciator instanciator = Instanciator.given(loader);
        final Function<String, String> myFct =
            instanciator.getInstance(Function.class, "newInstance").get();
        assertDoesNotThrow(() -> myFct.apply(null));
      }
    }

  }
}
