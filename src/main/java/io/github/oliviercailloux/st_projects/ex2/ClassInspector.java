package io.github.oliviercailloux.st_projects.ex2;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Paths;
import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

	@SuppressWarnings("rawtypes")
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
			Class<? extends Collection> classToLoad = Class
					.forName("org.decision_deck.utils.relation.graph.Preorder", true, child)
					.asSubclass(Collection.class);
			LOGGER.info("Class: {}.", classToLoad.getCanonicalName());
			final Constructor<? extends Collection> constructor = classToLoad.getConstructor();
			final Collection<?> created = constructor.newInstance();
//		Method method = classToLoad.getDeclaredMethod("create");
//		Object created = method.invoke(null);
			LOGGER.info("Created: {} ({}).", created, created.hashCode());
		}
	}

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(ClassInspector.class);
}
