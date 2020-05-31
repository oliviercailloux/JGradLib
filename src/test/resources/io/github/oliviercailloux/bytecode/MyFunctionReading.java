package io.github.oliviercailloux.bytecode;

import java.security.Policy;
import java.util.function.Function;

public class MyFunctionReading implements Function<String, String> {

	public static Function<String, String> newInstance() {
		return new MyFunctionReading();
	}

	private MyFunctionReading() {
		/** Empty private constructor. */
	}

	@Override
	public String apply(String t) {
		return Policy.getPolicy().toString();
	}

}
