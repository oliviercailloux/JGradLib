package io.github.oliviercailloux.java_grade.bytecode;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Verify.verify;

import com.google.common.base.VerifyException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.MoreCollectors;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ClassInfoList;
import io.github.classgraph.MethodInfo;
import io.github.classgraph.MethodInfoList;
import io.github.classgraph.ScanResult;
import io.github.oliviercailloux.jaris.exceptions.TryCatchAll;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Instanciator {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(Instanciator.class);

	public static Instanciator given(URLClassLoader loader) {
		return new Instanciator(loader);
	}

	private final URLClassLoader loader;
	private Optional<Class<?>> classOpt;
	private ReflectiveOperationException lastException;

	private Instanciator(URLClassLoader loader) {
		this.loader = checkNotNull(loader);
		classOpt = null;
		lastException = null;
	}

	public <T> Optional<T> getInstance(Class<T> type) {
		return getInstance(type, Optional.empty(), ImmutableList.of());
	}

	public <T> TryCatchAll<T> tryGetInstance(Class<T> type) {
		final Optional<T> instance = getInstance(type, Optional.empty(), ImmutableList.of());
		return instance.map(TryCatchAll::success).orElseGet(() -> TryCatchAll.failure(getLastException()));
	}

	public <T> T getInstanceOrThrow(Class<T> type) throws ReflectiveOperationException {
		final Optional<T> instance = getInstance(type, Optional.empty(), ImmutableList.of());
		return instance.orElseThrow(() -> getLastException());
	}

	public <T> Optional<T> getInstance(Class<T> type, String staticFactoryMethodName) {
		return getInstance(type, Optional.of(staticFactoryMethodName), ImmutableList.of());
	}

	public <T> TryCatchAll<T> tryGetInstance(Class<T> type, String staticFactoryMethodName) {
		final Optional<T> instance = getInstance(type, Optional.of(staticFactoryMethodName), ImmutableList.of());
		return instance.map(TryCatchAll::success).orElseGet(() -> TryCatchAll.failure(getLastException()));
	}

	public <T> T getInstanceOrThrow(Class<T> type, String staticFactoryMethodName, List<Object> args)
			throws ReflectiveOperationException {
		final Optional<T> instance = getInstance(type, Optional.of(staticFactoryMethodName), args);
		return instance.orElseThrow(() -> getLastException());
	}

	private <T> Optional<T> getInstance(Class<T> type, Optional<String> staticFactoryMethodNameOpt, List<Object> args) {
		checkNotNull(staticFactoryMethodNameOpt);
		lastException = null;

		final ClassGraph classGraph = new ClassGraph();
		Stream.of(loader.getURLs()).distinct().forEach(u -> classGraph.enableURLScheme(u.getProtocol()));
		final ClassGraph graph = classGraph.overrideClassLoaders(loader).ignoreParentClassLoaders().enableAllInfo();
		LOGGER.debug("Scanning.");
		try (ScanResult scanResult = graph.scan()) {
			LOGGER.debug("Scan found: {}.", scanResult.getAllClasses().size());
			final ClassInfoList implementingClasses = scanResult.getClassesImplementing(type.getTypeName());
			LOGGER.debug("Implementing: {}.", implementingClasses.size());
			final Optional<ClassInfo> infoOpt = implementingClasses.directOnly().getStandardClasses().stream()
					.collect(MoreCollectors.toOptional());
			classOpt = infoOpt.map(ClassInfo::loadClass);
			if (infoOpt.isPresent() && !infoOpt.get().isPublic()) {
				LOGGER.debug("Class {} is not public, problem might follow.", infoOpt);
			}
			Optional<Object> instanceOpt;
			if (staticFactoryMethodNameOpt.isPresent()) {
				final Optional<MethodInfoList> methodInfoListOpt = infoOpt
						.map(c -> c.getDeclaredMethodInfo(staticFactoryMethodNameOpt.get()));
				LOGGER.debug("Found {} classes in {}, implementing: {}, with method: {}.",
						scanResult.getAllClasses().size(), loader.getURLs(), implementingClasses.size(),
						methodInfoListOpt);
				final Optional<MethodInfo> methodInfoOpt = methodInfoListOpt
						.flatMap(l -> l.stream().collect(MoreCollectors.toOptional()));
				final boolean isPublic = methodInfoOpt.map(MethodInfo::isPublic).orElse(false);
				if (isPublic) {
					final Method method = methodInfoOpt.get().loadClassAndGetMethod();
					method.setAccessible(true);
					try {
						instanceOpt = Optional.of(method.invoke(null, args.toArray()));
					} catch (IllegalAccessException | IllegalArgumentException e) {
						throw new VerifyException(e);
					} catch (InvocationTargetException e) {
						lastException = e;
						instanceOpt = Optional.empty();
					}
				} else {
					lastException = new InvocationTargetException(new IllegalArgumentException("Factory not found"));
					instanceOpt = Optional.empty();
				}
			} else {
				final ImmutableSet<Constructor<?>> constructors = classOpt
						.map(c -> ImmutableSet.copyOf(c.getDeclaredConstructors())).orElse(ImmutableSet.of());
				final Optional<Constructor<?>> parameterlessConstructor = constructors.stream()
						.filter(c -> c.getParameters().length == 0).collect(MoreCollectors.toOptional());
				if (parameterlessConstructor.isPresent()) {
					try {
						final Constructor<?> constructor = parameterlessConstructor.get();
						constructor.setAccessible(true);
						instanceOpt = Optional.of(constructor.newInstance());
					} catch (IllegalAccessException | IllegalArgumentException e) {
						throw new VerifyException(e);
					} catch (InvocationTargetException | InstantiationException e) {
						lastException = e;
						instanceOpt = Optional.empty();
					}
				} else {
					lastException = new InvocationTargetException(
							new IllegalArgumentException("Constructor not found"));
					instanceOpt = Optional.empty();
				}
			}
			verify(instanceOpt.isEmpty() == (lastException != null));
			return instanceOpt.map(type::cast);
		}
	}

	public Optional<Class<?>> getLastClass() {
		checkState(classOpt != null);
		return classOpt;
	}

	public ReflectiveOperationException getLastException() {
		checkState(lastException != null);
		return lastException;
	}
}
