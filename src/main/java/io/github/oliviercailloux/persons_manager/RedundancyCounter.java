package io.github.oliviercailloux.persons_manager;

/**
 * A redundancy counter is linked to some manager that contains elements (such as persons) and
 * permits to retrieve the number of duplicated elements and the number of unique elements that have
 * been set in that manager.
 *
 */
public interface RedundancyCounter {

	/**
	 * Returns the number of entries that are redundant.
	 *
	 * @return a non-negative number.
	 */
	public int getRedundancyCount();

	/**
	 * Returns the number of unique elements in the associated manager.
	 *
	 * @return a non-negative number.
	 */
	public int getUniqueCount();
}
