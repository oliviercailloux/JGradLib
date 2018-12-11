package io.github.oliviercailloux.st_projects.utils;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonValue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.diffplug.common.base.Throwing;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.ImmutableMap;

public class Utils {
	public static final URL EXAMPLE_URL;

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(Utils.class);

	static {
		EXAMPLE_URL = newURL("http://example.com");
	}

	static public Stream<JsonObject> getContent(JsonObject connection) {
		return getContent(connection, false);
	}

	static public Stream<JsonObject> getContent(JsonObject connection, boolean allowPartial) {
		final JsonArray nodes = connection.getJsonArray("nodes");
		checkArgument(allowPartial || isConnectionComplete(connection), JsonUtils.asPrettyString(connection));
		final Stream<JsonObject> contents = nodes.stream().map(JsonValue::asJsonObject);
		return contents;
	}

	public static String getEncoded(Path path) {
		final List<String> encodedSegments = new ArrayList<>();
		for (Path localPath : path) {
			try {
				final String encoded = URLEncoder.encode(localPath.toString(), StandardCharsets.UTF_8.name())
						.replaceAll("\\+", "%20");
				encodedSegments.add(encoded);
			} catch (UnsupportedEncodingException e) {
				throw new IllegalArgumentException(e);
			}
		}
		return encodedSegments.stream().collect(Collectors.joining("/"));
	}

	public static <T> Optional<T> getIf(boolean condition, Supplier<T> supplier) {
		return condition ? Optional.of(supplier.get()) : Optional.empty();
	}

	public static <K, V> Optional<V> getOptionally(Map<K, V> map, K el) {
		return getIf(map.containsKey(el), () -> map.get(el));
	}

	public static String getTokenRuntimeExc() {
		Optional<String> tokenOpt;
		try {
			tokenOpt = getTokenOpt();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		return tokenOpt
				.orElseThrow(() -> new IllegalStateException("No token found in environment, in property or in file."));
	}

	public static Optional<String> getTokenOpt() throws IOException {
		{
			final String token = System.getenv("token_github_projects_review");
			if (token != null) {
				return Optional.of(token);
			}
		}
		{
			final String token = System.getProperty("token_github_projects_review");
			if (token != null) {
				return Optional.of(token);
			}
		}
		final Path path = Paths.get("token_github_projects_review.txt");
		if (!Files.exists(path)) {
			return Optional.empty();
		}
		final String content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
		return Optional.of(content.replaceAll("\n", ""));
	}

	static public boolean isConnectionComplete(JsonObject connection) {
		final JsonArray nodes = connection.getJsonArray("nodes");
		final boolean sizeComplete = connection.getInt("totalCount") == nodes.size();
		final boolean pageComplete = !connection.getJsonObject("pageInfo").getBoolean("hasNextPage");
		checkArgument(pageComplete == sizeComplete);
		return sizeComplete;
	}

	public static <F, R, T extends Throwable> ImmutableList<R> map(Iterable<F> iterable,
			Throwing.Specific.Function<F, R, T> fct) throws T {
		final Builder<R> builder = ImmutableList.builder();
		for (F elem : iterable) {
			LOGGER.debug("Getting elem {}.", elem);
			final R mapped = fct.apply(elem);
			LOGGER.debug("Mapped elem {}.", elem);
			builder.add(mapped);
		}
		return builder.build();
	}

	public static URI newURI(String uri) {
		try {
			return new URI(uri);
		} catch (URISyntaxException e) {
			throw new IllegalArgumentException(e);
		}
	}

	public static URL newURL(String url) {
		try {
			return new URL(url);
		} catch (MalformedURLException e) {
			throw new IllegalArgumentException(e);
		}
	}

	public static <T, K, U> Collector<T, ?, Map<K, U>> toLinkedMap(Function<? super T, ? extends K> keyMapper,
			Function<? super T, ? extends U> valueMapper) {
		return Collectors.toMap(keyMapper, valueMapper, (v1, v2) -> {
			throw new IllegalStateException(String.format("Duplicate key for values %s and %s", v1, v2));
		}, LinkedHashMap::new);
	}

	public static <K1, K2, V> ImmutableMap<K2, V> toHomonymousImmutableMap(Map<K1, V> startMap,
			Function<? super K1, ? extends K2> keyMapper) {
		return startMap.entrySet().stream()
				.collect(ImmutableMap.toImmutableMap((e) -> keyMapper.apply(e.getKey()), (e) -> e.getValue()));
	}

	public static URI toURI(URL url) {
		try {
			return url.toURI();
		} catch (URISyntaxException e) {
			throw new IllegalArgumentException(e);
		}
	}

	public static String getToken() throws IOException, IllegalStateException {
		final Optional<String> tokenOpt = getTokenOpt();
		return tokenOpt
				.orElseThrow(() -> new IllegalStateException("No token found in environment, in property or in file."));
	}

	public static <K1, K2, V> Map<K2, V> toHomonymousMap(Map<K1, V> startMap,
			Function<? super K1, ? extends K2> keyMapper) {
		final Collector<K1, ?, Map<K2, V>> linkedMap = Utils.<K1, K2, V>toLinkedMap((s) -> keyMapper.apply(s),
				(s) -> startMap.get(s));
		return startMap.keySet().stream().collect(linkedMap);
	}

}
