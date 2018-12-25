package io.github.oliviercailloux.git.utils;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Utils {
	public static final URI EXAMPLE_URI = URI.create("http://example.com");

	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(Utils.class);

	public static boolean implies(boolean a, boolean b) {
		return !a || b;
	}

}
