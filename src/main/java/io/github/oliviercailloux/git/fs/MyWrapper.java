package io.github.oliviercailloux.git.fs;

class MyWrapper implements AutoCloseable {
	public static void main(String[] args) {
		try (MyWrapper myWrapper = new MyWrapper()) {
			System.out.println(myWrapper.getCloseable().myInt);
		}
	}

	public static class MyCloseable implements AutoCloseable {
		public int myInt = 0;

		@Override
		public void close() {
		}

	}

	private final MyCloseable myCloseable = new MyCloseable();

	public MyCloseable getCloseable() {
		return myCloseable;
	}

	@Override
	public void close() {
		myCloseable.close();
	}
}
