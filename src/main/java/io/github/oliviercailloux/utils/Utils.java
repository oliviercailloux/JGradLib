package io.github.oliviercailloux.utils;

import java.net.URI;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.diffplug.common.base.Errors;
import com.diffplug.common.base.Throwing;

public class Utils {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(Utils.class);

	public static final URI EXAMPLE_URI = URI.create("http://example.com");

	public static boolean implies(boolean a, boolean b) {
		return !a || b;
	}

	public static final String ANY_REG_EXP = "[\\s\\S]*";

	public static <T, R> Function<T, R> wrapUsingIllegalStateException(Throwing.Function<T, R> function) {
		return Errors.createRethrowing(IllegalStateException::new).wrap(function);
	}

}
