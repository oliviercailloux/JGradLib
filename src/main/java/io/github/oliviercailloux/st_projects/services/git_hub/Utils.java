package io.github.oliviercailloux.st_projects.services.git_hub;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

public class Utils {
	public static final URL EXAMPLE_URL;
	static {
		EXAMPLE_URL = newUrl("http://example.com");
	}

	public static URL newUrl(String url) {
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
