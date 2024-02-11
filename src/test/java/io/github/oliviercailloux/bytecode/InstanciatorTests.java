package io.github.oliviercailloux.bytecode;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.MoreCollectors;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ClassInfoList;
import io.github.classgraph.MethodInfo;
import io.github.classgraph.MethodInfoList;
import io.github.classgraph.ScanResult;
import io.github.oliviercailloux.exercices.car.Person;
import io.github.oliviercailloux.jaris.exceptions.TryCatchAll;
import io.github.oliviercailloux.java_grade.bytecode.Compiler;
import io.github.oliviercailloux.java_grade.bytecode.Instanciator;
import io.github.oliviercailloux.java_grade.bytecode.RestrictingClassLoader;
import io.github.oliviercailloux.persons_manager.PersonsManager;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InstanciatorTests {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(InstanciatorTests.class);

	@Test
	void testGetInstance() throws Exception {
		final Path sourcePath = Path.of(getClass().getResource("MyIdentityFunction.java").toURI());
		try (FileSystem jimFs = Jimfs.newFileSystem(Configuration.unix())) {
			final Path work = jimFs.getPath("");

			Compiler.intolerant(ImmutableList.of(), work).compile(ImmutableList.of(sourcePath));

			final URL url = work.toUri().toURL();
			try (URLClassLoader loader =
					new URLClassLoader(new URL[] {url}, getClass().getClassLoader())) {
				final Instanciator instanciator = Instanciator.given(loader);
				assertTrue(instanciator.getInstance(List.class, "newInstance").isEmpty());
				assertTrue(instanciator.getInstance(Function.class, "newInstanceWrongName").isEmpty());
				assertTrue(instanciator.getInstance(Function.class, "newInstance").isPresent());
			}
		}
	}

	@Test
	void testGetInstancePackagePrivate() throws Exception {
		final Path sourcePath =
				Path.of(getClass().getResource("MyPackagePrivateIdentityFunction.java").toURI());
		try (FileSystem jimFs = Jimfs.newFileSystem(Configuration.unix())) {
			final Path work = jimFs.getPath("");

			Compiler.intolerant(ImmutableList.of(), work).compile(ImmutableList.of(sourcePath));

			final URL url = work.toUri().toURL();
			try (URLClassLoader loader =
					new URLClassLoader(new URL[] {url}, getClass().getClassLoader())) {
				final Instanciator instanciator = Instanciator.given(loader);
				assertTrue(instanciator.getInstance(List.class, "newInstance").isEmpty());
				assertTrue(instanciator.getInstance(Function.class, "newInstanceWrongName").isEmpty());
				assertTrue(instanciator.getInstance(Function.class, "newInstance").isPresent());
			}
		}
	}

	@Test
	void testGetInstanceRequiring() throws Exception {
		final Path sourcePath1 = Path.of(getClass().getResource("MyFunctionRequiring.java").toURI());
		final Path sourcePath2 = Path.of(getClass().getResource("SourceWithNoWarnings.java").toURI());
		final ClassLoader parent = getClass().getClassLoader();
		final Package thisPackage = getClass().getPackage();
		assertThrows(ClassNotFoundException.class,
				() -> parent.loadClass(thisPackage + ".MyFunctionRequiring"));
		assertThrows(ClassNotFoundException.class,
				() -> parent.loadClass(thisPackage + ".SourceWithNoWarnings"));
		try (FileSystem jimFs = Jimfs.newFileSystem(Configuration.unix())) {
			final Path work = jimFs.getPath("");

			Compiler.intolerant(ImmutableList.of(), work).compile(ImmutableList.of(sourcePath1, sourcePath2));
			final Path destSourceWithNoWarnings = work.resolve(thisPackage.getName().replace('.', '/'))
					.resolve("SourceWithNoWarnings.class");
			// Files.delete(destSourceWithNoWarnings);
			assertTrue(Files.isRegularFile(destSourceWithNoWarnings));
			final URL url = work.toUri().toURL();
			try (URLClassLoader loader =
					new URLClassLoader(new URL[] {url}, getClass().getClassLoader())) {
				final Instanciator instanciator = Instanciator.given(loader);
				assertTrue(instanciator.getInstance(List.class, "newInstance").isEmpty());
				assertTrue(instanciator.getInstance(Function.class, "newInstanceWrongName").isEmpty());
				@SuppressWarnings("rawtypes")
				final Optional<Function> functionOpt =
						instanciator.getInstance(Function.class, "newInstance");
				assertTrue(functionOpt.isPresent());

				final Object ret = assertDoesNotThrow(() -> functionOpt.get().apply("t"));
				LOGGER.debug("Ret: {}.", ret);
			}
		}
	}

	@Test
	void testInstanceThrowing() throws Exception {
		final Path sourcePath =
				Path.of(getClass().getResource("MyIdentityFunctionThrowing.java").toURI());
		try (FileSystem jimFs = Jimfs.newFileSystem(Configuration.unix())) {
			final Path work = jimFs.getPath("");

			Compiler.intolerant(ImmutableList.of(), work).compile(ImmutableList.of(sourcePath));

			final URL url = work.toUri().toURL();
			try (URLClassLoader loader =
					new URLClassLoader(new URL[] {url}, getClass().getClassLoader())) {
				final Instanciator instanciator = Instanciator.given(loader);
				assertTrue(instanciator.getInstance(Function.class, "newInstance").isEmpty());
				final ReflectiveOperationException lastException = instanciator.getLastException();
				LOGGER.debug("Last exc:", lastException);
				assertNotNull(lastException);
			}
		}
	}

	/**
	 * Just to test what is allowed.
	 */
	@Test
	@Disabled("No more implementation of this interface in the class path")
	void testLoadManually() throws Exception {
		final ClassGraph classGraph = new ClassGraph();
		final ClassGraph graph = classGraph.enableAllInfo();
		LOGGER.debug("Scanning.");
		try (ScanResult scanResult = graph.scan()) {
			LOGGER.info("Scan found: {}.", scanResult.getAllClasses().size());
			final ClassInfoList implementingClasses =
					scanResult.getClassesImplementing(PersonsManager.class.getTypeName());
			LOGGER.info("Implementing: {}.", implementingClasses.size());
			final ClassInfo info = implementingClasses.directOnly().getStandardClasses().stream()
					.collect(MoreCollectors.onlyElement());
			final MethodInfoList methodInfoList = info.getDeclaredMethodInfo("empty");
			LOGGER.debug("Found {} classes, implementing: {}, with method: {}.",
					scanResult.getAllClasses().size(), implementingClasses.size(), methodInfoList);
			final MethodInfo methodInfo = methodInfoList.stream().collect(MoreCollectors.onlyElement());
			assertTrue(methodInfo.isPublic());
			final Method method = methodInfo.loadClassAndGetMethod();
			method.invoke(null);
		}
	}

	/**
	 * Just to test what is allowed.
	 */
	@Test
	@Disabled("No more implementation of this interface in the class path")
	void testLoadManuallyThis() throws Exception {
		final ClassGraph classGraph = new ClassGraph();
		final ClassGraph graph = classGraph.enableAllInfo();
		LOGGER.debug("Scanning.");
		{
			final Method method = getClass().getMethod("empty");
			method.invoke(null);
		}
		try (ScanResult scanResult = graph.scan()) {
			LOGGER.debug("Scan found: {}.", scanResult.getAllClasses().size());
			final ClassInfoList implementingClasses =
					scanResult.getClassesImplementing(PersonsManager.class.getTypeName());
			LOGGER.debug("Implementing: {}.", implementingClasses.size());
			final ClassInfo info = implementingClasses.directOnly().getStandardClasses().stream()
					.collect(MoreCollectors.onlyElement());
			LOGGER.info("Found class {}.", info);
			final Method directMethod = info.loadClass().getMethod("empty");
			directMethod.invoke(null);
			final MethodInfoList methodInfoList = info.getDeclaredMethodInfo("empty");
			LOGGER.debug("Found {} classes, implementing: {}, with method: {}.",
					scanResult.getAllClasses().size(), implementingClasses.size(), methodInfoList);
			final MethodInfo methodInfo = methodInfoList.stream().collect(MoreCollectors.onlyElement());
			assertTrue(methodInfo.isPublic());
			final Method method = methodInfo.loadClassAndGetMethod();
			method.invoke(null);
		}
	}

	@Test
	void testInvoke() throws Exception {
		final TryCatchAll<Optional<String>> obtained = Instanciator
				.invoke(ImmutableList.of("elem", "heh"), String.class, "get", ImmutableList.of(0));
		Function<? super String, Boolean> mapper = s -> s.equals("elem");
		assertTrue(obtained.map(o -> o.map(mapper).orElse(false), c -> false), "" + obtained);
	}

	@Test
	void testInvokeNoSuch() throws Exception {
		URLClassLoader loader = RestrictingClassLoader
				.noPermissions(Path.of("src/main/java/").toUri().toURL(), getClass().getClassLoader());

		LOGGER.info("Loading {}.", this.getClass().getCanonicalName());
		final TryCatchAll<Person> mPerson = Instanciator.given(loader).invokeConstructor(
				this.getClass().getCanonicalName(), Person.class, ImmutableList.of("nameHHKorig", 71));
		assertEquals(NoSuchMethodException.class,
				mPerson.getCause().map(Object::getClass).orElseThrow());
		final TryCatchAll<Optional<String>> obtained = Instanciator
				.invoke(ImmutableList.of("elem", "heh"), String.class, "invalid", ImmutableList.of(0));
		assertEquals(NoSuchMethodException.class,
				obtained.getCause().map(Object::getClass).orElseThrow());

		final TryCatchAll<Optional<Void>> noSuch = Instanciator.invoke(ImmutableList.of("elem", "heh"),
				Void.class, "renameNON", ImmutableList.of("a new name!"));
		assertEquals(NoSuchMethodException.class,
				noSuch.getCause().map(Object::getClass).orElseThrow());

		final TryCatchAll<InstanciatorTests> thisOne = Instanciator.given(loader).invokeConstructor(
				this.getClass().getCanonicalName(), InstanciatorTests.class, ImmutableList.of());
		assertEquals(InstanciatorTests.class, thisOne.getResult().map(Object::getClass).orElseThrow());

		final TryCatchAll<InstanciatorTests> chained = thisOne.andConsume(
				t -> Instanciator.invoke(t, Void.class, "renameNON", ImmutableList.of("a new name!")));
		assertEquals(NoSuchMethodException.class,
				chained.getCause().map(Object::getClass).orElseThrow());
	}

	public static void empty() {
		LOGGER.info("Called.");
	}
}
