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

import javax.json.JsonObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

	public static URL newURL(String url) {
		try {
			return new URL(url);
		} catch (MalformedURLException e) {
			throw new IllegalArgumentException(e);
		}
	}

	public static URI toURI(URL url) {
		try {
			return url.toURI();
		} catch (URISyntaxException e) {
			throw new IllegalArgumentException(e);
		}
	}

}
