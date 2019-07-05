package io.github.oliviercailloux.utils;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Utils {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(Utils.class);

	public static final URI EXAMPLE_URI = URI.create("http://example.com");

	public static boolean implies(boolean a, boolean b) {
		return !a || b;
	}

	public static final String ANY_REG_EXP = "[\\s\\S]*";

}
