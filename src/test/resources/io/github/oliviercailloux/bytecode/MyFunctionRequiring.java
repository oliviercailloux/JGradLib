package io.github.oliviercailloux.java_grade.bytecode;

import java.util.function.Function;

public class MyFunctionRequiring implements Function<String, String> {

	public static Function<String, String> newInstance() {
		return new MyFunctionRequiring();
	}

	private MyFunctionRequiring() {
		/** Empty private constructor. */
	}

	@Override
	public String apply(String t) {
		return new SourceWithNoWarnings().toString();
	}

}
