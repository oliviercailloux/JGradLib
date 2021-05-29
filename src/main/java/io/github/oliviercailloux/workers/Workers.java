package io.github.oliviercailloux.workers;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * This type permits to manage a list of persons and distribute them into teams.
 */
public interface Workers {
	/**
	 * Adds the given person to the end of this list of persons (even it was already
	 * in the list).
	 * <p>
	 * This method must emit at least one logging statement (using SLF4J).
	 * </p>
	 */
	public void add(Person p);

	/**
	 * Sets the position of the given person in the list.
	 * <p>
	 * If the person already was at that position, this call has no effect on the
	 * list. If the person was at a different position, its first occurrence is
	 * <em>removed</em> from its original position and <em>inserted</em> in the new
	 * position (thus possibly shifting the indices of other persons accordingly).
	 * </p>
	 * <h2>Example</h2>
	 * <p>
	 * If the current list of persons is (p1, p2, p3, p4) and setPosition(p2, 2) is
	 * called, then the list becomes: (p1, p3, p2, p4).
	 * </p>
	 *
	 * @param p           must already be in the list.
	 * @param newPosition the position at which the person should be in the list,
	 *                    zero being the first position in the list
	 */
	public void setPosition(Person p, int newPosition);

	/**
	 * Returns the person at the given position as a present optional, or an empty
	 * optional if that position is greater than or equal to the size of the list.
	 *
	 * @param pos a non-negative number, zero for the first position in the list
	 */
	public Optional<Person> get(int pos);

	/**
	 * Returns a set of <em>teams</em>, where each team has size {@code n}, the
	 * teams being constituted of persons from this list, in order.
	 * <p>
	 * A person may thus be part of multiple teams if it appears multiple times in
	 * this list.
	 * </p>
	 * <p>
	 * The order of the teams in the set must correspond to the order of the persons
	 * in the list.
	 * </p>
	 * <h2>Example</h2>
	 * <p>
	 * If the current list of persons is (John, Mary, Adam, Carol) then
	 * {@code getAsTeamsOfSize(2)} returns a set of two teams, the first one being
	 * (John, Mary) and the second one being (Adam, Carol).
	 * </p>
	 * <h2>Implementation hint</h2>
	 * <p>
	 * You may want to use {@link List#subList(int, int)} to form the teams.
	 * </p>
	 *
	 * @param n an integer that divides the size of this list (thus, such that this
	 *          list can be divided as requested)
	 * @return a set constituted of lists of {@code n} persons
	 */
	public Set<List<Person>> getAsTeamsOfSize(int n);

	/**
	 * &#x52&#x65&#x74&#x75&#x72&#x6e&#x73&#x20&#x74&#x68&#x65&#x20
	 * &#x73&#x74&#x72&#x69&#x6e&#x67&#x20
	 * <code>&#x41&#x20&#x6c&#x69&#x73&#x74&#x20&#x6f&#x66&#x20
	 * <i>&#x78</i>&#x20
	 * &#x77&#x6f&#x72&#x6b&#x65&#x72&#x73</code>&#x2c&#x20
	 * &#x77&#x68&#x65&#x72&#x65&#x20<code><i>&#x78</i></code>&#x20&#x69&#x73&#x20
	 * &#x74&#x68&#x65&#x20&#x6e&#x75&#x6d&#x62&#x65&#x72&#x20
	 * &#x6f&#x66&#x20&#x70&#x65&#x72&#x73&#x6f&#x6e&#x73&#x20
	 * &#x63&#x6f&#x6e&#x74&#x61&#x69&#x6e&#x65&#x64&#x20
	 * &#x69&#x6e&#x20&#x74&#x68&#x69&#x73&#x20
	 * &#x69&#x6e&#x73&#x74&#x61&#x6e&#x63&#x65&#x2e
	 */
	@Override
	public String toString();
}
