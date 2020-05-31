package io.github.oliviercailloux.bytecode;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.FilePermission;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.security.AccessControlException;
import java.security.AccessController;
import java.security.Permissions;
import java.security.Policy;
import java.security.SecurityPermission;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;

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

			Compiler.intolerant(ImmutableList.of(), work).compile(ImmutableList.of(sourcePath));

			final URL url = work.toUri().toURL();
			try (URLClassLoader loader = RestrictingClassLoader.noPermissions(url, getClass().getClassLoader())) {
				final Instanciator instanciator = Instanciator.given(loader);
				final Function<String, String> myFct = instanciator.getInstance(Function.class, "newInstance").get();
				assertThrows(AccessControlException.class, () -> myFct.apply(null));
			}
			final Permissions permissions = new Permissions();
			permissions.add(new SecurityPermission("*"));
			try (URLClassLoader loader = RestrictingClassLoader.granting(url, getClass().getClassLoader(),
					permissions)) {
				final Instanciator instanciator = Instanciator.given(loader);
				final Function<String, String> myFct = instanciator.getInstance(Function.class, "newInstance").get();
				assertDoesNotThrow(() -> myFct.apply(null));
			}
		}

	}
}
