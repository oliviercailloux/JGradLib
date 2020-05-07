package io.github.oliviercailloux.java_grade.bytecode;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.VerifyException;
import com.google.common.collect.ImmutableSet;
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
	private Optional<Class<?>> classOpt;

	private Instanciator(URL url, ClassLoader delegate) {
		this.url = checkNotNull(url);
		this.parent = checkNotNull(delegate);
		classOpt = null;
	}

	public <T> Optional<T> getInstance(Class<T> type) throws InvocationTargetException {
		return getInstance(type, Optional.empty());
	}

	public <T> Optional<T> getInstance(Class<T> type, String staticFactoryMethodName) throws InvocationTargetException {
		return getInstance(type, Optional.of(staticFactoryMethodName));
	}

	private <T> Optional<T> getInstance(Class<T> type, Optional<String> staticFactoryMethodNameOpt)
			throws InvocationTargetException {
		checkNotNull(staticFactoryMethodNameOpt);
		try (URLClassLoader child = new URLClassLoader(new URL[] { url }, parent)) {
			final ClassGraph graph = new ClassGraph().enableURLScheme(url.getProtocol()).overrideClassLoaders(child)
					.ignoreParentClassLoaders().enableAllInfo();
			LOGGER.debug("Scanning.");
			try (ScanResult scanResult = graph.scan()) {
				LOGGER.debug("Scan found: {}.", scanResult.getAllClasses().size());
				final ClassInfoList implementingClasses = scanResult.getClassesImplementing(type.getTypeName());
				LOGGER.debug("Implementing: {}.", implementingClasses.size());
				final Optional<ClassInfo> infoOpt = implementingClasses.directOnly().getStandardClasses().stream()
						.collect(MoreCollectors.toOptional());
				classOpt = infoOpt.map(ClassInfo::loadClass);
				final Optional<Object> instanceOpt;
				if (staticFactoryMethodNameOpt.isPresent()) {
					final Optional<MethodInfoList> methodInfoListOpt = infoOpt
							.map(c -> c.getDeclaredMethodInfo(staticFactoryMethodNameOpt.get()));
					LOGGER.debug("Found {} classes in {}, implementing: {}, with method: {}.",
							scanResult.getAllClasses().size(), url, implementingClasses.size(), methodInfoListOpt);
					final Optional<MethodInfo> methodInfoOpt = methodInfoListOpt
							.flatMap(l -> l.stream().collect(MoreCollectors.toOptional()));
					final boolean isPublic = methodInfoOpt.map(MethodInfo::isPublic).orElse(false);
					if (isPublic) {
						final Optional<Method> methodOpt = methodInfoOpt.map(MethodInfo::loadClassAndGetMethod);
						try {
							instanceOpt = methodOpt.isPresent() ? Optional.of(methodOpt.get().invoke(null))
									: Optional.empty();
						} catch (IllegalAccessException | IllegalArgumentException e) {
							throw new VerifyException(e);
						}
					} else {
						instanceOpt = Optional.empty();
					}
				} else {
					final ImmutableSet<Constructor<?>> constructors = classOpt
							.map(c -> ImmutableSet.copyOf(c.getDeclaredConstructors())).orElse(ImmutableSet.of());
					final Optional<Constructor<?>> parameterlessConstructor = constructors.stream()
							.filter(c -> c.getParameters().length == 0).collect(MoreCollectors.toOptional());
					instanceOpt = parameterlessConstructor.map(Utils.uncheck(Constructor::newInstance));
				}
				return instanceOpt.map(type::cast);
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	public Optional<Class<?>> getLastClass() {
		checkState(classOpt != null);
		return classOpt;
	}
}
