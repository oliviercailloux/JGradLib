package io.github.oliviercailloux.bytecode;

import java.util.function.Function;

public class MyIdentityFunctionThrowing implements Function<String, String> {

	public static Function<String, String> newInstance() {
		return new MyIdentityFunctionThrowing();
	}

	public MyIdentityFunctionThrowing() {
		throw new IllegalStateException("I like to be thrown.");
	}

	@Override
	public String apply(String t) {
		return t;
	}

}
