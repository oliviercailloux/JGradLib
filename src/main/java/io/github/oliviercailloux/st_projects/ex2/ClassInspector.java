package io.github.oliviercailloux.st_projects.ex2;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Paths;
import java.util.Collection;

import org.apache.maven.shared.invoker.DefaultInvocationRequest;
import org.apache.maven.shared.invoker.DefaultInvoker;
import org.apache.maven.shared.invoker.InvocationRequest;
import org.apache.maven.shared.invoker.InvocationResult;
import org.apache.maven.shared.invoker.Invoker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ClassInfoList;
import io.github.classgraph.ScanResult;

public class ClassInspector {
	public static void main(String[] args) throws Exception {
		final ClassInspector grader = new ClassInspector();
//		grader.proceed();
		grader.access();
	}

	private void access()
			throws ClassNotFoundException, NoSuchMethodException, SecurityException, InstantiationException,
			IllegalAccessException, IllegalArgumentException, InvocationTargetException, IOException {
		LOGGER.info("Start.");
		final URL url = Paths
				.get("/home/olivier/Local/Dropbox/Recherche/Decision Deck/J-MCDA/jmcda-utils/target/classes/").toUri()
				.toURL();
		try (URLClassLoader child = new URLClassLoader(new URL[] { url }, this.getClass().getClassLoader())) {
			final ClassGraph graph = new ClassGraph().overrideClassLoaders(child).enableAllInfo()
					.whitelistPackages("org.decision_deck.utils.relation");
			try (ScanResult scanResult = graph.scan()) {
				final ClassInfoList cls = scanResult.getClassesImplementing(Collection.class.getName());
				for (ClassInfo info : cls) {
					LOGGER.info("Name: {}.", info.getName());
				}
			}
			Class<?> classToLoad = Class.forName("org.decision_deck.utils.relation.graph.Preorder", true, child);
			LOGGER.info("Class: {}.", classToLoad.getCanonicalName());
			final Constructor<?> constructor = classToLoad.getConstructor();
			final Collection<?> created = (Collection<?>) constructor.newInstance();
//		Method method = classToLoad.getDeclaredMethod("create");
//		Object created = method.invoke(null);
			LOGGER.info("Created: {} ({}).", created, created.hashCode());
		}
	}

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(ClassInspector.class);
}
