package io.github.oliviercailloux.strings;


public class StringManiperSkeleton {
	/**
	 * Returns the given prefix followed by the given original string.
	 * <p>
	 * For example, given an original string "blah" and a prefix "pp", this method
	 * returns "ppblah".
	 *
	 * @param prefix   the prefix
	 * @param original the original string
	 * @return the original string, prefixed
	 */
	public static String prefixOnce(String prefix, String original) {
		/*
		 * The return expression below is incorrect and is there only to allow this
		 * class to compile.
		 */
		return "";
	}

	/**
	 * Returns the given original string, prefixed with as many repetitions of the
	 * prefix as requested.
	 * <p>
	 * For example, given an original string “blah” and a prefix “42” and
	 * repetitions equal to 3, this method returns “424242blah”.
	 *
	 * @param prefix      the prefix
	 * @param repetitions the number of repetitions of the prefix
	 * @param original    the original string
	 * @return the original string, prefixed.
	 * @throws IllegalArgumentException if <code>repetitions</code> is strictly
	 *                                  negative
	 */
	public static String prefix(int prefix, int repetitions, String original) {
		/*
		 * The return expression below is incorrect and is there only to allow this
		 * class to compile.
		 */
		return "";
	}

	/**
	 * Sets the suffix for later use.
	 *
	 * @param suffix the suffix to remember.
	 */
	public static void setSuffix(String suffix) {

	}

	/**
	 * Suffixes the given string with a suffix that has been remembered previously.
	 *
	 * @param original the original string to suffix
	 * @return the string, suffixed
	 * @throws IllegalStateException if the suffix set previously is
	 *                               <code>null</code> or empty
	 */
	public static String suffix(String original) {
		/*
		 * The return expression below is incorrect and is there only to allow this
		 * class to compile.
		 */
		return "";
	}
}
