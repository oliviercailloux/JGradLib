package io.github.oliviercailloux.utils;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.io.FilePermission;
import java.io.IOException;
import java.net.URI;
import java.nio.file.CopyOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
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

import com.google.common.base.Strings;
import com.google.common.base.Verify;
import com.google.common.collect.ImmutableList;
import com.google.common.graph.EndpointPair;
import com.google.common.graph.Graph;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.ImmutableGraph;
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

	public static Path getTempUniqueDirectory(String prefix) {
		return getTempDirectory().resolve(prefix + " " + Utils.ISO_BASIC_UTC_FORMATTER.format(Instant.now()));
	}

	public static <E> Graph<E> asGraph(SuccessorsFunction<E> successorsFunction, Set<E> tips) {
		checkNotNull(successorsFunction);
		checkNotNull(tips);
		checkArgument(tips.stream().allMatch(t -> t != null));

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
		Function<E, E> transformer = Function.identity();
		return asImmutableGraph(graph, transformer);
	}

	public static <E, F> ImmutableGraph<F> asImmutableGraph(Graph<E> graph, Function<E, F> transformer) {
		final GraphBuilder<Object> startBuilder = graph.isDirected() ? GraphBuilder.directed()
				: GraphBuilder.undirected();
		startBuilder.allowsSelfLoops(graph.allowsSelfLoops());
		final ImmutableGraph.Builder<F> builder = startBuilder.immutable();
		final Set<E> nodes = graph.nodes();
		for (E node : nodes) {
			builder.addNode(transformer.apply(node));
		}
		final Set<EndpointPair<E>> edges = graph.edges();
		for (EndpointPair<E> edge : edges) {
			builder.putEdge(transformer.apply(edge.source()), transformer.apply(edge.target()));
		}
		return builder.build();
	}

	/**
	 * Thx https://stackoverflow.com/a/60621544.
	 */
	public static void copyRecursively(Path source, Path target, CopyOption... options) throws IOException {
		if (Files.exists(target)) {
			checkArgument(!target.toRealPath().startsWith(source.toRealPath()));
		} else {
			checkArgument(!target.startsWith(source.toRealPath()));
		}
		/**
		 * Note that under restricted security settings, even the toUri() call fails
		 * (because it tries to construct an absolute path, I think).
		 */
		if (source.toUri().getScheme().equals("file")) {
			final SecurityManager securityManager = System.getSecurityManager();
			if (securityManager != null) {
				securityManager.checkPermission(new FilePermission(source.toString() + "/-", "read"));
			}
		}

		Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
				LOGGER.debug("Pre-visiting directory {}.", dir);
				Files.createDirectories(target.resolve(source.relativize(dir).toString()));
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				LOGGER.debug("Copying {}.", file);
				Files.copy(file, target.resolve(source.relativize(file).toString()), options);
				return FileVisitResult.CONTINUE;
			}
		});
	}

}
