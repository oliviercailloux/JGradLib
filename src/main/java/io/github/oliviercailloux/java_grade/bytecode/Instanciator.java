package io.github.oliviercailloux.java_grade.bytecode;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Verify.verify;

import com.google.common.base.VerifyException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.MoreCollectors;
import com.google.common.collect.Sets;
import com.google.common.primitives.Primitives;
import com.google.common.reflect.TypeToken;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ClassInfoList;
import io.github.classgraph.MethodInfo;
import io.github.classgraph.MethodInfoList;
import io.github.classgraph.ScanResult;
import io.github.oliviercailloux.jaris.exceptions.Try;
import io.github.oliviercailloux.jaris.exceptions.TryCatchAll;
import io.github.oliviercailloux.jaris.throwing.TOptional;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URLClassLoader;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Instanciator {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(Instanciator.class);

	private static Class<?> toPrimitiveIfPossible(Class<?> type) {
		return Primitives.isWrapperType(type) ? Primitives.unwrap(type) : type;
	}

	private static ImmutableList<Class<?>> toPrimitiveIfPossible(List<Class<?>> list) {
		return list.stream().map(Instanciator::toPrimitiveIfPossible)
				.collect(ImmutableList.toImmutableList());
	}

	private static Executable getExecutable(Class<?> clz, Optional<String> methodName,
			Set<? extends List<Class<?>>> possibleArgTypes) throws NoSuchMethodException {
		final Iterator<? extends List<Class<?>>> iterator = possibleArgTypes.iterator();
		checkArgument(iterator.hasNext());
		final Try<Executable, NoSuchMethodException> firstAttempt;
		{
			final List<Class<?>> argTypes = iterator.next();
			final Class<?>[] argTypesArray = argTypes.toArray(new Class[] {});
			LOGGER.debug("Searching for method {}#{} using arg types {}.", clz, methodName, argTypes);
			firstAttempt = Try.get(() -> TOptional.wrapping(methodName)
					.<Executable, NoSuchMethodException>map(n -> clz.getMethod(n, argTypesArray))
					.orElseGet(() -> clz.getConstructor(argTypesArray)));
		}
		Try<Executable, NoSuchMethodException> t = firstAttempt;
		while (!t.isSuccess() && iterator.hasNext()) {
			final List<Class<?>> argTypes = iterator.next();
			final Class<?>[] argTypesArray = argTypes.toArray(new Class[] {});
			LOGGER.debug("Searching for method {}#{} using arg types {}.", clz, methodName, argTypes);
			t = firstAttempt.or(() -> TOptional.wrapping(methodName)
					.<Executable, NoSuchMethodException>map(n -> clz.getMethod(n, argTypesArray))
					.orElseGet(() -> clz.getConstructor(argTypesArray)), (c, d) -> c);
		}
		return t.orThrow();
	}

	private static Object invokeExecutable(Optional<Object> instance, Executable executable,
			List<?> args) throws InvocationTargetException, InstantiationException {
		final Object[] argsArray = args.toArray();
		try {
			if (executable instanceof Method m) {
				/*
				 * TODO using this is required to avoid an IllegalAccessException when invoking a public
				 * static factory method that uses an anonymous instance and returns it.
				 */
				m.trySetAccessible();
				return m.invoke(instance.orElse(null), argsArray);
			} else if (executable instanceof Constructor<?> c) {
				checkArgument(instance.isEmpty());
				c.trySetAccessible();
				return c.newInstance(argsArray);
			} else {
				throw new VerifyException();
			}
		} catch (IllegalAccessException e) {
			throw new VerifyException("I guess that this shouldn’t happen.", e);
		} catch (NullPointerException e) {
			throw new VerifyException("Method was verified static but null object was rejected.", e);
		} catch (IllegalArgumentException e) {
			throw new VerifyException(
					"Method was found using those exact arguments but they were rejected.", e);
		}
	}

	private static <T> ImmutableSet<Class<?>> getAllSuperClasses(Class<T> c) {
		final Set<Class<? super T>> supers = TypeToken.of(c).getTypes().rawTypes();
		return ImmutableSet.copyOf(supers);
	}

	public static <T> TryCatchAll<Optional<T>> invoke(Object instance, Class<T> returnType,
			String methodName, Object... args) {
		return invoke(instance.getClass(), Optional.of(instance), returnType, Optional.of(methodName),
				ImmutableList.copyOf(args));
	}

	public static <T> TryCatchAll<Optional<T>> invoke(Object instance, Class<T> returnType,
			String methodName, List<?> args) {
		return invoke(instance.getClass(), Optional.of(instance), returnType, Optional.of(methodName),
				args);
	}

	public static <T> TryCatchAll<T> invokeProducing(Object instance, Class<T> returnType,
			String methodName, Object... args) {
		return invoke(instance.getClass(), Optional.of(instance), returnType, Optional.of(methodName),
				ImmutableList.copyOf(args)).andApply(o -> o.orElseThrow());
	}

	private static <T> TryCatchAll<Optional<T>> invoke(Class<?> clz, Optional<Object> instance,
			Class<T> returnType, Optional<String> methodName, List<?> args) {
		final ImmutableList<Class<?>> argTypes =
				args.stream().map(a -> a.getClass()).collect(ImmutableList.toImmutableList());
		// final ImmutableList<Class<?>> argTypesPrim =
		// argTypes.stream().map(Instanciator::toPrimitiveIfPossible)
		// .collect(ImmutableList.toImmutableList());
		final ImmutableList<ImmutableSet<Class<?>>> setsOfSuperClasses =
				argTypes.stream().map(c -> getAllSuperClasses(c)).collect(ImmutableList.toImmutableList());
		final Set<List<Class<?>>> possibleArgTypes = Sets.cartesianProduct(setsOfSuperClasses);
		final ImmutableSet<ImmutableList<Class<?>>> possibleArgTypesPrim = possibleArgTypes.stream()
				.map(l -> toPrimitiveIfPossible(l)).collect(ImmutableSet.toImmutableSet());
		final Executable method;
		try {
			method = getExecutable(clz, methodName, possibleArgTypesPrim);
		} catch (NoSuchMethodException e) {
			return TryCatchAll.failure(e);
		}
		if (methodName.isPresent()
				&& (instance.isEmpty() != Modifier.isStatic(method.getModifiers()))) {
			return TryCatchAll.failure(new IllegalArgumentException("Unexpectedly static."));
		}

		final Object result;
		try {
			LOGGER.debug("Invoking method using args {}.", args);
			result = invokeExecutable(instance, method, args);
			LOGGER.debug("Received {}.", result);
		} catch (ExceptionInInitializerError | InstantiationException e) {
			return TryCatchAll.failure(e);
		} catch (InvocationTargetException e) {
			return TryCatchAll.failure(e.getCause());
		}
		if (result != null && !returnType.isInstance(result)) {
			return TryCatchAll.failure(new IllegalArgumentException("Unexpected return type."));
		}

		if (result == null) {
			verify(!methodName.isEmpty());
			return TryCatchAll.success(Optional.empty());
		}
		final T casted;
		try {
			casted = returnType.cast(result);
		} catch (ClassCastException e) {
			throw new VerifyException("Object was verified castable but is not.", e);
		}
		return TryCatchAll.success(Optional.of(casted));
	}

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
		return instance.map(TryCatchAll::success)
				.orElseGet(() -> TryCatchAll.failure(getLastException()));
	}

	public <T> T getInstanceOrThrow(Class<T> type) throws ReflectiveOperationException {
		final Optional<T> instance = getInstance(type, Optional.empty(), ImmutableList.of());
		return instance.orElseThrow(() -> getLastException());
	}

	public <T> Optional<T> getInstance(Class<T> type, String staticFactoryMethodName) {
		return getInstance(type, Optional.of(staticFactoryMethodName), ImmutableList.of());
	}

	public <T> TryCatchAll<T> tryGetInstance(Class<T> type, String staticFactoryMethodName) {
		final Optional<T> instance =
				getInstance(type, Optional.of(staticFactoryMethodName), ImmutableList.of());
		return instance.map(TryCatchAll::success)
				.orElseGet(() -> TryCatchAll.failure(getLastException()));
	}

	public <T> T getInstanceOrThrow(Class<T> type, String staticFactoryMethodName, List<Object> args)
			throws ReflectiveOperationException {
		final Optional<T> instance = getInstance(type, Optional.of(staticFactoryMethodName), args);
		return instance.orElseThrow(() -> getLastException());
	}

	private <T> Optional<T> getInstance(Class<T> type, Optional<String> staticFactoryMethodNameOpt,
			List<Object> args) {
		checkNotNull(staticFactoryMethodNameOpt);
		lastException = null;

		final ClassGraph classGraph = new ClassGraph();
		Stream.of(loader.getURLs()).distinct()
				.forEach(u -> classGraph.enableURLScheme(u.getProtocol()));
		final ClassGraph graph =
				classGraph.overrideClassLoaders(loader).ignoreParentClassLoaders().enableAllInfo();
		LOGGER.debug("Scanning.");
		try (ScanResult scanResult = graph.scan()) {
			LOGGER.debug("Scan found: {}.", scanResult.getAllClasses().size());
			final ClassInfoList implementingClasses =
					scanResult.getClassesImplementing(type.getTypeName());
			LOGGER.debug("Implementing: {}.", implementingClasses.size());
			final Optional<ClassInfo> infoOpt = implementingClasses.directOnly().getStandardClasses()
					.stream().collect(MoreCollectors.toOptional());
			classOpt = infoOpt.map(ClassInfo::loadClass);
			if (infoOpt.isPresent() && !infoOpt.get().isPublic()) {
				LOGGER.debug("Class {} is not public, problem might follow.", infoOpt);
			}
			Optional<Object> instanceOpt;
			if (staticFactoryMethodNameOpt.isPresent()) {
				final Optional<MethodInfoList> methodInfoListOpt =
						infoOpt.map(c -> c.getDeclaredMethodInfo(staticFactoryMethodNameOpt.get()));
				LOGGER.debug("Found {} classes in {}, implementing: {}, with method: {}.",
						scanResult.getAllClasses().size(), loader.getURLs(), implementingClasses.size(),
						methodInfoListOpt);
				final Optional<MethodInfo> methodInfoOpt =
						methodInfoListOpt.flatMap(l -> l.stream().collect(MoreCollectors.toOptional()));
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
					lastException =
							new InvocationTargetException(new IllegalArgumentException("Factory not found"));
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
					lastException =
							new InvocationTargetException(new IllegalArgumentException("Constructor not found"));
					instanceOpt = Optional.empty();
				}
			}
			verify(instanceOpt.isEmpty() == (lastException != null));
			return instanceOpt.map(type::cast);
		}
	}

	public <T> TryCatchAll<Optional<T>> invokeStatic(String className, Class<T> returnType,
			String methodName, List<Object> args) {
		return obtainFrom(className, returnType, Optional.of(methodName), args);
	}

	public <T> TryCatchAll<T> invokeConstructor(String className, Class<T> returnType, List<?> args) {
		final TryCatchAll<Optional<T>> opt = obtainFrom(className, returnType, Optional.empty(), args);
		return opt.andApply(o -> o.orElseThrow(VerifyException::new));
	}

	private <T> TryCatchAll<Optional<T>> obtainFrom(String className, Class<T> returnType,
			Optional<String> methodName, List<?> args) {
		checkNotNull(className);
		checkNotNull(methodName);
		checkNotNull(args);

		final Class<?> clz;
		try {
			clz = Class.forName(className, true, loader);
		} catch (ClassNotFoundException | ExceptionInInitializerError e) {
			return TryCatchAll.failure(e);
		}

		return invoke(clz, Optional.empty(), returnType, methodName, args);
	}

	public Optional<Class<?>> getLastClass() {
		checkState(classOpt != null);
		return classOpt;
	}

	public ReflectiveOperationException getLastException() {
		checkState(lastException != null);
		return lastException;
	}

	public URLClassLoader getLoader() {
		return loader;
	}
}
