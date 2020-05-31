package io.github.oliviercailloux.bytecode;

import java.io.IOException;

public class SourceWithWarnings {
	public static void main(String[] args) throws IOException {
		Integer a = (Integer) 0;
		if (a == null) {
			System.out.println();
		}
		int b;
	}
}
