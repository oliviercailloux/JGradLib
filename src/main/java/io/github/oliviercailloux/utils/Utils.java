package io.github.oliviercailloux.utils;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.diffplug.common.base.Errors;
import com.diffplug.common.base.Throwing;
import com.google.common.base.Strings;
import com.google.common.base.Verify;
import com.google.common.collect.ImmutableList;
import com.google.common.graph.EndpointPair;
import com.google.common.graph.Graph;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.ImmutableGraph;
import com.google.common.graph.ImmutableGraph.Builder;
import com.google.common.graph.MutableGraph;
import com.google.common.graph.SuccessorsFunction;

public class Utils {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(Utils.class);

	public static final String ANY_REG_EXP = "[\\s\\S]*";

	public static final URI EXAMPLE_URI = URI.create("http://example.com");

	/**
	 * Using nanoseconds, and using the comma as a separator as recommended per
	 * <a href="https://en.wikipedia.org/wiki/ISO_8601">ISO 8601</a>.
	 */
	public static final DateTimeFormatter ISO_BASIC_UTC_FORMATTER = DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss','m")
			.withZone(ZoneOffset.UTC);

	public static boolean implies(boolean a, boolean b) {
		return !a || b;
	}

	public static <T, R> Function<T, R> uncheck(Throwing.Function<T, R> function) {
		return Errors.createRethrowing(IllegalStateException::new).wrap(function);
	}

	public static <T> T getOrThrow(SupplierThrowingIOException<T> supplier) {
		try {
			return supplier.get();
		} catch (Throwable e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 *
	 * TODO
	 * https://www.freecodecamp.org/news/why-you-should-ignore-exceptions-in-java-and-how-to-do-it-correctly-8e95e5775e58/
	 * https://github.com/ylegat/uncheck https://github.com/diffplug/durian/issues/8
	 */
	@FunctionalInterface
	public interface SupplierThrowingIOException<T> {
		T get() throws IOException;
	}

	/**
	 * Thanks to https://stackoverflow.com/a/13592567. Slightly modified to avoid
	 * null values.
	 */
	public static Map<String, ImmutableList<String>> getQuery(URI uri) {
		if (Strings.isNullOrEmpty(uri.getQuery())) {
			return Collections.emptyMap();
		}
		return Arrays.stream(uri.getQuery().split("&")).map(Utils::splitQueryParameter)
				.collect(Collectors.groupingBy(SimpleImmutableEntry::getKey, LinkedHashMap::new,
						Collectors.mapping(Map.Entry::getValue, ImmutableList.toImmutableList())));
	}

	private static SimpleImmutableEntry<String, String> splitQueryParameter(String it) {
		final int idx = it.indexOf("=");
		final String key = idx > 0 ? it.substring(0, idx) : it;
		final String value = idx > 0 && it.length() > idx + 1 ? it.substring(idx + 1) : "";
		return new SimpleImmutableEntry<>(key, value);
	}

	public static Path getTempDirectory() {
		return Paths.get(System.getProperty("java.io.tmpdir"));
	}

	public static <E> Graph<E> asGraph(SuccessorsFunction<E> successorsFunction, Set<E> tips) {
		checkNotNull(successorsFunction);
		checkNotNull(tips);
		checkArgument(tips.stream().allMatch((t) -> t != null));

		final Queue<E> toConsider = new LinkedList<>(tips);
		final Set<E> seen = new LinkedHashSet<>(tips);

		final MutableGraph<E> mutableGraph = GraphBuilder.directed().build();
		while (!toConsider.isEmpty()) {
			final E current = toConsider.remove();
			Verify.verify(current != null);
			mutableGraph.addNode(current);
			final Iterable<? extends E> successors = successorsFunction.successors(current);
			for (E successor : successors) {
				checkArgument(successor != null);
				mutableGraph.putEdge(current, successor);
				if (!seen.contains(successor)) {
					toConsider.add(successor);
					seen.add(successor);
				}
			}
		}
		return mutableGraph;
	}

	public static <E> ImmutableGraph<E> asImmutableGraph(Graph<E> graph) {
		if (graph instanceof ImmutableGraph) {
			return (ImmutableGraph<E>) graph;
		}
		final Builder<E> builder = GraphBuilder.from(graph).immutable();
		final Set<E> nodes = graph.nodes();
		for (E node : nodes) {
			builder.addNode(node);
		}
		final Set<EndpointPair<E>> edges = graph.edges();
		for (EndpointPair<E> edge : edges) {
			builder.putEdge(edge);
		}
		return builder.build();
	}

}
