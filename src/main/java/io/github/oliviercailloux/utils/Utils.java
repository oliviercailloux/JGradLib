package io.github.oliviercailloux.utils;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Verify.verify;
import static io.github.oliviercailloux.jaris.exceptions.Unchecker.IO_UNCHECKER;

import com.google.common.base.Strings;
import com.google.common.base.Verify;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterators;
import com.google.common.collect.PeekingIterator;
import com.google.common.collect.Streams;
import com.google.common.graph.EndpointPair;
import com.google.common.graph.Graph;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.ImmutableGraph;
import com.google.common.graph.MutableGraph;
import com.google.common.graph.SuccessorsFunction;
import io.github.oliviercailloux.jaris.exceptions.CheckedStream;
import io.github.oliviercailloux.jaris.throwing.TComparator;
import io.github.oliviercailloux.jaris.throwing.TPredicate;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
		final Path resolved = getTempDirectory().resolve(
				prefix + " " + Utils.ISO_BASIC_UTC_FORMATTER.format(Instant.now()) + " " + new Random().nextInt());
		verify(!Files.exists(resolved));
		return resolved;
	}

	/**
	 * @deprecated use the jaris version (and remove the copy of this one in GitHistory?)
	 */
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

	/**
	 * @deprecated see the Jaris version
	 */
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
	@SuppressWarnings({ "deprecation", "removal" })
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

	/**
	 * @deprecated use the Jaris version
	 */
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

	public static <T, X extends Exception> ImmutableSet<T> getMaximalElements(Iterable<T> iterable,
			TComparator<T, ? extends X> comparator) throws X {
		final Optional<T> maxOpt = CheckedStream.<T, X>wrapping(Streams.stream(iterable)).max(comparator);
		if (maxOpt.isEmpty()) {
			return ImmutableSet.of();
		}
		final T max = maxOpt.get();
		return CheckedStream.<T, X>wrapping(Streams.stream(iterable)).filter(t -> comparator.compare(t, max) == 0)
				.collect(ImmutableSet.toImmutableSet());
	}

	public static ImmutableSet<Path> getPathsMatching(Path root,
			TPredicate<? super Path, IOException> predicate) throws IOException {
		final Predicate<? super Path> wrapped = IO_UNCHECKER.wrapPredicate(predicate);
		try (Stream<Path> foundStream = Files.find(root, Integer.MAX_VALUE, (p, a) -> wrapped.test(p))) {
			return foundStream.collect(ImmutableSet.toImmutableSet());
		} catch (UncheckedIOException e) {
			throw e.getCause();
		}
	}

	/**
	 * I believe I found this on SO, but I can’t find the source now. If you find
	 * it, please write to me.
	 *
	 * @return a collector that collects multiple elements to an empty optional; and
	 *         a single element to a present optional.
	 */
	public static <T> Collector<T, ?, Optional<T>> singleOrEmpty() {
		return Collectors.collectingAndThen(
				Collectors.mapping(Optional::of, Collectors.reducing((a, b) -> Optional.empty())),
				o -> o.orElseGet(Optional::empty));
	}
}
