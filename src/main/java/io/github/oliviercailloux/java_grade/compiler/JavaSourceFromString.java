package io.github.oliviercailloux.java_grade.compiler;

import java.net.URI;

import javax.tools.SimpleJavaFileObject;

/**
 *
 * Straight from
 * https://docs.oracle.com/en/java/javase/11/docs/api/java.compiler/javax/tools/JavaCompiler.html
 *
 */
class JavaSourceFromString extends SimpleJavaFileObject {
	/**
	 * The source code of this "file".
	 */
	final String code;

	/**
	 * Constructs a new JavaSourceFromString.
	 *
	 * @param name the name of the compilation unit represented by this file object
	 * @param code the source code for the compilation unit represented by this file
	 *             object
	 */
	JavaSourceFromString(String name, String code) {
//		super(URI.create("string:///" + name.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
		super(URI.create("string:///" + name), Kind.SOURCE);
		this.code = code;
	}

	@Override
	public CharSequence getCharContent(boolean ignoreEncodingErrors) {
		return code;
	}
}
