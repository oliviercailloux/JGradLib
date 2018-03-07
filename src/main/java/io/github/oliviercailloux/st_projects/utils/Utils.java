package io.github.oliviercailloux.st_projects.utils;

import static com.google.common.base.Preconditions.checkState;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import javax.json.JsonObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.diffplug.common.base.Throwing;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.jcabi.github.Github;
import com.jcabi.github.Limits;
import com.jcabi.github.RtGithub;

public class Utils {
	public static final URL EXAMPLE_URL;

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(Utils.class);

	static {
		EXAMPLE_URL = newURL("http://example.com");
	}

	public static <T> Optional<T> getIf(boolean condition, Supplier<T> supplier) {
		return condition ? Optional.of(supplier.get()) : Optional.empty();
	}

	public static <K, V> Optional<V> getOptionally(Map<K, V> map, K el) {
		return getIf(map.containsKey(el), () -> map.get(el));
	}

	public static String getToken() throws IOException {
		final String token = System.getenv("token_github_projects_review");
		if (token != null) {
			return token;
		}
		final Path path = Paths.get("token_github_projects_review.txt");
		checkState(Files.exists(path));
		final String content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
		return content.replaceAll("\n", "");
	}

	public static void logLimits() throws IOException {
		final String token = getToken();
		checkState(token != null);
		final Github gitHub = new RtGithub(token);
		logLimits(gitHub);
	}

	public static void logLimits(Github gitHub) throws IOException {
		final JsonObject json = gitHub.limits().get(Limits.CORE).json();
		final int limit = json.getJsonNumber("limit").intValue();
		final int remaining = json.getJsonNumber("remaining").intValue();
		final int reset = json.getJsonNumber("reset").intValue();
		LOGGER.info("Limit: {}.", limit);
		LOGGER.info("Remaining: {}.", remaining);
		LOGGER.info("Reset time: {}.", Instant.ofEpochSecond(reset).atZone(ZoneId.systemDefault()));
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

	public static URL newURL(String url) {
		try {
			return new URL(url);
		} catch (MalformedURLException e) {
			throw new IllegalArgumentException(e);
		}
	}

	public static <T, K, U> Collector<T, ?, Map<K, U>> toLinkedMap(Function<? super T, ? extends K> keyMapper,
			Function<? super T, ? extends U> valueMapper) {
		return Collectors.toMap(keyMapper, valueMapper, (u, v) -> {
			throw new IllegalStateException(String.format("Duplicate key %s", u));
		}, LinkedHashMap::new);
	}

	public static URI toURI(URL url) {
		try {
			return url.toURI();
		} catch (URISyntaxException e) {
			throw new IllegalArgumentException(e);
		}
	}

}
