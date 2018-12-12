package io.github.oliviercailloux.git.git_hub.utils;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Utils {
	public static final URI EXAMPLE_URI = URI.create("http://example.com");

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(Utils.class);

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

}
