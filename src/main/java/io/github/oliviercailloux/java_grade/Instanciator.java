package io.github.oliviercailloux.java_grade;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.MoreCollectors;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ClassInfoList;
import io.github.classgraph.MethodInfo;
import io.github.classgraph.MethodInfoList;
import io.github.classgraph.ScanResult;
import io.github.oliviercailloux.utils.Utils;

public class Instanciator {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(Instanciator.class);

	public static Instanciator given(URL url, ClassLoader parent) {
		return new Instanciator(url, parent);
	}

	private final URL url;
	private final ClassLoader parent;

	private Instanciator(URL url, ClassLoader delegate) {
		this.url = checkNotNull(url);
		this.parent = checkNotNull(delegate);
	}

	public <T> Optional<T> getInstance(Class<T> type, String staticFactoryMethodName) {
		checkNotNull(staticFactoryMethodName);
		try (URLClassLoader child = new URLClassLoader(new URL[] { url }, parent)) {
			final ClassGraph graph = new ClassGraph().overrideClassLoaders(child).ignoreParentClassLoaders()
					.enableAllInfo();
			try (ScanResult scanResult = graph.scan()) {
				final ClassInfoList implementingClasses = scanResult.getClassesImplementing(type.getTypeName());
				final Optional<ClassInfo> infoOpt = implementingClasses.directOnly().getStandardClasses().stream()
						.collect(MoreCollectors.toOptional());
				final Optional<MethodInfoList> methodInfoListOpt = infoOpt
						.map(c -> c.getDeclaredMethodInfo(staticFactoryMethodName));
				LOGGER.debug("Found {} classes in {}, implementing: {}, with method: {}.",
						scanResult.getAllClasses().size(), url, implementingClasses.size(), methodInfoListOpt);
				final Optional<MethodInfo> methodInfoOpt = methodInfoListOpt
						.flatMap(l -> l.stream().collect(MoreCollectors.toOptional()));
				final Optional<Method> methodOpt = methodInfoOpt.map(MethodInfo::loadClassAndGetMethod);
				final Optional<Object> instanceOpt = methodOpt.map(Utils.uncheck(m -> m.invoke(null)));
				return instanceOpt.map(type::cast);
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
