package io.github.oliviercailloux.exercices.car;

/**
 * A person with a name and a speed that we assume is the speed at which that
 * person <em>always</em> drives.
 */
public interface Person {
	public String getName();

	/**
	 * Returns the speed at which that person drives, in km per hour.
	 *
	 * @return a strictly positive number
	 */
	public int getFavoriteSpeed();
}
