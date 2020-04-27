package io.github.oliviercailloux.java_grade.compiler;

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
