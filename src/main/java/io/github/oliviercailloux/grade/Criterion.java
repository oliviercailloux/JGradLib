package io.github.oliviercailloux.grade;

public interface Criterion {
	public String getRequirement();

	public double getMaxPoints();

	public double getMinPoints();

	public static final Criterion ROOT_CRITERION = new Criterion() {

		@Override
		public String getRequirement() {
			return toString();
		}

		@Override
		public double getMaxPoints() {
			return 1d;
		}

		@Override
		public double getMinPoints() {
			return 0d;
		}

		@Override
		public String toString() {
			return "ROOT_CRITERION";
		}
	};
}
