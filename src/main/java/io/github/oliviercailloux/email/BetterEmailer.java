package io.github.oliviercailloux.email;

public class BetterEmailer implements AutoCloseable {

	public static BetterEmailer newInstance() {
		return new BetterEmailer();
	}

	private BetterEmailer() {
		// nothing yet
	}

	@Override
	public void close() throws Exception {
		TODO();

	}

}
