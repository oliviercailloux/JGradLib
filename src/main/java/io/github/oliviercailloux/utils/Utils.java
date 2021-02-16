package io.github.oliviercailloux.utils;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static io.github.oliviercailloux.jaris.exceptions.Unchecker.IO_UNCHECKER;

import java.io.FilePermission;
import java.io.IOException;
import java.io.UncheckedIOException;
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
import java.util.AbstractSet;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;
import com.google.common.base.Verify;
import com.google.common.collect.AbstractIterator;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterators;
import com.google.common.collect.PeekingIterator;
import com.google.common.collect.Streams;
import com.google.common.collect.UnmodifiableIterator;
import com.google.common.graph.EndpointPair;
import com.google.common.graph.Graph;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.ImmutableGraph;
import com.google.common.graph.ImmutableGraph.Builder;
import com.google.common.graph.MutableGraph;
import com.google.common.graph.SuccessorsFunction;

import io.github.oliviercailloux.grade.Criterion;
import io.github.oliviercailloux.grade.IGrade.GradePath;
import io.github.oliviercailloux.jaris.exceptions.CheckedStream;
import io.github.oliviercailloux.jaris.exceptions.Throwing;

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

	public static <E, F extends E> Graph<E> asGraph(SuccessorsFunction<F> successorsFunction, Set<F> roots) {
		checkNotNull(successorsFunction);
		checkNotNull(roots);
		checkArgument(roots.stream().allMatch(t -> t != null));

		final Queue<F> toConsider = new LinkedList<>(roots);
		final Set<F> seen = new LinkedHashSet<>(roots);

		final MutableGraph<E> mutableGraph = GraphBuilder.directed().build();
		while (!toConsider.isEmpty()) {
			final F current = toConsider.remove();
			Verify.verify(current != null);
			mutableGraph.addNode(current);
			final Iterable<? extends F> successors = successorsFunction.successors(current);
			for (F successor : successors) {
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
			checkArgument(!target.toAbsolutePath().startsWith(source.toRealPath()));
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

	public static <E> ImmutableGraph<E> asGraph(List<E> elements) {
		final MutableGraph<E> builder = GraphBuilder.directed().allowsSelfLoops(false).build();
		final ListIterator<E> iterator = elements.listIterator();
		final PeekingIterator<E> peekingIterator = Iterators.peekingIterator(iterator);
		while (peekingIterator.hasNext()) {
			final E e1 = peekingIterator.next();
			if (peekingIterator.hasNext()) {
				final E e2 = peekingIterator.peek();
				builder.putEdge(e1, e2);
			}
		}
		return ImmutableGraph.copyOf(builder);
	}

	/**
	 * From jbduncan at https://github.com/jrtom/jung/pull/174
	 */
	public static <N> Set<N> topologicallySortedNodes(Graph<N> graph) {
		return new TopologicallySortedNodes<>(graph);
	}

	private static class TopologicallySortedNodes<N> extends AbstractSet<N> {
		private final Graph<N> graph;

		private TopologicallySortedNodes(Graph<N> graph) {
			this.graph = checkNotNull(graph, "graph");
		}

		@Override
		public UnmodifiableIterator<N> iterator() {
			return new TopologicalOrderIterator<>(graph);
		}

		@Override
		public int size() {
			return graph.nodes().size();
		}

		@Override
		public boolean remove(Object o) {
			throw new UnsupportedOperationException();
		}
	}

	private static class TopologicalOrderIterator<N> extends AbstractIterator<N> {
		private final Graph<N> graph;
		private final Queue<N> roots;
		private final Map<N, Integer> nonRootsToInDegree;

		private TopologicalOrderIterator(Graph<N> graph) {
			this.graph = checkNotNull(graph, "graph");
			this.roots = graph.nodes().stream().filter(node -> graph.inDegree(node) == 0)
					.collect(Collectors.toCollection(ArrayDeque::new));
			this.nonRootsToInDegree = graph.nodes().stream().filter(node -> graph.inDegree(node) > 0)
					.collect(Collectors.toMap(node -> node, graph::inDegree, (a, b) -> a, HashMap::new));
		}

		@Override
		protected N computeNext() {
			// Kahn's algorithm
			if (!roots.isEmpty()) {
				N next = roots.remove();
				for (N successor : graph.successors(next)) {
					int newInDegree = nonRootsToInDegree.get(successor) - 1;
					nonRootsToInDegree.put(successor, newInDegree);
					if (newInDegree == 0) {
						nonRootsToInDegree.remove(successor);
						roots.add(successor);
					}
				}
				return next;
			}
			checkState(nonRootsToInDegree.isEmpty(), "graph has at least one cycle");
			return endOfData();
		}
	}

	public static <T, X extends Exception> ImmutableSet<T> getMaximalElements(Iterable<T> iterable,
			Throwing.Comparator<T, ? extends X> comparator) throws X {
		final Optional<T> maxOpt = CheckedStream.<T, X>wrapping(Streams.stream(iterable)).max(comparator);
		if (maxOpt.isEmpty()) {
			return ImmutableSet.of();
		}
		final T max = maxOpt.get();
		return CheckedStream.<T, X>wrapping(Streams.stream(iterable)).filter(t -> comparator.compare(t, max) == 0)
				.collect(ImmutableSet.toImmutableSet());
	}

	public static ImmutableSet<Path> getPathsMatching(Path root,
			Throwing.Predicate<? super Path, IOException> predicate) throws IOException {
		final Predicate<? super Path> wrapped = IO_UNCHECKER.wrapPredicate(predicate);
		try (Stream<Path> foundStream = Files.find(root, Integer.MAX_VALUE, (p, a) -> wrapped.test(p))) {
			return foundStream.collect(ImmutableSet.toImmutableSet());
		} catch (UncheckedIOException e) {
			throw e.getCause();
		}
	}

	public static <T> Collector<T, ?, Optional<T>> singleOrEmpty() {
		return Collectors.collectingAndThen(
				Collectors.mapping(Optional::of, Collectors.reducing((a, b) -> Optional.empty())),
				o -> o.orElseGet(Optional::empty));
	}
}
