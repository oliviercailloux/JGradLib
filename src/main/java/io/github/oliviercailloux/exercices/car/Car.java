package io.github.oliviercailloux.exercices.car;

/**
 * A car has a color (black or white), a driver, a passenger.
 * <p>
 * Paint: 6 points
 * <p>
 * Driving: 13 points, half of which for swap support.
 * <p>
 * Parts of the specifications of this class is determined by the corresponding
 * unit tests.
 */
public interface Car {
	public void paintBlack();

	public void paintWhite();

	public boolean isBlack();

	public Person getDriver();

	public void setPassenger(Person passenger);

	/**
	 * Swaps the driver and the passenger: the passenger becomes the driver, and
	 * conversely.
	 * <p>
	 * If there is currently no passenger, this method should return an appropriate
	 * exception.
	 */
	public void swap();

	/**
	 * This lets the driver drive for the given number of hours, at the favorite
	 * speed of the driver, as indicated by {@link Person#getFavoriteSpeed()}.
	 * <p>
	 * The car advances the corresponding number of kilometers.
	 *
	 * @param hours the number of hours that the driver drives
	 */
	public void drive(int hours);

	/**
	 * Retrieves the total distance that this car has traveled since its birth.
	 *
	 * @return a non-negative number, zero if not traveled yet.
	 */
	public int getTotalTraveledDistance();
}
