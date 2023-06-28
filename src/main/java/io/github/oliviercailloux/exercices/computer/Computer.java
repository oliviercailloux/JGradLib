package io.github.oliviercailloux.exercices.computer;

/**
 * A simple computer that is able to store two operands, then apply operations
 * on them.
 */
public interface Computer {

	/**
	 * Creates a new computer with no operands.
	 *
	 * @return a new computer
	 */
	public static Computer instance() {
		/* TODO */
		return null;
	}

	/**
	 * Creates a new computer with one operand.
	 *
	 * @return a new computer
	 */
	public static Computer oneOp(double op) {
		/* TODO */
		return null;
	}

	/**
	 * Creates a new computer with two operands having the same value.
	 *
	 * @return a new computer
	 */
	public static Computer duplOp(double op) {
		/* TODO */
		return null;
	}

	/**
	 * The first time this is called (on a given instance), this sets the first
	 * operand; the second time, this sets the second operand. Further calls throw
	 * an exception.
	 */
	void addOperand(double op) throws IllegalStateException;

	/**
	 * This call should log at least one statement (anything is good) at the level
	 * INFO, using the SLF4J library.
	 *
	 * @param op one of “+”, “-”, “*” or “/”
	 * @return the result of applying the given operation on the previously given
	 *         two operands
	 */
	public double apply(String op) throws IllegalStateException, IllegalArgumentException, ArithmeticException;
}
